package com.victorpolitakis.pesto.gboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The superclass walk of the language seed, on the two chains seen in the real APKs, and the
 * manifest name helpers. No APK needed.
 */
class ClassChainTest {
    /** A class of the dex: its superclass, and whether it declares attachBaseContext(Context). */
    private data class FakeClass(val type: String, val superclass: String?, val declares: Boolean = false)

    private fun dexOf(vararg classes: FakeClass) = classes.associateBy { it.type }

    private fun walk(dex: Map<String, FakeClass>, start: String) = firstDeclaringClass(
        startType = start,
        lookup = dex::get,
        superclassOf = { it.superclass },
        declares = { it.declares },
    )?.type

    private val app = "Lcom/google/android/apps/inputmethod/latin/HiltLatinApp;"

    @Test
    fun `18_5_4 chain finds the final attachBaseContext in neg`() {
        // HiltLatinApp -> eux -> ewt -> neg(final) -> pkg -> Application
        val dex = dexOf(
            FakeClass(app, "Leux;"),
            FakeClass("Leux;", "Lewt;"),
            FakeClass("Lewt;", "Lneg;"),
            FakeClass("Lneg;", "Lpkg;", declares = true),
            FakeClass("Lpkg;", "Landroid/app/Application;"),
        )
        assertEquals("Lneg;", walk(dex, app))
    }

    @Test
    fun `18_5_6 chain finds the final attachBaseContext in aatj`() {
        // HiltLatinApp -> ifg -> jmg -> aatj(final) -> aeyo -> Application
        val dex = dexOf(
            FakeClass(app, "Lifg;"),
            FakeClass("Lifg;", "Ljmg;"),
            FakeClass("Ljmg;", "Laatj;"),
            FakeClass("Laatj;", "Laeyo;", declares = true),
            FakeClass("Laeyo;", "Landroid/app/Application;"),
        )
        assertEquals("Laatj;", walk(dex, app))
    }

    @Test
    fun `the first declaring class wins, the application class itself included`() {
        val dex = dexOf(
            FakeClass(app, "La;", declares = true),
            FakeClass("La;", "Landroid/app/Application;", declares = true),
        )
        assertEquals(app, walk(dex, app))
    }

    @Test
    fun `the walk stops at the first class outside the dex`() {
        // Application is a framework class, not in the dex: nothing declares it before that.
        val dex = dexOf(
            FakeClass(app, "La;"),
            FakeClass("La;", "Landroid/app/Application;"),
        )
        assertNull(walk(dex, app))
    }

    @Test
    fun `a superclass cycle ends the walk`() {
        val dex = dexOf(FakeClass("La;", "Lb;"), FakeClass("Lb;", "La;"))
        assertNull(walk(dex, "La;"))
    }

    @Test
    fun `component names resolve against the package as Android does`() {
        val pkg = "com.google.android.inputmethod.latin"
        assertEquals(
            "com.google.android.apps.inputmethod.latin.HiltLatinApp",
            componentClassName("com.google.android.apps.inputmethod.latin.HiltLatinApp", pkg),
        )
        assertEquals("$pkg.App", componentClassName(".App", pkg))
        assertEquals("$pkg.App", componentClassName("App", pkg))
    }

    @Test
    fun `class names become type descriptors`() {
        assertEquals(app, classNameToType("com.google.android.apps.inputmethod.latin.HiltLatinApp"))
    }

    @Test
    fun `local properties with a greek path are read as utf-8`(@TempDir dir: File) {
        val path = "/Users/victor/Έγγραφα/gboard/Gboard-18.5.4.apk"
        val file = dir.resolve("local.properties")
        file.writeText("gboard.apk.18_5_4=$path\n", Charsets.UTF_8)
        assertEquals(path, RealGboardApkTest.readLocalProperties(file).getProperty("gboard.apk.18_5_4"))
    }
}
