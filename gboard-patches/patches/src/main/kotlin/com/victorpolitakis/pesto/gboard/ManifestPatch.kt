package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"

/**
 * Manifest wiring for talking to Pesto.
 *
 * - Adds a `<queries>` entry for the Pesto package so the patched keyboard can see and bind to
 *   Pesto's dictation service under package visibility (Android 11+).
 * - Sets the application label to "Gboard with Pesto" so it is told apart from the stock keyboard.
 * - Ensures `RECORD_AUDIO` is declared. Gboard already declares it for its own voice input; this is
 *   a safety net so the microphone works even if a build dropped it.
 */
@Suppress("unused")
val manifestPatch = resourcePatch(
    name = "Pesto manifest wiring",
    description = "Declares Pesto package visibility, the keyboard label and the microphone permission.",
    default = true,
) {
    compatibleWith(Gboard.COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use { manifest ->
            val manifestElement = manifest.getElementsByTagName("manifest").item(0) as Element

            // <queries><package android:name="com.victorpolitakis.pesto"/></queries>
            val queries = manifest.getElementsByTagName("queries").let { existing ->
                if (existing.length > 0) existing.item(0) as Element
                else manifest.createElement("queries").also { manifestElement.appendChild(it) }
            }
            val alreadyQueried = (0 until queries.childNodes.length)
                .mapNotNull { queries.childNodes.item(it) as? Element }
                .any { it.tagName == "package" && it.getAttributeNS(ANDROID_NS, "name") == Gboard.PESTO_PACKAGE_NAME }
            if (!alreadyQueried) {
                val pkg = manifest.createElement("package")
                pkg.setAttributeNS(ANDROID_NS, "android:name", Gboard.PESTO_PACKAGE_NAME)
                queries.appendChild(pkg)
            }

            // Application label, and the patch-set version so the app knows when to rebuild the
            // clone after the patches change but Gboard has not (PatchPlan).
            (manifest.getElementsByTagName("application").item(0) as? Element)?.let { application ->
                application.setAttributeNS(ANDROID_NS, "android:label", Gboard.DISPLAY_NAME)

                val existing = (0 until application.childNodes.length)
                    .mapNotNull { application.childNodes.item(it) as? Element }
                    .firstOrNull {
                        it.tagName == "meta-data" &&
                            it.getAttributeNS(ANDROID_NS, "name") == Gboard.PATCHES_VERSION_META
                    }
                val meta = existing ?: manifest.createElement("meta-data").also {
                    it.setAttributeNS(ANDROID_NS, "android:name", Gboard.PATCHES_VERSION_META)
                    application.appendChild(it)
                }
                meta.setAttributeNS(ANDROID_NS, "android:value", Gboard.PATCHES_VERSION.toString())
            }

            // Ensure RECORD_AUDIO is declared.
            val usesPermissions = manifest.getElementsByTagName("uses-permission")
            val hasRecordAudio = (0 until usesPermissions.length)
                .map { usesPermissions.item(it) as Element }
                .any { it.getAttributeNS(ANDROID_NS, "name") == RECORD_AUDIO }
            if (!hasRecordAudio) {
                val perm = manifest.createElement("uses-permission")
                perm.setAttributeNS(ANDROID_NS, "android:name", RECORD_AUDIO)
                manifestElement.appendChild(perm)
            }
        }
    }
}
