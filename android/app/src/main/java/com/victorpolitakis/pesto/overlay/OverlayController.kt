package com.victorpolitakis.pesto.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.backend.Incoming
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.dictation.Bounds
import com.victorpolitakis.pesto.dictation.DictationController
import com.victorpolitakis.pesto.dictation.DictationUi
import com.victorpolitakis.pesto.dictation.Outcome
import com.victorpolitakis.pesto.dictation.Phase
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import com.victorpolitakis.pesto.util.PestoLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What the panel shows, already turned into what Compose draws. [profile]: the name of the profile asked for (C9). */
private data class PanelState(
    @param:StringRes val status: Int = R.string.status_connecting,
    val text: String = "",
    val levels: List<Float> = emptyList(),
    val paused: Boolean = false,
    @param:StringRes val profile: Int? = null,
)

/**
 * Adds and removes the two overlay windows: the mic button over the keyboard's
 * mic key, and the listening panel over the keyboard. Both are not focusable,
 * so the app's field keeps the focus and Gboard stays open.
 */
class OverlayController(
    private val context: Context,
    private val methods: OverlayMethodProvider,
    private val controller: () -> DictationController,
    private val log: PestoLog,
    private val onButtonTap: () -> Unit,
    private val onPanelClosed: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val owners = OverlayOwners()
    private val scope = MainScope()

    private var button: View? = null
    private var buttonKey: Pair<Bounds, OverlayMethod>? = null
    private var panel: View? = null
    private var panelJob: Job? = null
    private val panelState = MutableStateFlow(PanelState())

    val panelVisible get() = panel != null

    fun showButton(anchor: Bounds) {
        val method = methods.current()
        if (button != null && buttonKey == anchor to method) return
        hideButton()
        val min = context.resources.getDimensionPixelSize(R.dimen.mic_button_min)
        val size = maxOf(anchor.width, anchor.height, min)
        val view = owners.composeView(context) { PestoTheme { MicButton(onTap = onButtonTap) } }
        if (add(view, method, anchor.centerX - size / 2, anchor.centerY - size / 2, size, size)) {
            button = view
            buttonKey = anchor to method
        }
    }

    fun hideButton() {
        button?.let(::remove)
        button = null
        buttonKey = null
    }

    /** Covers the keyboard, from the top of its mic key row to the bottom of the screen. */
    fun showPanel(anchor: Bounds) {
        hideButton()
        if (panel != null) return
        val screen = wm.currentWindowMetrics.bounds
        val levels = ArrayDeque<Float>()
        panelState.value = PanelState()
        val view = owners.composeView(context) {
            PestoTheme {
                val state by panelState.collectAsState()
                val c = controller()
                ListeningPanel(
                    status = stringResource(state.status),
                    profile = state.profile?.let { stringResource(it) },
                    text = state.text,
                    levels = state.levels,
                    paused = state.paused,
                    onCancel = c::cancel,
                    onPauseResume = { if (state.paused) c.resume() else c.pause() },
                    onFinish = c::finish,
                )
            }
        }
        if (!add(view, methods.current(), 0, anchor.top, screen.width(), screen.height() - anchor.top)) {
            controller().cancel()
            return
        }
        panel = view
        panelJob = scope.launch {
            controller().ui.collect { ui ->
                when (ui) {
                    DictationUi.Idle -> Unit
                    is DictationUi.Active -> {
                        levels.addLast(ui.live.level)
                        while (levels.size > 32) levels.removeFirst()
                        panelState.value = PanelState(
                            statusOf(ui.live.phase),
                            ui.live.text,
                            levels.toList(),
                            ui.live.phase == Phase.PAUSED,
                            ProfilesCatalog.find(ui.live.profile)?.name,
                        )
                    }
                    is DictationUi.Ended -> {
                        val error = errorOf(ui.outcome)
                        if (error != null) {
                            panelState.value = panelState.value.copy(status = error)
                            delay(2000)
                        }
                        closePanel()
                    }
                }
            }
        }
    }

    private fun closePanel() {
        panel?.let(::remove)
        panel = null
        controller().clear()
        panelJob?.cancel()
        onPanelClosed()
    }

    fun destroy() {
        hideButton()
        panel?.let(::remove)
        panel = null
        scope.cancel()
    }

    private fun statusOf(phase: Phase) = when (phase) {
        Phase.CONNECTING -> R.string.status_connecting
        Phase.LISTENING -> R.string.status_listening
        Phase.PAUSED -> R.string.status_paused
        Phase.FINISHING -> R.string.status_finishing
    }

    private fun errorOf(outcome: Outcome): Int? = when {
        outcome !is Outcome.Failed -> null
        outcome.reason == "no_server" -> R.string.error_no_server
        outcome.reason == Incoming.UNAUTHORIZED -> R.string.error_unpaired
        outcome.reason.startsWith("mic_") -> R.string.error_mic_blocked
        else -> R.string.error_generic
    }

    private fun add(view: View, method: OverlayMethod, x: Int, y: Int, width: Int, height: Int): Boolean {
        val type = when (method) {
            OverlayMethod.ACCESSIBILITY -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            OverlayMethod.APPLICATION -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        val params = WindowManager.LayoutParams(
            width, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return try {
            wm.addView(view, params)
            true
        } catch (e: RuntimeException) {
            // e.g. method 2 without the overlay permission
            log.button("addView failed method=$method error=${e.javaClass.simpleName}")
            view.disposeOverlay()
            false
        }
    }

    private fun remove(view: View) {
        view.disposeOverlay()
        runCatching { wm.removeView(view) }
    }
}
