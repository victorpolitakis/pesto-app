package com.victorpolitakis.pesto.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Patch, update, reinstall or nothing, from the stock Gboard and the installed clone. */
class PatchPlanTest {
    private val v = 1 // the current patch-set version in these tests

    private fun clone(versionCode: Long, patchesVersion: Int = v, signedByUs: Boolean = true) =
        InstalledClone(versionCode, patchesVersion, signedByUs)

    @Test
    fun `no clone yet means patch`() {
        assertEquals(PatchAction.PATCH, PatchPlan.decide(100, installed = null, currentPatchesVersion = v))
    }

    @Test
    fun `an older Gboard version is updated`() {
        assertEquals(PatchAction.UPDATE, PatchPlan.decide(200, clone(versionCode = 100), v))
    }

    @Test
    fun `newer patches on the same Gboard are updated`() {
        assertEquals(PatchAction.UPDATE, PatchPlan.decide(200, clone(versionCode = 200, patchesVersion = 0), v))
    }

    @Test
    fun `a clone signed with another key is reinstalled`() {
        // Pesto's data was cleared or reinstalled, so the local key is new.
        assertEquals(PatchAction.REINSTALL, PatchPlan.decide(200, clone(versionCode = 200, signedByUs = false), v))
    }

    @Test
    fun `a wrong key is reinstalled even when the versions would say up to date`() {
        assertEquals(
            PatchAction.REINSTALL,
            PatchPlan.decide(200, clone(versionCode = 200, patchesVersion = v, signedByUs = false), v),
        )
    }

    @Test
    fun `a clone at the stock version and current patches is left alone`() {
        assertEquals(PatchAction.UP_TO_DATE, PatchPlan.decide(200, clone(versionCode = 200), v))
    }

    @Test
    fun `a clone newer than the stock Gboard is left alone`() {
        assertEquals(PatchAction.UP_TO_DATE, PatchPlan.decide(200, clone(versionCode = 300), v))
    }
}
