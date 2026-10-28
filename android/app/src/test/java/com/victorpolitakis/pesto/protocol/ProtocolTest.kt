package com.victorpolitakis.pesto.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
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
}
