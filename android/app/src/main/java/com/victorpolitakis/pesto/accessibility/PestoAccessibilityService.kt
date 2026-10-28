package com.victorpolitakis.pesto.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.victorpolitakis.pesto.dictation.ButtonDecision
import com.victorpolitakis.pesto.dictation.DictationController
import com.victorpolitakis.pesto.dictation.FieldRules
import com.victorpolitakis.pesto.dictation.Trigger
import com.victorpolitakis.pesto.overlay.OverlayController
import com.victorpolitakis.pesto.overlay.OverlayMethodProvider
import com.victorpolitakis.pesto.util.PestoLog
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Watches which field has focus in any app and shows the mic button over the
 * keyboard's mic key. Light while idle: the dictation pieces are [Lazy] and
 * created on the first tap. Reads only the focused field, never other text.
 */
@AndroidEntryPoint
class PestoAccessibilityService : AccessibilityService() {
    @Inject lateinit var bridge: EditorBridge
    @Inject lateinit var controller: Lazy<DictationController>
    @Inject lateinit var overlayMethod: OverlayMethodProvider
    @Inject lateinit var log: PestoLog

    private val main = Handler(Looper.getMainLooper())
    private val update = Runnable { update() }
    private var overlay: OverlayController? = null
    private var tracker: FocusTracker? = null
    private var snapshot: Snapshot? = null
    private var lastDecision: String? = null

    override fun onServiceConnected() {
        bridge.service = this
        tracker = FocusTracker(bridge)
        overlay = OverlayController(
            context = this,
            methods = overlayMethod,
            controller = { controller.get() },
            log = log,
            onButtonTap = ::onButtonTap,
            onPanelClosed = ::scheduleUpdate,
        )
        log.event("service connected")
        scheduleUpdate()
    }

    override fun onCreateInputMethod(): InputMethod = PestoInputMethod(this, bridge, ::scheduleUpdate)

    // Events from our own windows only reschedule an update that changes nothing,
    // so they are not filtered: filtering our package would also hide the test screen.
    override fun onAccessibilityEvent(event: AccessibilityEvent) = scheduleUpdate()

    override fun onInterrupt() = Unit

    private fun scheduleUpdate() {
        main.removeCallbacks(update)
        main.postDelayed(update, 80)
    }

    private fun update() {
        val overlay = overlay ?: return
        val tracker = tracker ?: return
        val s = tracker.snapshot(this)
        snapshot = s
        when (val d = FieldRules.button(s.field, s.keyboardVisible, s.micAnchor, overlay.panelVisible)) {
            is ButtonDecision.Show -> {
                val a = d.anchor
                logDecision("shown package=${s.field?.packageName} method=${overlayMethod.current()} anchor=${a.left},${a.top},${a.right},${a.bottom}")
                overlay.showButton(d.anchor)
            }
            is ButtonDecision.Hidden -> {
                logDecision("hidden reason=${d.reason} package=${s.field?.packageName} keyboard=${s.keyboardVisible}")
                overlay.hideButton()
            }
        }
    }

    /** One line per change, so that `adb logcat -s Pesto/Button` says why the button is (not) there. */
    private fun logDecision(line: String) {
        if (line != lastDecision) log.button(line)
        lastDecision = line
    }

    private fun onButtonTap() {
        val s = snapshot ?: return
        val anchor = s.micAnchor ?: return
        if (s.field != null && FieldRules.isPassword(s.field)) return // C7, checked again at the start
        if (controller.get().start(Trigger.BUTTON, s.field)) overlay?.showPanel(anchor)
    }

    override fun onDestroy() {
        // Without the service there is no panel: never keep the microphone open unseen.
        if (overlay?.panelVisible == true) controller.get().cancel()
        bridge.service = null
        overlay?.destroy()
        main.removeCallbacks(update)
        super.onDestroy()
    }
}
