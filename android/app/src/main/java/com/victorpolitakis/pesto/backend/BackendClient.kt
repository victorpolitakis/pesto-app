package com.victorpolitakis.pesto.backend

import com.victorpolitakis.pesto.data.DeviceTokenStore
import com.victorpolitakis.pesto.di.AppScope
import com.victorpolitakis.pesto.protocol.ClientMessage
import com.victorpolitakis.pesto.protocol.ServerMessage
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.protocol.decode
import com.victorpolitakis.pesto.protocol.encode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import javax.inject.Inject
import javax.inject.Singleton

/** What arrives from the backend: a message, or the end of the connection. */
sealed interface Incoming {
    data class Message(val message: ServerMessage) : Incoming

    /** The connection ended. [failure] is true when it broke (network, refused, bad token). */
    data class Closed(val failure: Boolean, val reason: String) : Incoming

    companion object {
        /** The [Closed.reason] when this phone has no token, or the backend refused it (Δ24). */
        const val UNAUTHORIZED = "unauthorized"
    }
}

/** One connection = one dictation (docs/protocol.md). */
interface BackendConnection {
    val incoming: ReceiveChannel<Incoming>

    /** Sends [start], then what was sent before it: start is always the first message. Once. */
    fun start(start: Start)

    /** Before [start], it waits in a queue. */
    fun sendAudio(pcm: ByteArray)

    /** Before [start], it waits in a queue. */
    fun send(message: ClientMessage)

    fun close()
}

interface BackendClient {
    /**
     * Opens the WebSocket at once, so it connects while the start message is still
     * being built (StartBuilder). Audio may be sent at once: it is queued.
     */
    fun open(): BackendConnection
}

/** The URL of the dictation. The token is not here: each phone has its own, from pairing (Δ24). */
data class BackendConfig(val url: String)

@Singleton
class OkHttpBackendClient @Inject constructor(
    private val sockets: WebSocket.Factory,
    private val config: BackendConfig,
    private val tokens: DeviceTokenStore,
    @param:AppScope private val scope: CoroutineScope,
) : BackendClient {
    override fun open(): BackendConnection {
        val incoming = Channel<Incoming>(Channel.UNLIMITED)
        // Not paired: nothing to connect with. C3 asks for the code at the next open of the app.
        val token = tokens.current() ?: return Refused(incoming)
        val request = Request.Builder()
            .url(config.url)
            .header("Authorization", "Bearer $token")
            .build()
        val socket = sockets.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                // TODO(experimental): an llm_partial message (Haiku streaming to the panel) would be
                // dropped here as an unknown type: a new class in Protocol.kt first.
                val message = runCatching { decode(text) }.getOrNull() ?: return
                incoming.trySend(Incoming.Message(message))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                // 1008 = wrong token (docs/protocol.md)
                val refused = code == 1008
                if (refused) forgetToken()
                incoming.trySend(Incoming.Closed(failure = refused, reason = if (refused) Incoming.UNAUTHORIZED else "closed:$code"))
                incoming.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // A wrong or revoked token is refused before the handshake, which ends as HTTP 403.
                val refused = response?.code == 403
                if (refused) forgetToken()
                incoming.trySend(Incoming.Closed(failure = true, reason = if (refused) Incoming.UNAUTHORIZED else t.javaClass.simpleName))
                incoming.close()
            }
        })
        return Queued(socket, incoming)
    }

    /** Revoked (or never valid): C3 asks for a new pairing at the next open of the app. */
    private fun forgetToken() {
        scope.launch { tokens.clear() }
    }

    /**
     * Holds audio and messages until [start], since the backend takes nothing else as
     * the first message. From then on OkHttp queues them until the socket opens.
     */
    private class Queued(private val socket: WebSocket, override val incoming: ReceiveChannel<Incoming>) : BackendConnection {
        private val waiting = mutableListOf<Any>()
        private var started = false

        @Synchronized
        override fun start(start: Start) {
            if (started) return
            started = true
            socket.send(encode(start))
            waiting.forEach(::write)
            waiting.clear()
        }

        @Synchronized
        override fun sendAudio(pcm: ByteArray) = queue(pcm.toByteString())

        @Synchronized
        override fun send(message: ClientMessage) = queue(encode(message))

        override fun close() {
            socket.close(1000, null)
        }

        private fun queue(item: Any) {
            if (started) write(item) else waiting += item
        }

        private fun write(item: Any) {
            when (item) {
                is String -> socket.send(item)
                is ByteString -> socket.send(item)
            }
        }
    }

    /** A connection that was never opened: it only says why. */
    private class Refused(override val incoming: Channel<Incoming>) : BackendConnection {
        init {
            incoming.trySend(Incoming.Closed(failure = true, reason = Incoming.UNAUTHORIZED))
            incoming.close()
        }

        override fun start(start: Start) = Unit

        override fun sendAudio(pcm: ByteArray) = Unit

        override fun send(message: ClientMessage) = Unit

        override fun close() = Unit
    }
}
