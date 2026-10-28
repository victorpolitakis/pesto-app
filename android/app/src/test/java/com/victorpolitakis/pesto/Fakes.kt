package com.victorpolitakis.pesto

import com.victorpolitakis.pesto.audio.AudioCapture
import com.victorpolitakis.pesto.audio.AudioFrame
import com.victorpolitakis.pesto.backend.BackendApi
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.backend.BackendConnection
import com.victorpolitakis.pesto.backend.Incoming
import com.victorpolitakis.pesto.backend.PairResult
import com.victorpolitakis.pesto.data.DeviceTokenStore
import com.victorpolitakis.pesto.protocol.ClientMessage
import com.victorpolitakis.pesto.protocol.ServerMessage
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.util.PestoLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Fakes for JVM tests, like backend/tests/fakes.py. */

class FakeAudioCapture : AudioCapture {
    val frames = Channel<AudioFrame>(Channel.UNLIMITED)
    var opened = 0
    var open = false

    fun speak(level: Float = 0.5f, silenced: Boolean = false) {
        frames.trySend(AudioFrame(ByteArray(3200), level, silenced))
    }

    override fun frames(): Flow<AudioFrame> = flow {
        opened++
        open = true
        try {
            for (frame in frames) emit(frame)
        } finally {
            open = false
        }
    }
}

class FakeBackendClient : BackendClient {
    /** Everything the phone sent after start: client messages, or "audio" for each frame. */
    val sent = mutableListOf<Any>()
    var start: Start? = null
    var closed = false
    private val incoming = Channel<Incoming>(Channel.UNLIMITED)

    fun server(message: ServerMessage) {
        incoming.trySend(Incoming.Message(message))
    }

    fun drop(failure: Boolean = true, reason: String = "test") {
        incoming.trySend(Incoming.Closed(failure, reason))
    }

    override fun open(start: Start): BackendConnection {
        this.start = start
        return object : BackendConnection {
            override val incoming: ReceiveChannel<Incoming> = this@FakeBackendClient.incoming

            override fun sendAudio(pcm: ByteArray) {
                sent += "audio"
            }

            override fun send(message: ClientMessage) {
                sent += message
            }

            override fun close() {
                closed = true
            }
        }
    }
}

/** The device token in memory only (the real one is in the Android Keystore). */
class FakeDeviceTokenStore(token: String? = "device-token") : DeviceTokenStore {
    private val token = MutableStateFlow(token)

    override fun current(): String? = token.value

    override suspend fun isPaired(): Boolean = token.value != null

    override val paired: Flow<Boolean> = this.token.map { it != null }

    override suspend fun save(token: String) {
        this.token.value = token
    }

    override suspend fun clear() {
        token.value = null
    }
}

class FakeBackendApi(var result: PairResult = PairResult.Paired("new-token"), var answers: Boolean = true) : BackendApi {
    /** Code and device name of every pairing. */
    val pairs = mutableListOf<Pair<String, String>>()

    override suspend fun health(): Boolean = answers

    override suspend fun pair(code: String, deviceName: String): PairResult {
        pairs += code to deviceName
        return result
    }
}

class FakeLog : PestoLog {
    val lines = mutableListOf<String>()

    override fun button(message: String) {
        lines += "button $message"
    }

    override fun perf(name: String, ms: Long) {
        lines += "perf $name"
    }

    override fun event(message: String) {
        lines += "event $message"
    }
}
