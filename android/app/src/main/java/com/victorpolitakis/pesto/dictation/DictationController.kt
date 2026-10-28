package com.victorpolitakis.pesto.dictation

import android.os.SystemClock
import com.victorpolitakis.pesto.audio.AudioCapture
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.di.AppScope
import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.util.PestoLog
import com.victorpolitakis.pesto.util.WallClock
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Who started a dictation. Today only the button (and the debug test screen). */
enum class Trigger { BUTTON, TEST }

/** What the overlay shows. */
sealed interface DictationUi {
    data object Idle : DictationUi

    data class Active(val live: LiveState) : DictationUi

    data class Ended(val outcome: Outcome) : DictationUi
}

/**
 * The only place a dictation starts, whatever the trigger, and the place that
 * runs the end of it (C7 check, TextJoiner, Inserter, History). Heavy pieces
 * are [Lazy], so the idle accessibility service does not create them.
 */
@Singleton
class DictationController @Inject constructor(
    private val audio: Lazy<AudioCapture>,
    private val backend: Lazy<BackendClient>,
    private val delivery: Lazy<Delivery>,
    private val clock: WallClock,
    private val log: PestoLog,
    @param:AppScope private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow<DictationUi>(DictationUi.Idle)
    val ui: StateFlow<DictationUi> = _ui.asStateFlow()

    private var session: DictationSession? = null
    private var watcher: Job? = null

    val isActive get() = session != null

    /** Starts a dictation for [field] (null = no target field). False if one is already running. */
    fun start(trigger: Trigger, field: FieldState?): Boolean {
        if (session != null) return false
        val context = field?.let(FieldRules::context) ?: FieldContext()
        val startedAt = clock.now()
        log.event("start trigger=$trigger package=${context.packageName} input_type=${context.inputType}")
        val start = Start(fieldContext = context)
        val s = DictationSession(start, audio.get(), backend.get(), scope, log, SystemClock::elapsedRealtime)
        session = s
        s.start()
        watcher = scope.launch {
            launch { s.state.collect { if (session === s) _ui.value = DictationUi.Active(it) } }
            val outcome = s.outcome.await()
            session = null
            log.event("end outcome=${outcome.javaClass.simpleName}")
            _ui.value = DictationUi.Ended(outcome)
            delivery.get().deliver(outcome, start, startedAt)
            watcher?.cancel()
        }
        return true
    }

    fun pause() = session?.pause()

    fun resume() = session?.resume()

    fun finish() = session?.finish()

    fun cancel() = session?.cancel()

    /** The overlay calls this once it has shown the end state. */
    fun clear() {
        if (session == null) _ui.value = DictationUi.Idle
    }
}

/** What happens with the outcome of a dictation: insertion and History. */
interface Delivery {
    /** [start]: what the dictation asked for (field, profile, terms). [startedAt]: the tap, wall clock. */
    suspend fun deliver(outcome: Outcome, start: Start, startedAt: Long)
}
