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
    suspend fun buildPatchedApk(
        workDir: File,
        languagesFile: String?,
        onStep: (PatchStep) -> Unit = {},
    ): File {
        val apks = gboard.apkFiles(KeyboardPatch.GBOARD_PACKAGE)
            ?: throw KeyboardPatchException(PatchStep.MERGING, "stock Gboard is not installed")
        workDir.mkdirs()

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

        onStep(PatchStep.DONE)
        return signed
    }

    private suspend fun runPatches(input: File, output: File, workDir: File, languagesFile: String?) {
        val bundle = File(workDir, "patches.mpp")
        context.assets.open(KeyboardPatch.PATCHES_ASSET).use { asset ->
            bundle.outputStream().use(asset::copyTo)
        }
        val patches: Set<Patch<*>> = loadPatchesFromDex(setOf(bundle), optimizedDexDirectory = workDir)
        languagesFile?.let { contents ->
            patches.firstOrNull { it.name == "Seed keyboard languages" }
                ?.options?.set(KeyboardPatch.PESTO_LOCALES_OPTION, contents)
        }

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
