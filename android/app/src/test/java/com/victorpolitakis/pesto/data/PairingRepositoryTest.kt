package com.victorpolitakis.pesto.data

import com.victorpolitakis.pesto.FakeBackendApi
import com.victorpolitakis.pesto.FakeDeviceTokenStore
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.backend.PairResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Δ24 on the phone: the code of `pesto-server pair` becomes the token of this phone. */
class PairingRepositoryTest {
    private val api = FakeBackendApi()
    private val tokens = FakeDeviceTokenStore(token = null)
    private val log = FakeLog()
    private val pairing = PairingRepository(api, tokens, { "POCO X7 Pro" }, log)

    @Test
    fun `the code becomes the token of this phone`() = runTest {
        assertFalse(pairing.isPaired())
        assertEquals(PairResult.Paired("new-token"), pairing.pair("1234 5678"))
        assertEquals(listOf("12345678" to "POCO X7 Pro"), api.pairs)
        assertEquals("new-token", tokens.current())
        assertTrue(pairing.isPaired())
    }

    @Test
    fun `a wrong code or no answer leaves the phone unpaired`() = runTest {
        api.result = PairResult.WrongCode
        assertEquals(PairResult.WrongCode, pairing.pair("00000000"))
        api.result = PairResult.Failed("ConnectException")
        assertEquals(PairResult.Failed("ConnectException"), pairing.pair("00000000"))
        assertNull(tokens.current())
    }

    @Test
    fun `the log says the result, never the code or the token`() = runTest {
        pairing.pair("12345678")
        assertTrue(log.lines.any { "pairing result=Paired" in it })
        assertTrue(log.lines.none { "12345678" in it || "new-token" in it })
    }

    @Test
    fun `unpairing forgets the token on the phone`() = runTest {
        pairing.pair("12345678")
        pairing.unpair()
        assertFalse(pairing.isPaired())
        assertNull(tokens.current())
    }
}
