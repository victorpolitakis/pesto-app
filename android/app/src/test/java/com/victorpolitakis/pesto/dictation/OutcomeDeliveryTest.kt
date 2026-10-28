package com.victorpolitakis.pesto.dictation

import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.data.DictationHistory
import com.victorpolitakis.pesto.data.NewDictation
import com.victorpolitakis.pesto.insert.InsertMethod
import com.victorpolitakis.pesto.insert.Inserter
import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Start
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
    private val start = Start(profile = "chatter", fieldContext = whatsapp)
    private val final = Final("καλημέρα", "Καλημέρα.", "chatter", null, "user", Stats(1.0, 1, 150, null, 160))

    @Test
    fun `done is inserted and recorded with the app, the field, the profile and the tap`() = runTest {
        delivery.deliver(Outcome.Done(final), start, startedAt = 900L)
        assertEquals(listOf("Καλημέρα."), inserted)
        assertEquals(listOf(NewDictation(1000L, 900L, whatsapp, "chatter", final, null, "COMMIT_TEXT")), recorded)
    }

    @Test
    fun `scenario 10 - cancel leaves no trace`() = runTest {
        delivery.deliver(Outcome.Cancelled, start, 900L)
        assertEquals(emptyList<String>(), inserted)
        assertEquals(emptyList<NewDictation>(), recorded)
    }

    @Test
    fun `failures leave no trace`() = runTest {
        delivery.deliver(Outcome.Failed("no_server"), start, 900L)
        assertEquals(emptyList<String>(), inserted)
        assertEquals(emptyList<NewDictation>(), recorded)
    }

    @Test
    fun `incognito is inserted but never recorded`() = runTest {
        delivery.deliver(Outcome.Done(final), start.copy(fieldContext = whatsapp.copy(isIncognito = true)), 900L)
        assertEquals(listOf("Καλημέρα."), inserted)
        assertEquals(emptyList<NewDictation>(), recorded)
    }

    @Test
    fun `a broken connection inserts and records the last text, with the profile asked for`() = runTest {
        delivery.deliver(Outcome.ConnectionLost("μέχρι εδώ"), start.copy(profile = "mail"), 900L)
        assertEquals(listOf("μέχρι εδώ"), inserted)
        assertNull(recorded.single().final)
        assertEquals("μέχρι εδώ", recorded.single().lostText)
        assertEquals("mail", recorded.single().requestedProfile)
    }
}
