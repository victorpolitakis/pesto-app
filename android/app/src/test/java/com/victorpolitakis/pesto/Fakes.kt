package com.victorpolitakis.pesto

import com.victorpolitakis.pesto.audio.AudioCapture
import com.victorpolitakis.pesto.audio.AudioFrame
import com.victorpolitakis.pesto.backend.BackendApi
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.backend.BackendConnection
import com.victorpolitakis.pesto.backend.Incoming
import com.victorpolitakis.pesto.backend.PairResult
import com.victorpolitakis.pesto.data.AppProfileDao
import com.victorpolitakis.pesto.data.AppProfileEntity
import com.victorpolitakis.pesto.data.DeviceTokenStore
import com.victorpolitakis.pesto.data.TermDao
import com.victorpolitakis.pesto.data.TermEntity
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

/** Keeps the order of the real connection: nothing goes out before start, what came earlier waits. */
class FakeBackendClient : BackendClient {
    /** Everything the phone sent after start, in order: client messages, or "audio" for each frame. */
    val sent = mutableListOf<Any>()

    /** The start message, once the phone sent it. */
    var start: Start? = null
    var closed = false
    private val waiting = mutableListOf<Any>()
    private val incoming = Channel<Incoming>(Channel.UNLIMITED)

    fun server(message: ServerMessage) {
        incoming.trySend(Incoming.Message(message))
    }

    fun drop(failure: Boolean = true, reason: String = "test") {
        incoming.trySend(Incoming.Closed(failure, reason))
    }

    private fun queue(item: Any) {
        if (start == null) waiting += item else sent += item
    }

    override fun open(): BackendConnection = object : BackendConnection {
        override val incoming: ReceiveChannel<Incoming> = this@FakeBackendClient.incoming

        override fun start(start: Start) {
            this@FakeBackendClient.start = start
            sent.addAll(waiting)
            waiting.clear()
        }

        override fun sendAudio(pcm: ByteArray) = queue("audio")

        override fun send(message: ClientMessage) = queue(message)

        override fun close() {
            closed = true
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

/** The terms table in memory, ordered like the real query. */
class FakeTermDao : TermDao {
    val rows = MutableStateFlow<List<TermEntity>>(emptyList())

    override fun all(): Flow<List<TermEntity>> = rows.map { list -> list.sortedBy { it.termKey } }

    override suspend fun list(): List<TermEntity> = rows.value

    override suspend fun insert(term: TermEntity): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1
        rows.value += term.copy(id = id)
        return id
    }

    override suspend fun update(term: TermEntity) {
        rows.value = rows.value.map { if (it.id == term.id) term else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

class FakeAppProfileDao : AppProfileDao {
    val rows = MutableStateFlow<List<AppProfileEntity>>(emptyList())

    override fun all(): Flow<List<AppProfileEntity>> = rows

    override suspend fun set(entry: AppProfileEntity) {
        rows.value = rows.value.filterNot { it.packageName == entry.packageName } + entry
    }

    override suspend fun remove(packageName: String) {
        rows.value = rows.value.filterNot { it.packageName == packageName }
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
