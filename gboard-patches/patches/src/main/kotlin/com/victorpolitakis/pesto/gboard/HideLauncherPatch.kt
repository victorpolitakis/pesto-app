package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"

/**
 * Keeps the patched keyboard out of the launcher's app drawer.
 *
 * The keyboard is configured from Pesto and the system keyboard settings, so it needs no launcher
 * entry. Every `android.intent.category.LAUNCHER` category is removed from the manifest; the `MAIN`
 * action is left in place so the component is otherwise unchanged.
 */
@Suppress("unused")
val hideLauncherPatch = resourcePatch(
    name = "Hide from launcher",
    description = "Removes the patched keyboard's launcher icon.",
    default = true,
) {
    compatibleWith(Gboard.COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use { manifest ->
            val categories = manifest.getElementsByTagName("category")
            // Collect first; removing while iterating a live NodeList shifts the indices.
            val launcherCategories = (0 until categories.length)
                .map { categories.item(it) as Element }
                .filter { it.getAttributeNS(ANDROID_NS, "name") == LAUNCHER_CATEGORY }

            launcherCategories.forEach { it.parentNode.removeChild(it) }
        }
    }
}
