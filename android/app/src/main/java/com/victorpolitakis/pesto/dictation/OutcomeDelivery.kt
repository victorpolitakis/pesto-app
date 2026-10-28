package com.victorpolitakis.pesto.dictation

import com.victorpolitakis.pesto.data.DictationHistory
import com.victorpolitakis.pesto.data.NewDictation
import com.victorpolitakis.pesto.insert.Inserter
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.util.PestoLog
import com.victorpolitakis.pesto.util.WallClock
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
    override suspend fun deliver(outcome: Outcome, start: Start, startedAt: Long) {
        val text = when (outcome) {
            is Outcome.Done -> outcome.final.text
            is Outcome.ConnectionLost -> outcome.lastText
            Outcome.Cancelled, is Outcome.Failed -> return
        }
        if (text.isBlank()) return
        val method = inserter.insert(text)
        log.event("inserted method=$method")
        if (start.fieldContext.isIncognito == true) return
        history.record(
            NewDictation(
                createdAt = clock.now(),
                startedAt = startedAt,
                context = start.fieldContext,
                requestedProfile = start.profile,
                final = (outcome as? Outcome.Done)?.final,
                lostText = (outcome as? Outcome.ConnectionLost)?.lastText,
                insertMethod = method.name,
            ),
        )
    }
}
