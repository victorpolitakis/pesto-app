package com.victorpolitakis.pesto.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** setText from the ±4000 chars window of the field would delete what is outside it. */
class WindowIsWholeFieldTest {
    @Test fun `a short field is the whole window`() = assertTrue(windowIsWholeField(offset = 0, charsAfterSelection = 120))

    @Test fun `a window that starts inside the field is cut before`() = assertFalse(windowIsWholeField(offset = 15, charsAfterSelection = 120))

    @Test fun `a window that is full after the selection may be cut after`() = assertFalse(windowIsWholeField(offset = 0, charsAfterSelection = 4000))
}
