package com.victorpolitakis.pesto.keyboard

import javax.inject.Inject

/**
 * Who PestoDictationService trusts (phase 2): only the keyboard signed with this phone's local key
 * (phase 3). The trust follows the key on this phone, with no build-time pin. This only reads the
 * key; until the first patch makes it, the list is empty and no keyboard is trusted.
 */
class TrustedLocalKeyboard @Inject constructor(
    private val signingKey: LocalSigningKey,
) : TrustedKeyboards {
    override fun certificates(): List<ByteArray> = listOfNotNull(signingKey.certificateSha256Bytes())
}
