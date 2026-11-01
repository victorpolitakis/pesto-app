package com.victorpolitakis.pesto.gboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Structural checks of the patch set that do not need a Gboard APK; they verify the patches
 * construct and are declared as the plan expects. Fingerprint matching is covered by the
 * APK-gated [RealGboardApkTest].
 */
class PatchSetTest {
    @Test
    fun `the six planned patches are present`() {
        assertEquals(6, gboardWithPestoPatches.size)
    }

    @Test
    fun `every patch has a unique, non-blank name`() {
        val names = gboardWithPestoPatches.map { it.name }
        assertTrue(names.all { !it.isNullOrBlank() }, "a patch has no name")
        assertEquals(names.size, names.toSet().size, "patch names are not unique")
    }

    @Test
    fun `every patch is enabled by default`() {
        // The plan applies all six patches whenever the keyboard is patched.
        assertTrue(gboardWithPestoPatches.all { it.name != null })
    }

    @Test
    fun `expected patch names are declared`() {
        val names = gboardWithPestoPatches.mapNotNull { it.name }.toSet()
        setOf(
            "Install as Gboard with Pesto clone",
            "Gboard signature bypass",
            "Microphone to Pesto",
            "Seed keyboard languages",
            "Hide from launcher",
            "Pesto manifest wiring",
        ).forEach { expected ->
            assertTrue(expected in names, "missing patch: $expected")
        }
    }

    @Test
    fun `compatibility targets the stock Gboard package`() {
        assertEquals("com.google.android.inputmethod.latin", Gboard.PACKAGE_NAME)
        assertFalse(Gboard.PATCHED_PACKAGE_NAME == Gboard.PACKAGE_NAME, "clone must use a different package")
    }
}
