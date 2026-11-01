package com.victorpolitakis.pesto.keyboard

/** Names and constants shared by the on-device keyboard patcher (phase 3) and Settings (phase 4). */
object KeyboardPatch {
    /** The stock Google Keyboard. */
    const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"

    /**
     * The package the patched keyboard is installed under, so it lives next to the stock Gboard.
     * Must match the rename patch in gboard-patches (Gboard.PATCHED_PACKAGE_NAME).
     * TODO(phase 6): the final package of the project.
     */
    const val PATCHED_PACKAGE = "com.victorpolitakis.pesto.gboard"

    /** The patches bundle shipped as an asset (built by gboard-patches, phase 1). */
    const val PATCHES_ASSET = "patches/gboard-patches.mpp"

    /** The option of the language-seed patch that carries the pesto_locales.txt contents. */
    const val PESTO_LOCALES_OPTION = "pestoLocales"
}

/** What patching should do, given the stock Gboard and any already-installed patched clone. */
enum class PatchAction {
    /** No patched clone yet: build and install one. */
    PATCH,

    /** A patched clone is installed, but older than the stock Gboard: rebuild and update it. */
    UPDATE,

    /** The patched clone is already at the stock Gboard's version: nothing to do. */
    UP_TO_DATE,
}

object PatchPlan {
    /**
     * @param stockVersionCode the version code of the installed stock Gboard
     * @param patchedVersionCode the version code of the installed patched clone, or null if none
     *
     * The patched clone keeps the stock version code (the rename patch does not change it), so a
     * clone with a smaller code was built from an older Gboard and is updated. A newer or equal
     * clone is left alone.
     */
    fun decide(stockVersionCode: Long, patchedVersionCode: Long?): PatchAction = when {
        patchedVersionCode == null -> PatchAction.PATCH
        patchedVersionCode < stockVersionCode -> PatchAction.UPDATE
        else -> PatchAction.UP_TO_DATE
    }
}
