package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.Patch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.Properties

/**
 * Integration test that runs the patches against a real Gboard APK.
 *
 * The APKs are never committed (RESEARCH §5, legal). The paths are read from the repository root
 * `local.properties`; when a key is missing, its tests are skipped. Add, for example:
 *
 * ```
 * gboard.apk.18_5_4=/absolute/path/Gboard-18.5.4.apk
 * gboard.apk.18_5_6.merged=/absolute/path/Gboard-18.5.6-merged.apk
 * ```
 *
 * The 18.5.6 build ships as split APKs; merging them is the on-device patcher's job (phase 3), so
 * this test takes an already-merged APK for that version.
 *
 * Each patch runs on its own, in its own patcher, so one failing patch does not hide the others:
 * every patch is a separate test, and all failures show in one run. This decodes the APK once per
 * patch, so it is slow, but it is a local test.
 */
class RealGboardApkTest {
    @TestFactory
    fun `each patch applies to Gboard 18_5_4`() = perPatchTests("gboard.apk.18_5_4")

    @TestFactory
    fun `each patch applies to merged Gboard 18_5_6`() = perPatchTests("gboard.apk.18_5_6.merged")

    private fun perPatchTests(key: String): List<DynamicTest> {
        val apkPath = localProperty(key)
            ?: return listOf(
                dynamicTest("$key not set") {
                    assumeTrue(false) { "$key not set in local.properties; skipping" }
                },
            )
        val apk = File(apkPath)
        if (!apk.isFile) {
            // A path that is set but not found is a broken setup, not a reason to skip: fail loudly.
            return listOf(dynamicTest(key) { fail<Unit>("APK set in local.properties but not found: $apkPath") })
        }
        return gboardWithPestoPatches.map { patch ->
            dynamicTest(patch.name ?: patch.toString()) { assertApplies(apk, patch) }
        }
    }

    /**
     * Runs [patch] (and its dependencies) alone against [apk] and asserts that nothing failed, which
     * confirms its fingerprints matched the real bytecode.
     */
    private fun assertApplies(apk: File, patch: Patch<*>) {
        val temp = Files.createTempDirectory("gboard-patch").toFile()
        // Fingerprints are global objects that keep their match. Patcher.get() clears them, but
        // this test never calls it, so clear them here: otherwise a run reuses the matches of the
        // previous one, even of the other APK.
        clearFingerprints()
        try {
            val failures = mutableListOf<String>()
            Patcher(PatcherConfig(apkFile = apk, temporaryFilesPath = temp)).use { patcher ->
                patcher += setOf(patch)
                runBlocking { patcher().toList() }.forEach { result ->
                    val exception = result.exception ?: return@forEach
                    // A failed dependency is wrapped; show every cause down to the root one.
                    val causes = generateSequence<Throwable>(exception) { it.cause }
                        .mapNotNull { it.message }
                        .joinToString("\n    caused by: ")
                    failures += "${result.patch.name}: $causes"
                }
            }
            assertTrue(failures.isEmpty()) { failures.joinToString("\n") }
        } finally {
            clearFingerprints()
            temp.deleteRecursively()
        }
    }

    private fun clearFingerprints() = gboardFingerprints.forEach { it.clearMatch() }

    private fun localProperty(key: String): String? {
        // The test working directory is the patches module; local.properties lives at the subproject
        // root (its parent), which is where ./gradlew is run from.
        val candidates = listOf(
            File("../local.properties"),
            File("local.properties"),
        )
        val file = candidates.firstOrNull { it.isFile } ?: return null
        return readLocalProperties(file).getProperty(key)?.takeIf { it.isNotBlank() }
    }

    internal companion object {
        /**
         * Reads a `local.properties` as UTF-8, not as `Properties.load(InputStream)`'s ISO-8859-1:
         * a path with Greek letters would otherwise come out garbled, the file would not be found,
         * and the test would skip quietly.
         */
        fun readLocalProperties(file: File): Properties = Properties().apply {
            InputStreamReader(file.inputStream(), Charsets.UTF_8).use(::load)
        }
    }
}
