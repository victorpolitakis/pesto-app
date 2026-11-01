package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import org.w3c.dom.Element

private const val EXTENSION_CLASS = "Lcom/victorpolitakis/pesto/bridge/PestoBridge;"
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val CONTEXT_TYPE = "Landroid/content/Context;"

/** Where the language-seed values go inside the patched APK; read by `LocaleSeeder`. */
internal const val PESTO_LOCALES_ASSET = "assets/pesto_locales.txt"

/**
 * Example contents of [PESTO_LOCALES_ASSET]: exactly what Gboard wrote to its own preferences for
 * English (UK) + Greek on the POCO (RESEARCH §10, spike S2). Note `enabled_ime_language_tags` holds
 * `el`, not `el-GR`: the values are Gboard's own, and are written as given, with no rule of ours.
 */
internal val EXAMPLE_PESTO_LOCALES = """
    enabled_input_method_entries=en-GB:qwerty;el-GR:greek
    current_input_method_entry=en-GB:qwerty
    enabled_ime_language_tags=en-GB;el
""".trimIndent() + "\n"

/**
 * The contents of [PESTO_LOCALES_ASSET]: `key=value` lines with Gboard's three language
 * preferences. The on-device patcher (phase 3) sets it from the stock keyboard's subtypes, read
 * before the stock keyboard is disabled; the default is [EXAMPLE_PESTO_LOCALES].
 */
internal val pestoLocalesOption = stringOption(
    key = "pestoLocales",
    default = EXAMPLE_PESTO_LOCALES,
    title = "Keyboard languages",
    description = "Contents of $PESTO_LOCALES_ASSET: key=value lines with Gboard's language " +
        "preferences, written into the keyboard as given.",
    required = true,
)

/** Adds [PESTO_LOCALES_ASSET] to the patched APK, with the value of [pestoLocalesOption]. */
private val pestoLocalesAssetPatch = resourcePatch {
    execute {
        val contents = pestoLocalesOption.value
            ?: throw PatchException("the pestoLocales option is not set")
        get(PESTO_LOCALES_ASSET, false).apply {
            parentFile?.mkdirs()
            writeText(contents)
        }
    }
}

/**
 * The type of the application class, from the manifest's `<application android:name>`; set by
 * [applicationClassPatch] on every run, before [languageSeedPatch] executes.
 */
private var applicationType: String? = null

/** Reads the application class from the manifest, for [languageSeedPatch]. */
private val applicationClassPatch = resourcePatch {
    execute {
        applicationType = null
        document("AndroidManifest.xml").use { manifest ->
            val manifestElement = manifest.getElementsByTagName("manifest").item(0) as Element
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: throw PatchException("the manifest has no <application>")
            val name = application.getAttributeNS(ANDROID_NS, "name")
            if (name.isEmpty()) throw PatchException("<application> has no android:name")
            applicationType = classNameToType(componentClassName(name, manifestElement.getAttribute("package")))
        }
    }
}

private fun Method.isAttachBaseContext() =
    name == "attachBaseContext" &&
        returnType == "V" &&
        parameterTypes.map { it.toString() } == listOf(CONTEXT_TYPE)

/**
 * Seeds the patched keyboard's enabled languages on first start.
 *
 * A fresh clone of Gboard starts with only English. This patch bundles the stock keyboard's
 * languages as [PESTO_LOCALES_ASSET] and calls `PestoBridge.seedLanguages(context)` at the start of
 * the application's `attachBaseContext`, the earliest hook, which runs before Gboard reads its
 * preferences, so the languages apply in the first process, without the restart the spike needed
 * (RESEARCH §10, spike S2).
 *
 * The application class (`HiltLatinApp`) does not declare `attachBaseContext` itself: a superclass
 * declares it `protected final` (`neg` in 18.5.4, `aatj` in 18.5.6). So the patch reads the class
 * from the manifest, walks up its superclasses, and patches the first one that declares
 * `attachBaseContext(Context)`. It adds no override to the application class, which the `final`
 * would turn into a VerifyError.
 *
 * The seed is applied once, guarded by a `pesto_seeded` flag the bridge writes.
 */
@Suppress("unused")
val languageSeedPatch = bytecodePatch(
    name = "Seed keyboard languages",
    description = "Applies the stock keyboard's languages to the patched keyboard on first start.",
    default = true,
) {
    compatibleWith(Gboard.COMPATIBILITY)

    dependsOn(pestoLocalesAssetPatch, applicationClassPatch)
    pestoLocalesOption()

    // Shares the extension with the microphone patch; Morphe merges a given extension only once.
    extendWith("extensions/pestobridge.mpe")

    execute {
        val startType = applicationType
            ?: throw PatchException("the application class was not read from the manifest")

        val owner: ClassDef = firstDeclaringClass(
            startType = startType,
            lookup = { type -> classDefByOrNull(type) },
            superclassOf = { it.superclass },
            declares = { classDef -> classDef.methods.any { it.isAttachBaseContext() } },
        ) ?: throw PatchException("no class from $startType up declares attachBaseContext(Context)")

        val method = mutableClassDefBy(owner).methods.first { it.isAttachBaseContext() }
        if (method.implementation == null) {
            throw PatchException("${owner.type}->attachBaseContext has no code to patch")
        }

        // p1 is the base Context parameter. The range form takes any register number.
        method.addInstructions(
            0,
            "invoke-static/range { p1 .. p1 }, $EXTENSION_CLASS->seedLanguages($CONTEXT_TYPE)V",
        )
    }
}
