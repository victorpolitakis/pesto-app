package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * Shared constants for the "Gboard with Pesto" patches.
 */
object Gboard {
    /** The package name of the stock Google Keyboard. Not obfuscated. */
    const val PACKAGE_NAME = "com.google.android.inputmethod.latin"

    /**
     * The package the patched keyboard is installed under until phase 6 of the project renames it.
     * It is prefixed so it can be installed alongside the stock keyboard.
     */
    const val PATCHED_PACKAGE_NAME = "com.victorpolitakis.pesto.gboard"

    /** The package of the Pesto app, which hosts the dictation service the patch talks to. */
    const val PESTO_PACKAGE_NAME = "com.victorpolitakis.pesto"

    /** The display name the patched keyboard shows in the system keyboard picker and settings. */
    const val DISPLAY_NAME = "Gboard with Pesto"

    /**
     * The two Gboard builds the spikes were developed against (RESEARCH §7, §11). Declared for
     * documentation and for Morphe Manager; the patches match by fingerprint, not by version, so
     * they are expected to keep working across nearby builds.
     *
     * 18.5.4 is a single arm64 APK; 18.5.6 beta ships as split APKs that the on-device patcher
     * merges before patching.
     */
    val COMPATIBILITY = Compatibility(
        name = DISPLAY_NAME,
        packageName = PACKAGE_NAME,
        apkFileType = ApkFileType.APK,
        appIconColor = 0x4285F4,
        targets = listOf(
            AppTarget(version = "18.5.6.993608717-beta", isExperimental = true),
            AppTarget(version = "18.5.4.993608717-release"),
        ),
    )
}
