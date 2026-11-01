package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly

/**
 * Makes Gboard's internal signature check accept the re-signed, patched keyboard.
 *
 * The patched APK is signed with a local key, not Google's, so the check that compares the running
 * app's certificate would otherwise fail and disable features. Forcing it to return `true` restores
 * them. This is the same effect as Morphe's "Gboard signature bypass" used in the spikes.
 */
@Suppress("unused")
val signatureBypassPatch = bytecodePatch(
    name = "Gboard signature bypass",
    description = "Accepts the local signing key of the patched keyboard.",
    default = true,
) {
    compatibleWith(Gboard.COMPATIBILITY)

    execute {
        SignatureCheckFingerprint.method.returnEarly(true)
    }
}
