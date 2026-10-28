package com.victorpolitakis.pesto.dictation

import com.victorpolitakis.pesto.audio.AudioCapture
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.backend.BackendConnection
import com.victorpolitakis.pesto.backend.Incoming
import com.victorpolitakis.pesto.protocol.Cancel
import com.victorpolitakis.pesto.protocol.Error
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Finalize
import com.victorpolitakis.pesto.protocol.Partial
import com.victorpolitakis.pesto.protocol.Pause
import com.victorpolitakis.pesto.protocol.Ready
import com.victorpolitakis.pesto.protocol.Resume
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.util.PestoLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { CONNECTING, LISTENING, PAUSED, FINISHING }

/** What the listening panel shows. [profile]: the id the start message asked for, once it is built (C9). */
data class LiveState(
    val phase: Phase = Phase.CONNECTING,
    val text: String = "",
    val level: Float = 0f,
    val profile: String? = null,
)

/** How a dictation ended. */
sealed interface Outcome {
    data class Done(val final: Final) : Outcome

    /** The connection broke mid-dictation: [lastText] is what the panel showed (Δ14). */
    data class ConnectionLost(val lastText: String) : Outcome

    data object Cancelled : Outcome

    /** E.g. `no_server`, `unauthorized` (not paired, Δ24), `mic_blocked`, `mic_error`, or a backend error code. */
    data class Failed(val reason: String) : Outcome
}

/**
 * One dictation: microphone → backend → outcome. Knows nothing about who
 * started it (button today, tile or gesture later) or about the overlay.
 * Recording and the connection start at once. The [start] message follows as
 * soon as it is built (StartBuilder): audio before it waits in the connection,
 * audio before `ready` in the backend.
 */
class DictationSession(
    private val start: Deferred<Start>,
    private val audio: AudioCapture,
    private val backend: BackendClient,
    private val scope: CoroutineScope,
    private val log: PestoLog,
    private val clock: () -> Long,
) {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()
    val outcome = CompletableDeferred<Outcome>()

    private var connection: BackendConnection? = null
    private var audioJob: Job? = null
    private var startedAt = 0L
    private var finalizedAt: Long? = null
    private var ready = false

    fun start() {
        startedAt = clock()
        val conn = backend.open()
        connection = conn
        startAudio()
        scope.launch { sendStart(conn) }
        scope.launch { receive(conn) }
    }

    private suspend fun sendStart(conn: BackendConnection) {
        val s = start.await()
        if (outcome.isCompleted) return
        log.perf("tap_to_start", clock() - startedAt)
        _state.update { it.copy(profile = s.profile) }
        conn.start(s)
    }

    fun pause() {
        val phase = _state.value.phase
        if (outcome.isCompleted || (phase != Phase.LISTENING && phase != Phase.CONNECTING)) return
        audioJob?.cancel()
        connection?.send(Pause)
        _state.update { it.copy(phase = Phase.PAUSED, level = 0f) }
    }

    fun resume() {
        if (outcome.isCompleted || _state.value.phase != Phase.PAUSED) return
        connection?.send(Resume)
        _state.update { it.copy(phase = if (ready) Phase.LISTENING else Phase.CONNECTING) }
        startAudio()
    }

    /** ✓: no more audio, the backend sends one `final`. */
    fun finish() {
        if (outcome.isCompleted || _state.value.phase == Phase.FINISHING) return
        audioJob?.cancel()
        finalizedAt = clock()
        connection?.send(Finalize)
        _state.update { it.copy(phase = Phase.FINISHING, level = 0f) }
    }

    /** ✕: nothing comes back, nothing is recorded. */
    fun cancel() {
        if (outcome.isCompleted) return
        connection?.send(Cancel)
        end(Outcome.Cancelled)
    }

    private fun startAudio() {
        var first = true
        audioJob = scope.launch {
            try {
                audio.frames().collect { frame ->
                    if (first) {
                        first = false
                        log.perf("tap_to_mic", clock() - startedAt)
                    }
                    if (frame.silenced) {
                        end(Outcome.Failed("mic_blocked"))
                        return@collect
                    }
                    connection?.sendAudio(frame.pcm)
                    _state.update { it.copy(level = frame.level) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.event("audio failed: ${e.javaClass.simpleName}")
                end(Outcome.Failed("mic_error"))
            }
        }
    }

    private suspend fun receive(conn: BackendConnection) {
        var closedReason: String? = null
        for (item in conn.incoming) {
            when (item) {
                is Incoming.Message -> when (val m = item.message) {
                    Ready -> {
                        ready = true
                        log.perf("tap_to_ready", clock() - startedAt)
                        _state.update { if (it.phase == Phase.CONNECTING) it.copy(phase = Phase.LISTENING) else it }
                    }
                    is Partial -> _state.update { it.copy(text = m.finalText + m.pendingText) }
                    is Final -> {
                        finalizedAt?.let { log.perf("finalize_to_final", clock() - it) }
                        end(Outcome.Done(m))
                    }
                    // `stt` is followed by a final with what was written; a mid-dictation
                    // `bad_request` lets the dictation continue (docs/protocol.md).
                    is Error -> {
                        log.event("backend error code=${m.code}")
                        if (m.code != "stt" && m.code != "bad_request") end(Outcome.Failed(m.code))
                    }
                }
                is Incoming.Closed -> {
                    log.event("connection closed failure=${item.failure} reason=${item.reason}")
                    closedReason = item.reason
                    break
                }
            }
        }
        if (!outcome.isCompleted) {
            val text = _state.value.text
            end(
                when {
                    !ready && closedReason == Incoming.UNAUTHORIZED -> Outcome.Failed(Incoming.UNAUTHORIZED)
                    !ready -> Outcome.Failed("no_server")
                    text.isNotBlank() -> Outcome.ConnectionLost(text)
                    else -> Outcome.Failed("connection_lost")
                },
            )
        }
    }

    private fun end(result: Outcome) {
        if (!outcome.complete(result)) return
        audioJob?.cancel()
        connection?.close()
    }
}
