package com.victorpolitakis.pesto.backend

import com.victorpolitakis.pesto.protocol.ClientMessage
import com.victorpolitakis.pesto.protocol.ServerMessage
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.protocol.decode
import com.victorpolitakis.pesto.protocol.encode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import javax.inject.Inject
import javax.inject.Singleton

/** What arrives from the backend: a message, or the end of the connection. */
sealed interface Incoming {
    data class Message(val message: ServerMessage) : Incoming

    /** The connection ended. [failure] is true when it broke (network, refused, bad token). */
    data class Closed(val failure: Boolean, val reason: String) : Incoming
}

/** One connection = one dictation (docs/protocol.md). */
interface BackendConnection {
    val incoming: ReceiveChannel<Incoming>

    fun sendAudio(pcm: ByteArray)

    fun send(message: ClientMessage)

    fun close()
}

interface BackendClient {
    /** Opens the WebSocket and sends [start] first. Audio may be sent at once: it is queued. */
    fun open(start: Start): BackendConnection
}

data class BackendConfig(val url: String, val token: String)

@Singleton
class OkHttpBackendClient @Inject constructor(
    private val http: OkHttpClient,
    private val config: BackendConfig,
) : BackendClient {
    override fun open(start: Start): BackendConnection {
        val incoming = Channel<Incoming>(Channel.UNLIMITED)
        val request = Request.Builder()
            .url(config.url)
            .header("Authorization", "Bearer ${config.token}")
            .build()
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { decode(text) }.getOrNull() ?: return
                incoming.trySend(Incoming.Message(message))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                // 1008 = wrong token (docs/protocol.md)
                incoming.trySend(Incoming.Closed(failure = code == 1008, reason = "closed:$code"))
                incoming.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                incoming.trySend(Incoming.Closed(failure = true, reason = t.javaClass.simpleName))
                incoming.close()
            }
        })
        // OkHttp queues messages until the socket opens, so start is always first.
        socket.send(encode(start))
        return object : BackendConnection {
            override val incoming: ReceiveChannel<Incoming> = incoming

            override fun sendAudio(pcm: ByteArray) {
                socket.send(pcm.toByteString())
            }

            override fun send(message: ClientMessage) {
                socket.send(encode(message))
            }

            override fun close() {
                socket.close(1000, null)
            }
        }
    }
}
