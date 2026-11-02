package com.victorpolitakis.pesto.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** The step the user is on, from installed/enabled/current and the Xiaomi autostart. No device. */
class KeyboardStageTest {
    private fun stage(
        installed: Boolean = true,
        enabled: Boolean = true,
        current: Boolean = true,
        isXiaomi: Boolean = false,
        xiaomiAutostartShown: Boolean = false,
    ) = KeyboardStageRules.stage(installed, enabled, current, isXiaomi, xiaomiAutostartShown)

    @Test
    fun `not installed`() {
        assertEquals(KeyboardStage.NOT_INSTALLED, stage(installed = false))
    }

    @Test
    fun `installed but not enabled`() {
        assertEquals(KeyboardStage.INSTALLED_NOT_ENABLED, stage(enabled = false))
    }

    @Test
    fun `enabled but not current`() {
        assertEquals(KeyboardStage.ENABLED_NOT_CURRENT, stage(current = false))
    }

    @Test
    fun `current and not xiaomi is active`() {
        assertEquals(KeyboardStage.ACTIVE, stage(isXiaomi = false))
    }

    @Test
    fun `on xiaomi, autostart comes before active`() {
        assertEquals(KeyboardStage.XIAOMI_AUTOSTART, stage(isXiaomi = true, xiaomiAutostartShown = false))
        assertEquals(KeyboardStage.ACTIVE, stage(isXiaomi = true, xiaomiAutostartShown = true))
    }

    @Test
    fun `each step assumes the ones before are done`() {
        // Not enabled wins over not current and the Xiaomi step.
        assertEquals(KeyboardStage.INSTALLED_NOT_ENABLED, stage(enabled = false, current = false, isXiaomi = true))
    }
}
