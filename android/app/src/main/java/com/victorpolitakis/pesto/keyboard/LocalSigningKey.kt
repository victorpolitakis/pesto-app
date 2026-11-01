package com.victorpolitakis.pesto.keyboard

import android.content.Context
import app.morphe.patcher.apk.ApkSigner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The local key the patched keyboard is signed with (phase 3). It is made on the phone and kept in
 * Pesto's private storage, never inside any APK, so only this install can produce a keyboard the
 * dictation service trusts. Its certificate's SHA-256 is what PestoDictationService checks a
 * caller against (phase 2), so the trust follows the key on this phone instead of a build-time pin.
 *
 * The keystore is a BKS file (Bouncy Castle, as the patcher's signer uses), with a fixed local
 * password: the file sits in app-private storage, which only this app can read.
 */
@Singleton
class LocalSigningKey @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val keyStoreFile: File get() = File(context.filesDir, "keyboard-signing/pesto.bks")

    /** The signer for the patched keyboard, making the key on first use. */
    fun signer(): ApkSigner.Signer {
        val pair = loadOrCreate()
        return ApkSigner.newApkSigner(ALIAS, pair)
    }

    /** The SHA-256 of the signing certificate, lowercase hex, as PestoDictationService expects. */
    fun certificateSha256(): String = sha256Hex(loadOrCreate().certificate)

    private fun loadOrCreate(): ApkSigner.PrivateKeyCertificatePair {
        val file = keyStoreFile
        if (file.isFile) {
            val keyStore = file.inputStream().use { ApkSigner.readKeyStore(it, PASSWORD) }
            return ApkSigner.readPrivateKeyCertificatePair(keyStore, ALIAS, PASSWORD)
        }
        val validUntil = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }.time
        val pair = ApkSigner.newPrivateKeyCertificatePair(COMMON_NAME, validUntil)
        val keyStore = ApkSigner.newKeyStore(setOf(ApkSigner.KeyStoreEntry(ALIAS, PASSWORD, pair)))
        file.parentFile?.mkdirs()
        file.outputStream().use { keyStore.store(it, PASSWORD.toCharArray()) }
        return pair
    }

    private fun sha256Hex(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ALIAS = "pesto-keyboard"
        const val COMMON_NAME = "Pesto Keyboard"
        // The keystore is protected by app-private storage, not by this password.
        const val PASSWORD = "pesto"
    }
}
