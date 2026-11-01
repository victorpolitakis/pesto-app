package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Installs the patched keyboard under its own package name so it lives next to the stock Gboard
 * instead of replacing it.
 *
 * Android forbids two installed apps from declaring the same provider authority or the same custom
 * permission, so renaming the package is not enough on its own: every authority and custom
 * permission that embeds the original package name is prefixed too. The patched classes keep their
 * original package, so each component's relative `android:name` ("`.Foo`") is first expanded to its
 * original fully qualified name, so it still resolves after the manifest package changes.
 *
 * The package is temporary; phase 6 of the project renames everything to the final package.
 *
 * Changing the manifest `package` attribute is what drives the resource table rename inside the
 * patcher (it reads the new package from the manifest after patches run).
 */
@Suppress("unused")
val renamePatch = resourcePatch(
    name = "Install as Gboard with Pesto clone",
    description = "Installs the patched keyboard under its own package, alongside the stock Gboard.",
    default = true,
) {
    compatibleWith(Gboard.COMPATIBILITY)

    execute {
        val original = Gboard.PACKAGE_NAME
        val renamed = Gboard.PATCHED_PACKAGE_NAME

        document("AndroidManifest.xml").use { manifest ->
            val manifestElement = manifest.getElementsByTagName("manifest").item(0) as Element

            // Expand relative component names to the original package before the package changes,
            // so the classes (which are not renamed) still resolve.
            manifest.elements().forEach { element ->
                val name = element.getAttributeNS(ANDROID_NS, "name")
                if (name.startsWith(".")) {
                    element.setAttributeNS(ANDROID_NS, "android:name", original + name)
                }
            }

            // Prefix every authority and custom permission that names the original package.
            manifest.elements().forEach { element ->
                prefixAttribute(element, "authorities", original, renamed)
                // Custom permissions and permission references; framework permissions
                // ("android.permission.*") are left untouched because they do not start with the
                // original package name.
                prefixAttribute(element, "name", original, renamed, onlyTags = PERMISSION_TAGS)
                prefixAttribute(element, "permission", original, renamed)
                prefixAttribute(element, "targetPackage", original, renamed)
            }

            // Drive the rename.
            manifestElement.setAttribute("package", renamed)
        }
    }
}

private val PERMISSION_TAGS = setOf("permission", "uses-permission", "permission-group", "permission-tree")

/**
 * Prefixes an `android:`-namespaced attribute's package-qualified values. [value] may be a
 * semicolon-separated list (as `android:authorities` can be). Only values equal to [original] or
 * starting with "`original.`" are rewritten to [renamed]. When [onlyTags] is set, only elements
 * with those tag names are considered.
 */
private fun prefixAttribute(
    element: Element,
    attribute: String,
    original: String,
    renamed: String,
    onlyTags: Set<String>? = null,
) {
    if (onlyTags != null && element.tagName !in onlyTags) return
    val value = element.getAttributeNS(ANDROID_NS, attribute)
    if (value.isEmpty()) return

    val rewritten = value.split(";").joinToString(";") { part ->
        when {
            part == original -> renamed
            part.startsWith("$original.") -> renamed + part.substring(original.length)
            else -> part
        }
    }
    if (rewritten != value) element.setAttributeNS(ANDROID_NS, "android:$attribute", rewritten)
}

/** Every element in the document, in document order. */
private fun org.w3c.dom.Document.elements(): List<Element> {
    val nodes = getElementsByTagName("*")
    return (0 until nodes.length).map { nodes.item(it) as Element }
}
