package com.victorpolitakis.pesto.keyboard

import android.content.Context
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkMerger
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromDex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.toList
import java.io.File
import javax.inject.Inject

/** How far the patching got, for the Settings progress (phase 4). */
enum class PatchStep { MERGING, PATCHING, SIGNING, DONE }

/** The patching failed; [step] is where, [message] is for the log, not the user. */
class KeyboardPatchException(val step: PatchStep, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Builds the patched "Gboard with Pesto" APK on the phone (phase 3). It runs in the `:patcher`
 * process (large heap): it merges the stock Gboard's splits, runs the gboard-patches bundle shipped
 * as an asset, and signs the result with the local key. It does not install: Settings does that
 * with PackageInstaller, because the install needs the user.
 *
 * Only the patches build the keyboard; this class does not read or change Gboard's code itself.
 */
class KeyboardPatcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gboard: GboardInstall,
    private val signingKey: LocalSigningKey,
) {
    /**
     * Produces a signed patched APK for the installed stock Gboard, seeded with its [languagesFile]
     * (from [GboardInstall.stockLanguagesFile], read before the stock keyboard is disabled).
     *
     * @param workDir a scratch directory the caller clears; large, so app-private cache
     * @param onStep progress, on the calling thread
     * @return the signed APK, ready for PackageInstaller
     */
    // usableSpace is enough for a pre-flight check; the heavier StorageManager#getAllocatableBytes
    // (which would also clear caches) is not worth it for a one-off guard.
    @Suppress("UsableSpace")
    suspend fun buildPatchedApk(
        workDir: File,
        languagesFile: String?,
        onStep: (PatchStep) -> Unit = {},
    ): File {
        val apks = gboard.apkFiles(KeyboardPatch.GBOARD_PACKAGE)
            ?: throw KeyboardPatchException(PatchStep.MERGING, "stock Gboard is not installed")
        workDir.mkdirs()

        // Merging, patching and signing hold a few copies of a ~100 MB APK at once; fail early and
        // clearly when the disk cannot hold them, rather than part way through.
        val need = apks.all.sumOf { File(it).length() } * 4
        if (workDir.usableSpace < need) {
            throw KeyboardPatchException(
                PatchStep.MERGING,
                "not enough free space: need about ${need / 1_000_000} MB",
            )
        }

        // 1. One APK out of the base and its splits (the 18.5.6 build ships splits).
        onStep(PatchStep.MERGING)
        val merged = File(workDir, "merged.apk")
        try {
            if (apks.splits.isEmpty()) {
                File(apks.base).copyTo(merged, overwrite = true)
            } else {
                ApkMerger().merge(bundleOf(workDir, apks), merged)
            }
        } catch (e: Exception) {
            throw KeyboardPatchException(PatchStep.MERGING, "merge failed", e)
        }

        // 2. Run the patches bundled as an asset, with the languages as the seed patch's option.
        onStep(PatchStep.PATCHING)
        val patched = File(workDir, "patched.apk")
        try {
            runPatches(merged, patched, workDir, languagesFile)
        } catch (e: KeyboardPatchException) {
            throw e
        } catch (e: Exception) {
            throw KeyboardPatchException(PatchStep.PATCHING, "patching failed", e)
        }

        // 3. Sign with the local key; the key never goes into the APK.
        onStep(PatchStep.SIGNING)
        val signed = File(workDir, "gboard-with-pesto.apk")
        try {
            signingKey.signer().signApk(patched, signed)
        } catch (e: Exception) {
            throw KeyboardPatchException(PatchStep.SIGNING, "signing failed", e)
        }

        // The intermediates are hundreds of MB; keep only the signed APK. The caller deletes that
        // once the install is done (phase 4).
        listOf(merged, patched, File(workDir, "splits"), File(workDir, "patcher"), File(workDir, "patches.mpp"))
            .forEach { it.deleteRecursively() }

        onStep(PatchStep.DONE)
        return signed
    }

    private suspend fun runPatches(input: File, output: File, workDir: File, languagesFile: String?) {
        val bundle = File(workDir, "patches.mpp")
        context.assets.open(KeyboardPatch.PATCHES_ASSET).use { asset ->
            bundle.outputStream().use(asset::copyTo)
        }
        // Android 14+ refuses to load a dex that is still writable ("Writable dex file"), and
        // PatchLoader.Dex hands the file straight to DexClassLoader, so make it read-only first.
        check(bundle.setReadOnly()) { "could not make the patches bundle read-only" }

        val patches: Set<Patch<*>> = loadPatchesFromDex(setOf(bundle), optimizedDexDirectory = workDir)
        // Always set the languages option, even empty, so the patch's example default is never used.
        val seed = patches.firstOrNull { it.name == KeyboardPatch.SEED_PATCH_NAME }
            ?: throw KeyboardPatchException(PatchStep.PATCHING, "the patches bundle has no '${KeyboardPatch.SEED_PATCH_NAME}'")
        seed.options[KeyboardPatch.PESTO_LOCALES_OPTION] = languagesFile.orEmpty()

        // The patcher works in its own temporary tree; STRIP_FAST is the default.
        input.copyTo(output, overwrite = true)
        Patcher(PatcherConfig(apkFile = output, temporaryFilesPath = File(workDir, "patcher"))).use { patcher ->
            patcher += patches
            val failures = patcher().toList().filter { it.exception != null }
            if (failures.isNotEmpty()) {
                throw KeyboardPatchException(
                    PatchStep.PATCHING,
                    failures.joinToString("; ") { "${it.patch.name}: ${it.exception?.message}" },
                )
            }
            patcher.get().applyTo(output)
        }
    }

    /** ApkMerger takes a bundle file or a directory of APKs; the simplest input is a directory. */
    private fun bundleOf(workDir: File, apks: ApkFiles): File {
        val dir = File(workDir, "splits").apply { mkdirs() }
        apks.all.forEachIndexed { i, path ->
            val name = if (i == 0) "base.apk" else File(path).name
            File(path).copyTo(File(dir, name), overwrite = true)
        }
        return dir
    }
}
