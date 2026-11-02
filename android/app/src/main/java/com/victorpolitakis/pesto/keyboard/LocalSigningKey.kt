package com.victorpolitakis.pesto.keyboard

import android.content.Context
import app.morphe.patcher.apk.ApkSigner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The local key the patched keyboard is signed with (phase 3). It is made on the phone and kept in
 * Pesto's private storage, never inside any APK, so only this install can produce a keyboard the
 * dictation service trusts. Its certificate's SHA-256 is what PestoDictationService checks a
 * caller against (phase 2), so the trust follows the key on this phone.
 *
 * The key is made only when it is first needed to sign ([signer]); everything else reads it and
 * returns null when it is not there yet, so the main process and the `:patcher` process do not race
 * to create it. Creation is atomic: the keystore is written to a temp file and renamed into place.
 */
@Singleton
class LocalSigningKey @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val keyStoreFile: File get() = File(context.filesDir, "keyboard-signing/pesto.bks")

    /** The signer for the patched keyboard, making the key on first use. */
    @Synchronized
    fun signer(): ApkSigner.Signer = ApkSigner.newApkSigner(ALIAS, loadOrCreate())

    /** The SHA-256 of the signing certificate as lowercase hex, or null if the key does not exist yet. */
    fun certificateSha256OrNull(): String? = loadOrNull()?.let { sha256Hex(it.certificate) }

    /** The same digest as raw bytes, or null if the key does not exist yet. */
    fun certificateSha256Bytes(): ByteArray? = loadOrNull()?.let { digestOf(it.certificate) }

    private fun loadOrNull(): ApkSigner.PrivateKeyCertificatePair? {
        val file = keyStoreFile
        if (!file.isFile) return null
        val keyStore = file.inputStream().use { ApkSigner.readKeyStore(it, PASSWORD) }
        return ApkSigner.readPrivateKeyCertificatePair(keyStore, ALIAS, PASSWORD)
    }

    @Synchronized
    private fun loadOrCreate(): ApkSigner.PrivateKeyCertificatePair {
        loadOrNull()?.let { return it }
        val validUntil = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }.time
        val pair = ApkSigner.newPrivateKeyCertificatePair(COMMON_NAME, validUntil)
        val keyStore = ApkSigner.newKeyStore(setOf(ApkSigner.KeyStoreEntry(ALIAS, PASSWORD, pair)))
        val dir = keyStoreFile.parentFile?.also { it.mkdirs() }
        val temp = File.createTempFile("pesto", ".bks", dir)
        temp.outputStream().use { keyStore.store(it, PASSWORD.toCharArray()) }
        // Atomic: a reader sees either no file or the whole keystore, never a half-written one.
        if (!temp.renameTo(keyStoreFile)) {
            temp.delete()
            // Another process won the race; its key is the one to use.
            return loadOrNull() ?: throw IllegalStateException("could not create the signing key")
        }
        return pair
    }

    private fun digestOf(certificate: X509Certificate): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)

    private fun sha256Hex(certificate: X509Certificate): String =
        digestOf(certificate).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ALIAS = "pesto-keyboard"
        const val COMMON_NAME = "Pesto Keyboard"
        // The keystore is protected by app-private storage, not by this password.
        const val PASSWORD = "pesto"
    }
}
