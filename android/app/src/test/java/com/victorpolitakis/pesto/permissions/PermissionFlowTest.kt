package com.victorpolitakis.pesto.permissions

import com.victorpolitakis.pesto.permissions.Requirement.ACCESSIBILITY
import com.victorpolitakis.pesto.permissions.Requirement.BATTERY
import com.victorpolitakis.pesto.permissions.Requirement.MICROPHONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionFlowTest {
    private val flow = PermissionFlow(listOf(MICROPHONE, ACCESSIBILITY, BATTERY))

    @Test
    fun `clean install asks one by one, in order`() {
        assertEquals(MICROPHONE, flow.next(setOf(MICROPHONE, ACCESSIBILITY, BATTERY)))
        assertEquals(ACCESSIBILITY, flow.next(setOf(ACCESSIBILITY, BATTERY))) // back from the dialog, granted
        assertEquals(BATTERY, flow.next(setOf(BATTERY))) // back from the settings page, granted
        assertNull(flow.next(emptySet()))
    }

    @Test
    fun `everything granted asks nothing`() {
        assertNull(flow.next(emptySet()))
    }

    @Test
    fun `coming back without granting does not loop`() {
        assertEquals(ACCESSIBILITY, flow.next(setOf(ACCESSIBILITY)))
        assertNull(flow.next(setOf(ACCESSIBILITY))) // came back, still missing: not asked again now
    }

    @Test
    fun `a denied one is skipped and the next is asked`() {
        assertEquals(MICROPHONE, flow.next(setOf(MICROPHONE, ACCESSIBILITY)))
        assertEquals(ACCESSIBILITY, flow.next(setOf(MICROPHONE, ACCESSIBILITY)))
    }

    @Test
    fun `next visit asks again for what is still missing`() {
        assertEquals(ACCESSIBILITY, flow.next(setOf(ACCESSIBILITY)))
        assertNull(flow.next(setOf(ACCESSIBILITY)))
        flow.onStop() // the user left the app
        assertEquals(ACCESSIBILITY, flow.next(setOf(ACCESSIBILITY)))
    }

    @Test
    fun `leaving for a page we opened is not a new visit`() {
        assertEquals(ACCESSIBILITY, flow.next(setOf(ACCESSIBILITY)))
        flow.onStop() // we opened the accessibility settings
        assertNull(flow.next(setOf(ACCESSIBILITY)))
    }

    @Test
    fun `revoked later is asked at the next visit`() {
        assertNull(flow.next(emptySet()))
        flow.onStop()
        assertEquals(ACCESSIBILITY, flow.next(setOf(ACCESSIBILITY))) // revoked in the settings meanwhile
    }
}
