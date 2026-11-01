package com.victorpolitakis.pesto.keyboard

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The SHA-256 digests of the signing certificates a patched keyboard may be signed with. */
fun interface TrustedKeyboards {
    fun certificates(): List<ByteArray>
}

/** Whether an app (by uid) is signed with a certificate, as [PackageManager.hasSigningCertificate] says. */
fun interface SigningCertificates {
    fun hasCertificate(uid: Int, sha256: ByteArray): Boolean
}

/**
 * Who may dictate through the service: only an app signed with the local key of the patched
 * keyboard. Any app can bind an exported service, and the service records audio into text and
 * History, so the caller is checked on every message, by certificate, not by package name (which
 * any app can take).
 *
 * The uid is the sender of the message (`Message.sendingUid`), set by Binder. In the handler of a
 * Messenger, `Binder.getCallingUid()` is Pesto's own uid: the message was already queued.
 */
class KeyboardCallerCheck @Inject constructor(
    private val trusted: TrustedKeyboards,
    private val certificates: SigningCertificates,
) {
    fun isAllowed(uid: Int): Boolean = trusted.certificates().any { certificates.hasCertificate(uid, it) }

    companion object {
        /**
         * Digests as `apksigner verify --print-certs` prints them: hex, with or without `:`, any case,
         * several separated by `,` or whitespace. Anything that is not 32 bytes of hex is dropped.
         */
        fun parseSha256s(text: String): List<ByteArray> = text.split(',', ' ', '\n', '\t')
            .map { it.replace(":", "").trim() }
            .filter { it.length == 64 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
            .map { hex -> ByteArray(32) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }
    }
}

class PackageManagerCertificates @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SigningCertificates {
    override fun hasCertificate(uid: Int, sha256: ByteArray): Boolean =
        context.packageManager.hasSigningCertificate(uid, sha256, PackageManager.CERT_INPUT_SHA256)
}
