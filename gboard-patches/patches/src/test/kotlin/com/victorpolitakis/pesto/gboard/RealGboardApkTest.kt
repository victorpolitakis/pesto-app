package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

/**
 * Integration test that runs the patches against a real Gboard APK.
 *
 * The APKs are never committed (RESEARCH §5, legal). The paths are read from the repository root
 * `local.properties`; when they are missing, each test is skipped. Add, for example:
 *
 * ```
 * gboard.apk.18_5_4=/absolute/path/Gboard-18.5.4.apk
 * gboard.apk.18_5_6.merged=/absolute/path/Gboard-18.5.6-merged.apk
 * ```
 *
 * The 18.5.6 build ships as split APKs; merging them is the on-device patcher's job (phase 3), so
 * this test takes an already-merged APK for that version.
 */
class RealGboardApkTest {
    @Test
    fun `patches apply to Gboard 18_5_4`(@TempDir temp: File) {
        runPatchesAgainst(localProperty("gboard.apk.18_5_4"), temp)
    }

    @Test
    fun `patches apply to merged Gboard 18_5_6`(@TempDir temp: File) {
        runPatchesAgainst(localProperty("gboard.apk.18_5_6.merged"), temp)
    }

    /**
     * Runs the full patch set against [apkPath] and asserts every patch executed without an
     * exception, which confirms every fingerprint matched on the real bytecode. Skipped when the
     * APK is not configured.
     */
    private fun runPatchesAgainst(apkPath: String?, temp: File) {
        assumeTrue(apkPath != null) { "APK path not set in local.properties; skipping" }
        val apk = File(apkPath!!)
        assumeTrue(apk.isFile) { "configured APK does not exist: $apkPath; skipping" }

        val failures = mutableListOf<String>()
        Patcher(PatcherConfig(apkFile = apk, temporaryFilesPath = temp)).use { patcher ->
            patcher += gboardWithPestoPatches
            runBlocking {
                patcher().toList().forEach { result ->
                    result.exception?.let { failures += "${result.patch.name}: ${it.message}" }
                }
            }
        }
        assertTrue(failures.isEmpty(), "patches failed:\n" + failures.joinToString("\n"))
    }

    private fun localProperty(key: String): String? {
        // The test working directory is the patches module; local.properties lives at the subproject
        // root (its parent), which is where ./gradlew is run from.
        val candidates = listOf(
            File("../local.properties"),
            File("local.properties"),
        )
        val file = candidates.firstOrNull { it.isFile } ?: return null
        val properties = Properties()
        file.inputStream().use(properties::load)
        return properties.getProperty(key)?.takeIf { it.isNotBlank() }
    }
}
