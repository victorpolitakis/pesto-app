package com.victorpolitakis.pesto.permissions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.accessibility.EditorBridge
import com.victorpolitakis.pesto.overlay.OverlayMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The order of C3: the overlay permission only with method 2 (Δ12), the pairing last (Δ24). */
@RunWith(AndroidJUnit4::class)
class RequirementChecksTest {
    private var method = OverlayMethod.ACCESSIBILITY
    private var keyboardActive = false
    private val checks = RequirementChecks(
        ApplicationProvider.getApplicationContext<Context>(),
        EditorBridge(),
        { method },
        { keyboardActive },
    )

    @Test
    fun `accessibility is required until the patched keyboard is active`() {
        assertTrue(Requirement.ACCESSIBILITY in checks.order)
        keyboardActive = true
        assertFalse(Requirement.ACCESSIBILITY in checks.order)
        // The microphone and the pairing stay.
        assertTrue(Requirement.MICROPHONE in checks.order)
        assertEquals(Requirement.PAIRING, checks.order.last())
    }

    @Test
    fun `the pairing is asked last, after the permissions`() {
        assertEquals(Requirement.PAIRING, checks.order.last())
        assertFalse(Requirement.OVERLAY in checks.order)
    }

    @Test
    fun `method 2 adds the overlay permission, read again every time`() {
        method = OverlayMethod.APPLICATION
        assertEquals(listOf(Requirement.OVERLAY, Requirement.PAIRING), checks.order.takeLast(2))
        method = OverlayMethod.ACCESSIBILITY
        assertFalse(Requirement.OVERLAY in checks.order)
    }

    @Test
    fun `Settings ask the order of the method just chosen`() {
        assertEquals(checks.order, checks.order(OverlayMethod.ACCESSIBILITY))
        assertTrue(Requirement.OVERLAY in checks.order(OverlayMethod.APPLICATION))
        // The method in memory has not changed.
        assertFalse(Requirement.OVERLAY in checks.order)
    }

    @Test
    fun `without a token the pairing is missing`() {
        assertTrue(Requirement.PAIRING in checks.missing(xiaomiShown = false, paired = false))
        assertFalse(Requirement.PAIRING in checks.missing(xiaomiShown = false, paired = true))
    }
}
