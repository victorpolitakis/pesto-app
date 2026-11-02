package com.victorpolitakis.pesto.keyboard

import android.content.Context
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.pm.PackageInfoCompat
import com.victorpolitakis.pesto.util.PestoLog
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
class GboardInstall @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val log: PestoLog,
) {
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

    /**
     * The installed patched clone, or null if none. [localCertSha256] is this install's signing
     * certificate digest ([LocalSigningKey.certificateSha256Bytes]); when it is null there is no
     * local key yet, so no clone can be ours.
     */
    fun installedClone(localCertSha256: ByteArray?): InstalledClone? {
        val pkg = KeyboardPatch.PATCHED_PACKAGE
        val info = runCatching {
            packageManager.getPackageInfo(pkg, PackageManager.GET_META_DATA)
        }.getOrNull() ?: return null
        // ARSCLib decides the meta-data value's type when it rebuilds the manifest; read it whether
        // it comes back an int or a string. getInt writes a warning to logcat and returns the
        // default on a type mismatch, so fall back to the string form.
        val meta = info.applicationInfo?.metaData
        val patchesVersion = meta?.getInt(KeyboardPatch.PATCHES_VERSION_META, 0)?.takeIf { it != 0 }
            ?: meta?.getString(KeyboardPatch.PATCHES_VERSION_META)?.toIntOrNull()
            ?: 0
        val signedByUs = localCertSha256 != null &&
            packageManager.hasSigningCertificate(pkg, localCertSha256, PackageManager.CERT_INPUT_SHA256)
        return InstalledClone(PackageInfoCompat.getLongVersionCode(info), patchesVersion, signedByUs)
    }

    /** What to do for the stock Gboard: patch, update, reinstall or nothing. */
    fun planFor(stockVersionCode: Long, localCertSha256: ByteArray?): PatchAction =
        PatchPlan.decide(stockVersionCode, installedClone(localCertSha256))

    /**
     * The enabled languages of the keyboard [packageName], as the pesto_locales.txt the seed patch
     * bundles; null when that keyboard is not found or has no languages. Read from its own enabled
     * subtypes. It works for any installed IME, the patched clone included (it is an IME too), whose
     * subtypes show the user's most recent language choices.
     *
     * @param allowImplicit see [InputMethodManager.getEnabledInputMethodSubtypeList]; false reads
     *   only the subtypes the user enabled.
     */
    fun languagesFileFor(packageName: String, allowImplicit: Boolean = true): String? {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
        val ime = imm.inputMethodList.firstOrNull { it.packageName == packageName } ?: return null
        return KeyboardLocales.fileContents(imm.enabledSubtypesOf(ime, allowImplicit))
    }

    private fun InputMethodManager.enabledSubtypesOf(ime: InputMethodInfo, allowImplicit: Boolean): List<KeyboardLocales.Locale> {
        val keyboardSubtypes = getEnabledInputMethodSubtypeList(ime, allowImplicit)
            .filter { it.mode == "keyboard" || it.mode.isEmpty() }
        val locales = keyboardSubtypes.mapNotNull { subtype ->
            // languageTag only; the old `locale` field is deprecated. A subtype with no tag is skipped.
            val tag = subtype.languageTag.ifEmpty { return@mapNotNull null }
            // The subtype's PkLayoutType, when present (English); empty for Greek (S2).
            val layout = subtype.extraValueOrNull("PkLayoutType")?.ifEmpty { null }
            KeyboardLocales.Locale(tag, layout)
        }
        val skipped = keyboardSubtypes.size - locales.size
        if (skipped > 0) log.event("keyboard languages: skipped $skipped subtype(s) with no language tag")
        return locales
    }
}

/** Reads a named value out of an InputMethodSubtype's extra value (`k=v,k2=v2`). */
private fun android.view.inputmethod.InputMethodSubtype.extraValueOrNull(key: String): String? =
    if (containsExtraValueKey(key)) getExtraValueOf(key) else null
