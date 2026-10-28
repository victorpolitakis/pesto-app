package com.victorpolitakis.pesto.insert

import org.junit.Assert.assertEquals
import org.junit.Test

/** The 10 scenarios of PLAN_phase-2.md (section 4, C5). `|` = the cursor. */
class TextJoinerTest {
    /** Applies the join to "before|after" and returns the field with the new cursor. */
    private fun dictate(field: String, dictated: String): String {
        val before = field.substringBefore('|')
        val after = field.substringAfter('|')
        val piece = TextJoiner.join(before, after, dictated)
        return before + piece + "|" + after
    }

    @Test fun `1 empty field`() = assertEquals("Καλημέρα.|", dictate("|", "Καλημέρα."))

    @Test fun `2 after a word`() = assertEquals("Γεια σου.|", dictate("Γεια|", "Σου."))

    @Test fun `3 after a word and a space`() = assertEquals("Γεια σου.|", dictate("Γεια |", "Σου."))

    @Test fun `4 after a full stop and a space`() = assertEquals("Τέλος. Αύριο πάμε.|", dictate("Τέλος. |", "αύριο πάμε."))

    @Test fun `5 right after a full stop`() = assertEquals("Τέλος. Αύριο πάμε.|", dictate("Τέλος.|", "Αύριο πάμε."))

    @Test fun `6 in the middle of the text`() = assertEquals("Πάμε όλοι μαζί |αύριο", dictate("Πάμε |αύριο", "Όλοι μαζί"))

    @Test fun `7 after a new line`() = assertEquals("Γεια\nΤι κάνεις;|", dictate("Γεια\n|", "τι κάνεις;"))

    @Test fun `8 after the Greek question mark`() = assertEquals("Τι κάνεις; Καλά.|", dictate("Τι κάνεις;|", "Καλά."))

    @Test fun `9 dictation that starts with punctuation`() = assertEquals("Πάμε, και το API.|", dictate("Πάμε|", ", και το API."))

    // Scenario 10 (✕ leaves no trace) is DictationSessionTest and OutcomeDeliveryTest: nothing is inserted.

    @Test fun `semicolon after English text is not a sentence end`() =
        assertEquals("Use this; then that|", dictate("Use this;|", "Then that"))

    @Test fun `acronyms and inner capitals keep their case mid-sentence`() {
        assertEquals("Στείλε το API|", dictate("Στείλε το|", "API"))
        assertEquals("Πήρα iPhone|", dictate("Πήρα|", "iPhone"))
    }

    @Test fun `no space after an opening bracket`() = assertEquals("(καλά)|", dictate("(|", "Καλά)"))

    @Test fun `spaces around the dictation are trimmed`() = assertEquals("Γεια σου|", dictate("Γεια|", "  Σου  "))

    @Test fun `empty dictation inserts nothing`() = assertEquals("Γεια|", dictate("Γεια|", "   "))
}
