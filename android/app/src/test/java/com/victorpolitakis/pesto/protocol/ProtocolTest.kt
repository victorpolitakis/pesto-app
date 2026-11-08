package com.victorpolitakis.pesto.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The JSON must match the examples of docs/protocol.md, read from the file itself. */
class ProtocolTest {
    private val examples: List<String> by lazy {
        val doc = File("../../docs/protocol.md").readText()
        Regex("```json\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(doc).map { it.groupValues[1] }.toList()
    }

    private fun example(type: String): JsonElement =
        examples.first { "\"type\": \"$type\"" in it }.let { Json.parseToJsonElement(it) }

    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `start matches the docs example`() {
        val start = Start(
            terms = listOf("Πολιτάκης", "Pesto"),
            profile = "mail",
            fieldContext = FieldContext(
                packageName = "com.google.android.gm",
                inputType = "text",
                hint = "Compose email",
                singleLine = false,
                isPassword = false,
                isIncognito = false,
            ),
        )
        assertEquals(example("start"), json(encode(start)))
    }

    @Test
    fun `missing field context values are left out`() {
        assertEquals(
            json("""{"type":"start","language_hints":["el","en"],"language_hints_strict":false,"terms":[],"profile":"raw","field_context":{"package":"x"},"sample_rate":16000,"encoding":"pcm_s16le"}"""),
            json(encode(Start(fieldContext = FieldContext(packageName = "x")))),
        )
    }

    @Test
    fun `control messages`() {
        assertEquals(json("""{"type":"finalize"}"""), json(encode(Finalize)))
        assertEquals(json("""{"type":"cancel"}"""), json(encode(Cancel)))
        assertEquals(json("""{"type":"pause"}"""), json(encode(Pause)))
        assertEquals(json("""{"type":"resume"}"""), json(encode(Resume)))
    }

    @Test
    fun `server messages from the docs decode`() {
        assertEquals(Ready, decode(example("ready").toString()))
        assertEquals(Partial("Καλημέρα Γιώργο, το ", "meeting πάει"), decode(example("partial").toString()))
        val final = decode(example("final").toString()) as Final
        assertEquals("mail", final.profile)
        assertEquals("user", final.endReason)
        assertEquals(Stats(5.7, 17, 152, 1496, 1648), final.stats)
        assertEquals(Error("unknown_profile", "no profile named 'x'", false), decode(example("error").toString()))
    }

    @Test
    fun `assistant requests and responses match the docs examples`() {
        val plan = AssistantPlanRequest(
            locale = "el",
            query = "Τι είπα για το meeting;",
            previousQueries = listOf("Πόσα λεπτά έχω κερδίσει;"),
            previous = PreviousPlanState(
                tool = AssistantToolName.STAT,
                keywords = emptyList(),
                metric = AssistantStatMetric.MINUTES_SAVED,
                period = AssistantTimePeriod.TODAY,
            ),
        )
        assertEquals(example("plan"), json(encodeAssistant(plan)))

        val answer = AssistantAnswerRequest(
            planId = "pX9_2kLm8vQr4tYz1wBc7nDf0gHj3sKp",
            locale = "el",
            query = "Τι είπα για το meeting;",
            toolResults = ToolResults(
                historyMatches = listOf(
                    HistoryEntrySummary(
                        createdAt = "2026-10-30 18:26",
                        app = "Gmail",
                        profile = "mail",
                        words = 17,
                        snippet = "Καλημέρα Γιώργο, το meeting πάει για την Πέμπτη στις 10:00.",
                    ),
                ),
            ),
        )
        assertEquals(example("answer"), json(encodeAssistant(answer)))

        assertEquals(
            AssistantToolCalls(
                planId = "pX9_2kLm8vQr4tYz1wBc7nDf0gHj3sKp",
                tools = listOf(
                    ToolCall(
                        tool = AssistantToolName.SEARCH_HISTORY,
                        keywords = listOf("meeting"),
                        period = AssistantTimePeriod.WEEK,
                    ),
                ),
            ),
            decodeAssistant(example("tool_calls").toString()),
        )
        assertEquals(
            AssistantReply(
                reply = "Την Πέμπτη έστειλες στο Gmail ότι το meeting πάει στις 10:00.",
                navigate = NavigationTarget(AssistantDestination.HISTORY),
                relevant = listOf(0),
            ),
            decodeAssistant(example("reply").toString()),
        )
    }

    @Test
    fun `assistant keyword validation rejects FTS operators, quotes, wildcards, and over-bounds`() {
        for (good in listOf("Πέμπτη", "meeting", "e-mail", "v2")) {
            assertTrue("expected valid: $good", isValidAssistantKeyword(good))
        }
        for (bad in listOf("", "a", "α".repeat(31), "\"meeting\"", "meet*", "NEAR", "or", "AND", "not", "two words", "-a", "a-", "a--b")) {
            assertFalse("expected invalid: $bad", isValidAssistantKeyword(bad))
        }
        assertEquals(
            listOf("Πέμπτη", "meeting", "v2"),
            cleanAssistantKeywords(listOf("OR", " Πέμπτη ", "meeting", "Πέμπτη", "v2", "fourth")),
        )
    }
}
