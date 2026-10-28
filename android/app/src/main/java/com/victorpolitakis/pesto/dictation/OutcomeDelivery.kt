package com.victorpolitakis.pesto.dictation

import com.victorpolitakis.pesto.data.DictationHistory
import com.victorpolitakis.pesto.data.NewDictation
import com.victorpolitakis.pesto.insert.Inserter
import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.util.PestoLog
import javax.inject.Inject

/**
 * The end of a dictation: the text goes in (C5), then one History record,
 * except for incognito fields (C7). ✕ and failures leave no trace.
 */
class OutcomeDelivery @Inject constructor(
    private val inserter: Inserter,
    private val history: DictationHistory,
    private val log: PestoLog,
    private val clock: WallClock,
) : Delivery {
    override suspend fun deliver(outcome: Outcome, context: FieldContext) {
        val text = when (outcome) {
            is Outcome.Done -> outcome.final.text
            is Outcome.ConnectionLost -> outcome.lastText
            Outcome.Cancelled, is Outcome.Failed -> return
        }
        if (text.isBlank()) return
        val method = inserter.insert(text)
        log.event("inserted method=$method")
        if (context.isIncognito == true) return
        history.record(
            NewDictation(
                createdAt = clock.now(),
                context = context,
                final = (outcome as? Outcome.Done)?.final,
                lostText = (outcome as? Outcome.ConnectionLost)?.lastText,
                insertMethod = method.name,
            ),
        )
    }
}

/** Wall-clock time in epoch milliseconds, injectable for tests. */
fun interface WallClock {
    fun now(): Long
}
