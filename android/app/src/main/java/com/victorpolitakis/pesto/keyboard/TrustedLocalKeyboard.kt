package com.victorpolitakis.pesto.keyboard

import com.victorpolitakis.pesto.BuildConfig
import com.victorpolitakis.pesto.util.PestoLog
import javax.inject.Inject

/**
 * Who PestoDictationService trusts (phase 2): the keyboard signed with this phone's local key
 * (phase 3), plus anything pinned at build time in pesto.trustedKeyboardSha256. Reading the local
 * certificate makes the key if it is not there yet, so the trust is ready before the first patch.
 */
class TrustedLocalKeyboard @Inject constructor(
    private val signingKey: LocalSigningKey,
    private val log: PestoLog,
) : TrustedKeyboards {
    override fun certificates(): List<ByteArray> {
        val local = runCatching { signingKey.certificateSha256() }
            .onFailure { log.event("local keyboard key unavailable: ${it.javaClass.simpleName}") }
            .getOrNull()
        val pinned = KeyboardCallerCheck.parseSha256s(BuildConfig.TRUSTED_KEYBOARD_SHA256)
        return (listOfNotNull(local) + pinned.map { it.toHex() })
            .distinct()
            .map { it.fromHex() }
    }
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

private fun String.fromHex() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
