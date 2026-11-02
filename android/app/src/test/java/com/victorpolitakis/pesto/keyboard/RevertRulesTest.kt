package com.victorpolitakis.pesto.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Revert puts the normal Gboard back before removing the clone, so the phone keeps a keyboard. */
class RevertRulesTest {
    @Test
    fun `the normal Gboard is enabled first`() {
        assertEquals(RevertStep.ENABLE_STOCK, RevertRules.step(stockEnabled = false, cloneStillCurrent = true))
        assertEquals(RevertStep.ENABLE_STOCK, RevertRules.step(stockEnabled = false, cloneStillCurrent = false))
    }

    @Test
    fun `then the normal Gboard is chosen while the clone is still current`() {
        assertEquals(RevertStep.CHOOSE_STOCK, RevertRules.step(stockEnabled = true, cloneStillCurrent = true))
    }

    @Test
    fun `only then is the clone removed`() {
        assertEquals(RevertStep.REMOVE, RevertRules.step(stockEnabled = true, cloneStillCurrent = false))
    }
}
