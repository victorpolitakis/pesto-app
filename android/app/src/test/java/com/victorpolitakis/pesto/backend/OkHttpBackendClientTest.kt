package com.victorpolitakis.pesto.backend

import com.victorpolitakis.pesto.FakeDeviceTokenStore
import com.victorpolitakis.pesto.protocol.Start
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.ProtocolException

/** The token of the device on the connection, and what happens when the backend refuses it (Δ24). */
@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpBackendClientTest {
    private class FakeSocket(private val request: Request) : WebSocket {
        val sent = mutableListOf<Any>()

        override fun request(): Request = request

        override fun queueSize(): Long = 0

        override fun send(text: String): Boolean = sent.add(text)

        override fun send(bytes: ByteString): Boolean = sent.add(bytes)

        override fun close(code: Int, reason: String?): Boolean = true

        override fun cancel() = Unit
    }

    /** Opens nothing: keeps the socket and its listener, so the test plays the backend. */
    private class FakeSockets : WebSocket.Factory {
        val opened = mutableListOf<Pair<FakeSocket, WebSocketListener>>()

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
            FakeSocket(request).also { opened += it to listener }
    }

    private val sockets = FakeSockets()
    private val tokens = FakeDeviceTokenStore("device-token")

    private fun TestScope.client() =
        OkHttpBackendClient(sockets, BackendConfig("ws://127.0.0.1:8000/v1/dictate"), tokens, backgroundScope)

    private fun refused(request: Request) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(403).message("Forbidden").build()

    @Test
    fun `the token of the device goes in the header, and start goes first`() = runTest {
        client().open(Start(profile = "mail")).sendAudio(ByteArray(4))
        val (socket, _) = sockets.opened.single()
        assertEquals("Bearer device-token", socket.request().header("Authorization"))
        assertTrue((socket.sent.first() as String).contains("\"type\":\"start\""))
        assertTrue(socket.sent[1] is ByteString)
    }

    @Test
    fun `without a token nothing is opened`() = runTest {
        tokens.clear()
        val connection = client().open(Start())
        assertEquals(Incoming.Closed(failure = true, reason = Incoming.UNAUTHORIZED), connection.incoming.receive())
        assertTrue(sockets.opened.isEmpty())
    }

    @Test
    fun `a refused handshake forgets the token`() = runTest {
        val connection = client().open(Start())
        val (socket, listener) = sockets.opened.single()
        listener.onFailure(socket, ProtocolException("Expected HTTP 101"), refused(socket.request()))
        runCurrent()
        assertEquals(Incoming.Closed(failure = true, reason = Incoming.UNAUTHORIZED), connection.incoming.receive())
        assertNull(tokens.current())
    }

    @Test
    fun `a close with 1008 forgets the token`() = runTest {
        val connection = client().open(Start())
        val (socket, listener) = sockets.opened.single()
        listener.onClosing(socket, 1008, "")
        runCurrent()
        assertEquals(Incoming.Closed(failure = true, reason = Incoming.UNAUTHORIZED), connection.incoming.receive())
        assertNull(tokens.current())
    }

    @Test
    fun `a network failure keeps the token`() = runTest {
        val connection = client().open(Start())
        val (socket, listener) = sockets.opened.single()
        listener.onFailure(socket, ConnectException("refused"), null)
        runCurrent()
        assertEquals(Incoming.Closed(failure = true, reason = "ConnectException"), connection.incoming.receive())
        assertEquals("device-token", tokens.current())
    }
}
