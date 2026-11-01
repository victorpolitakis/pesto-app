package com.victorpolitakis.pesto.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Patch, update or nothing, from the stock and the installed clone version codes. */
class PatchPlanTest {
    @Test
    fun `no clone yet means patch`() {
        assertEquals(PatchAction.PATCH, PatchPlan.decide(stockVersionCode = 100, patchedVersionCode = null))
    }

    @Test
    fun `an older clone is updated`() {
        assertEquals(PatchAction.UPDATE, PatchPlan.decide(stockVersionCode = 200, patchedVersionCode = 100))
    }

    @Test
    fun `a clone at the stock version is left alone`() {
        assertEquals(PatchAction.UP_TO_DATE, PatchPlan.decide(stockVersionCode = 200, patchedVersionCode = 200))
    }

    @Test
    fun `a clone newer than the stock is left alone`() {
        // The stock Gboard was downgraded, or updates are off; do not rebuild.
        assertEquals(PatchAction.UP_TO_DATE, PatchPlan.decide(stockVersionCode = 200, patchedVersionCode = 300))
    }
}
