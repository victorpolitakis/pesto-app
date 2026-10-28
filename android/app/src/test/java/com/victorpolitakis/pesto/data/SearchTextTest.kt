package com.victorpolitakis.pesto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextTest {
    @Test
    fun `accents and case`() {
        assertEquals("καλημερα", SearchText.normalize("Καλημέρα"))
        assertEquals("καλημερα", SearchText.normalize("ΚΑΛΗΜΈΡΑ"))
        assertEquals("ωρα αρχη ελα ηρθε ιδεα ολα υγεια", SearchText.normalize("Ώρα Αρχή έλα Ήρθε Ιδέα όλα Υγεία"))
    }

    @Test
    fun `diaeresis, with and without an accent`() {
        assertEquals("προυποθεση", SearchText.normalize("προϋπόθεση"))
        assertEquals("ι υ αιδονι", SearchText.normalize("ΐ ΰ Αϊδόνι"))
    }

    @Test
    fun `final sigma is a sigma, also from capitals`() {
        assertEquals("οδοσ σταδιου", SearchText.normalize("Οδός Σταδίου"))
        assertEquals("οδοσ σταδιου", SearchText.normalize("ΟΔΟΣ ΣΤΑΔΙΟΥ"))
    }

    @Test
    fun `english and other latin text`() {
        assertEquals("cafe meeting deadline", SearchText.normalize("Café MEETING Deadline"))
        assertEquals("το meeting τησ δευτερασ", SearchText.normalize("Το Meeting της Δευτέρας"))
    }

    @Test
    fun `words like the backend counts them`() {
        assertEquals(4, SearchText.words("  Καλημέρα   Γιώργο,\nτι κάνεις; "))
        assertEquals(0, SearchText.words(""))
        assertEquals(0, SearchText.words(" \n\t"))
        assertEquals(1, SearchText.words("API"))
    }

    @Test
    fun `the index keeps both texts, once when they are the same`() {
        assertEquals("γεια σου", SearchText.of("Γεια σου", "Γεια σου"))
        assertEquals("γεια σου γεια σου!", SearchText.of("γεια σου", "Γεια σου!"))
    }

    @Test
    fun `every word of the query is a prefix`() {
        assertEquals("καλημ*", SearchText.ftsQuery("Καλημ"))
        assertEquals("καλημερα* γιωργο*", SearchText.ftsQuery("  καλημέρα, Γιώργο "))
    }

    @Test
    fun `nothing is read as FTS syntax`() {
        assertEquals("and* or* not* x*", SearchText.ftsQuery("AND \"OR\" NOT -x*"))
        assertEquals("e* mail*", SearchText.ftsQuery("e-mail"))
    }

    @Test
    fun `too short queries search nothing`() {
        assertNull(SearchText.ftsQuery(""))
        assertNull(SearchText.ftsQuery("α"))
        assertNull(SearchText.ftsQuery(" ά , "))
        assertEquals("αβ*", SearchText.ftsQuery("αβ"))
    }
}
