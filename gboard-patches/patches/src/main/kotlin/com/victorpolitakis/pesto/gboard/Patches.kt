package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.patch.Patch

/**
 * Every "Gboard with Pesto" patch, in the order the project's plan lists them. The on-device
 * patcher (phase 3) and the tests use this set.
 */
val gboardWithPestoPatches: Set<Patch<*>> = setOf(
    renamePatch,
    signatureBypassPatch,
    micPatch,
    languageSeedPatch,
    hideLauncherPatch,
    manifestPatch,
)
