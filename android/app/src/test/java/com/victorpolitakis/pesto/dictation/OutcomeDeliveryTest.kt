package com.victorpolitakis.pesto.dictation

import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.data.DictationHistory
import com.victorpolitakis.pesto.data.NewDictation
import com.victorpolitakis.pesto.insert.InsertMethod
import com.victorpolitakis.pesto.insert.Inserter
import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Stats
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeDeliveryTest {
    private val inserted = mutableListOf<String>()
    private val recorded = mutableListOf<NewDictation>()
    private val delivery = OutcomeDelivery(
        inserter = object : Inserter {
            override suspend fun insert(text: String): InsertMethod {
                inserted += text
                return InsertMethod.COMMIT_TEXT
            }
        },
        history = object : DictationHistory {
            override suspend fun record(dictation: NewDictation) {
                recorded += dictation
            }
        },
        log = FakeLog(),
        clock = { 1000L },
    )
    private val whatsapp = FieldContext(packageName = "com.whatsapp", inputType = "text", isIncognito = false)
    private val final = Final("καλημέρα", "Καλημέρα.", "raw", null, "user", Stats(1.0, 1, 150, null, 160))

    @Test
    fun `done is inserted and recorded with the app and the field`() = runTest {
        delivery.deliver(Outcome.Done(final), whatsapp)
        assertEquals(listOf("Καλημέρα."), inserted)
        assertEquals(listOf(NewDictation(1000L, whatsapp, final, null, "COMMIT_TEXT")), recorded)
    }

    @Test
    fun `scenario 10 - cancel leaves no trace`() = runTest {
        delivery.deliver(Outcome.Cancelled, whatsapp)
        assertEquals(emptyList<String>(), inserted)
        assertEquals(emptyList<NewDictation>(), recorded)
    }

    @Test
    fun `failures leave no trace`() = runTest {
        delivery.deliver(Outcome.Failed("no_server"), whatsapp)
        assertEquals(emptyList<String>(), inserted)
        assertEquals(emptyList<NewDictation>(), recorded)
    }

    @Test
    fun `incognito is inserted but never recorded`() = runTest {
        delivery.deliver(Outcome.Done(final), whatsapp.copy(isIncognito = true))
        assertEquals(listOf("Καλημέρα."), inserted)
        assertEquals(emptyList<NewDictation>(), recorded)
    }

    @Test
    fun `a broken connection inserts and records the last text`() = runTest {
        delivery.deliver(Outcome.ConnectionLost("μέχρι εδώ"), whatsapp)
        assertEquals(listOf("μέχρι εδώ"), inserted)
        assertNull(recorded.single().final)
        assertEquals("μέχρι εδώ", recorded.single().lostText)
    }
}
