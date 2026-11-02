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

    /** The name of the language-seed patch, whose option carries the languages. */
    const val SEED_PATCH_NAME = "Seed keyboard languages"

    /**
     * The version of the bundled patch set. Must match gboard-patches' Gboard.PATCHES_VERSION, which
     * the manifest patch writes into the clone as [PATCHES_VERSION_META] (bump both together). The
     * clone is rebuilt when its written version is older, even if the stock Gboard has not changed.
     */
    const val PATCHES_VERSION = 1

    /** The clone's manifest meta-data that holds the patch-set version it was built with. */
    const val PATCHES_VERSION_META = "pesto.patchesVersion"
}

/** What patching should do, given the stock Gboard and any already-installed patched clone. */
enum class PatchAction {
    /** No patched clone yet: build and install one. */
    PATCH,

    /** A clone is installed but older (Gboard or patches) than now: rebuild and install over it. */
    UPDATE,

    /**
     * A clone is installed but signed with a different key (Pesto's data was cleared or reinstalled,
     * so the local key is new): an update would fail INSTALL_FAILED_UPDATE_INCOMPATIBLE and the
     * dictation service would reject it, so uninstall the old clone first, then patch.
     */
    REINSTALL,

    /** The clone matches the stock Gboard and the current patches: nothing to do. */
    UP_TO_DATE,
}

/** An installed patched clone, as [PatchPlan] needs to see it. */
data class InstalledClone(
    /** The clone's version code; it keeps the stock Gboard's, which the rename patch does not change. */
    val versionCode: Long,
    /** The patch-set version written in its manifest ([KeyboardPatch.PATCHES_VERSION_META]), or 0. */
    val patchesVersion: Int,
    /** Whether it is signed with this install's current local key. */
    val signedByUs: Boolean,
)

object PatchPlan {
    /**
     * @param stockVersionCode the version code of the installed stock Gboard
     * @param installed the installed patched clone, or null if none
     * @param currentPatchesVersion the patch-set version the app ships ([KeyboardPatch.PATCHES_VERSION])
     *
     * A clone signed with another key is reinstalled; otherwise an older Gboard version or an older
     * patch set is updated, and a clone that matches both is left alone. A clone newer than the
     * stock Gboard (a downgrade, or updates off) is not rebuilt.
     */
    fun decide(
        stockVersionCode: Long,
        installed: InstalledClone?,
        currentPatchesVersion: Int = KeyboardPatch.PATCHES_VERSION,
    ): PatchAction = when {
        installed == null -> PatchAction.PATCH
        !installed.signedByUs -> PatchAction.REINSTALL
        installed.versionCode < stockVersionCode -> PatchAction.UPDATE
        installed.patchesVersion < currentPatchesVersion -> PatchAction.UPDATE
        else -> PatchAction.UP_TO_DATE
    }
}
