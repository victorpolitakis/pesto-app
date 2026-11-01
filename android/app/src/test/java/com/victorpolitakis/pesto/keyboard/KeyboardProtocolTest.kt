package com.victorpolitakis.pesto.keyboard

import com.victorpolitakis.pesto.protocol.SAMPLE_RATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two sides of the keyboard's Messenger contract agree: this app's [KeyboardProtocol] and
 * `DictationProtocol` of the pestobridge extension, in gboard-patches of the same repository. The
 * extension is built apart (Morphe), so its source is read here.
 */
class KeyboardProtocolTest {
    private val source: String by lazy {
        // The working directory of the JVM tests is android/app.
        val file = File("../../gboard-patches/extensions/pestobridge/src/main/java/com/victorpolitakis/pesto/bridge/DictationProtocol.java")
        assertTrue("bridge protocol not found at ${file.absolutePath}", file.isFile)
        file.readText()
    }

    /** `public static final int NAME = 1;` and `public static final String NAME = "x";`, across lines. */
    private val bridge: Map<String, Any> by lazy {
        val ints = Regex("""static final int (\w+)\s*=\s*(-?\d+);""").findAll(source)
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val strings = Regex("""static final String (\w+)\s*=\s*"([^"]*)";""").findAll(source)
            .associate { it.groupValues[1] to it.groupValues[2] }
        ints + strings
    }

    private fun app(name: String): Any? = runCatching { KeyboardProtocol::class.java.getField(name).get(null) }.getOrNull()

    private fun appNames() = KeyboardProtocol::class.java.fields.map { it.name }

    @Test
    fun `every message and key of the keyboard has the same value here`() {
        val shared = bridge.keys.filter { it.startsWith("MSG_") || it.startsWith("KEY_") }
        assertTrue("no constants parsed", shared.size >= 10)
        for (name in shared) assertEquals(name, bridge[name], app(name))
    }

    @Test
    fun `every message and key here exists on the keyboard's side`() {
        val ours = appNames().filter { it.startsWith("MSG_") || it.startsWith("KEY_") }
        for (name in ours) assertEquals(name, app(name), bridge[name])
    }

    @Test
    fun `the keyboard binds this service, in this package`() {
        assertEquals(KeyboardProtocol.SERVICE, bridge["DICTATION_SERVICE"])
        assertEquals(PestoDictationService::class.java.name, KeyboardProtocol.SERVICE)
        assertEquals("com.victorpolitakis.pesto", bridge["PESTO_PACKAGE"])
    }

    @Test
    fun `the audio format and the context size agree`() {
        assertEquals(SAMPLE_RATE, bridge["SAMPLE_RATE_HZ"])
        assertEquals(KeyboardProtocol.CONTEXT_CHARS, bridge["CONTEXT_CHARS"])
    }
}
