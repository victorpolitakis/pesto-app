package com.victorpolitakis.pesto.keyboard

import android.content.Context
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The APK files of an installed app: the base, then its splits (the 18.5.6 build ships splits). */
data class ApkFiles(val base: String, val splits: List<String>) {
    val all get() = listOf(base) + splits
}

/**
 * Reads what the patcher needs about the installed keyboards: where the stock Gboard's APKs are,
 * the version codes, and the stock keyboard's enabled languages (read before the user disables it,
 * spike S2). Device-only; its pure parts are in KeyboardLocales and PatchPlan.
 */
class GboardInstall @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val packageManager get() = context.packageManager

    /** The APK files of [packageName], or null if it is not installed. */
    fun apkFiles(packageName: String): ApkFiles? {
        val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        val base = info.sourceDir ?: return null
        val splits = info.splitSourceDirs?.toList().orEmpty()
        return ApkFiles(base, splits)
    }

    /** The version code of [packageName], or null if it is not installed. */
    fun versionCode(packageName: String): Long? {
        val info = runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.getOrNull() ?: return null
        return PackageInfoCompat.getLongVersionCode(info)
    }

    /** What to do for the stock Gboard: patch, update or nothing. */
    fun planFor(stockVersionCode: Long): PatchAction =
        PatchPlan.decide(stockVersionCode, versionCode(KeyboardPatch.PATCHED_PACKAGE))

    /**
     * The stock Gboard's enabled languages, as the pesto_locales.txt the seed patch bundles. Read
     * from the stock keyboard's own enabled subtypes; null when the stock keyboard is not found or
     * has no languages (then nothing is seeded, and the clone starts with English).
     *
     * @param allowImplicit see [InputMethodManager.getEnabledInputMethodSubtypeList]; false reads
     *   only the subtypes the user enabled.
     */
    fun stockLanguagesFile(allowImplicit: Boolean = true): String? {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
        val gboard = imm.inputMethodList.firstOrNull { it.packageName == KeyboardPatch.GBOARD_PACKAGE } ?: return null
        val locales = imm.enabledSubtypesOf(gboard, allowImplicit)
        return KeyboardLocales.fileContents(locales)
    }

    private fun InputMethodManager.enabledSubtypesOf(ime: InputMethodInfo, allowImplicit: Boolean): List<KeyboardLocales.Locale> =
        getEnabledInputMethodSubtypeList(ime, allowImplicit)
            .filter { it.mode == "keyboard" || it.mode.isEmpty() }
            .mapNotNull { subtype ->
                val tag = subtype.languageTag.ifEmpty { subtype.locale }.ifEmpty { return@mapNotNull null }
                // The subtype's PkLayoutType, when present (English); empty for Greek (S2).
                val layout = subtype.extraValueOrNull("PkLayoutType")?.ifEmpty { null }
                KeyboardLocales.Locale(tag, layout)
            }
}

/** Reads a named value out of an InputMethodSubtype's extra value (`k=v,k2=v2`). */
private fun android.view.inputmethod.InputMethodSubtype.extraValueOrNull(key: String): String? =
    if (containsExtraValueKey(key)) getExtraValueOf(key) else null
