package com.victorpolitakis.pesto.backend

import org.junit.Assert.assertEquals
import org.junit.Test

/** The HTTP routes come from the ws:// URL of the dictation in BuildConfig. */
class BackendUrlsTest {
    @Test
    fun `health and pair next to the dictation`() {
        val dictate = httpUrl("ws://127.0.0.1:8000/v1/dictate")
        assertEquals("http://127.0.0.1:8000/health", dictate.resolve("../health").toString())
        assertEquals("http://127.0.0.1:8000/v1/pair", dictate.resolve("pair").toString())
    }

    @Test
    fun `wss becomes https, and a path prefix stays`() {
        val dictate = httpUrl("wss://pesto.example.org/pesto/v1/dictate")
        assertEquals("https://pesto.example.org/pesto/health", dictate.resolve("../health").toString())
        assertEquals("https://pesto.example.org/pesto/v1/pair", dictate.resolve("pair").toString())
    }
}
