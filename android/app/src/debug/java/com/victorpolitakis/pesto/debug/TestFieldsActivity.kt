package com.victorpolitakis.pesto.debug

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.victorpolitakis.pesto.data.PairingRepository
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.dictation.DictationController
import com.victorpolitakis.pesto.dictation.DictationUi
import com.victorpolitakis.pesto.dictation.Trigger
import com.victorpolitakis.pesto.overlay.OverlayMethod
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug build only. Fixed test fields (six) for the insertion scenarios and
 * the smoke test, the controls to dictate without the overlay, and a switch
 * of the overlay method, which writes the same setting as Settings (Δ12).
 */
@AndroidEntryPoint
class TestFieldsActivity : ComponentActivity() {
    @Inject lateinit var controller: DictationController
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var pairing: PairingRepository
    private lateinit var first: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { tag = "status" }
        val method = Button(this)
        method.setOnClickListener {
            val next = if (settings.current() == OverlayMethod.ACCESSIBILITY) OverlayMethod.APPLICATION else OverlayMethod.ACCESSIBILITY
            lifecycleScope.launch { settings.setOverlayMethod(next) }
        }
        lifecycleScope.launch {
            settings.settings.collect { method.text = "Overlay: ${it.overlayMethod}" }
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        fun field(hint: String, type: Int, ime: Int = 0) = EditText(this).apply {
            this.hint = hint
            inputType = type
            imeOptions = ime
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(method)
            addView(
                LinearLayout(context).apply {
                    addView(button("Start") { controller.start(Trigger.TEST, null) })
                    addView(button("⏸/▶") { if (controller.ui.value.isPaused()) controller.resume() else controller.pause() })
                    addView(button("✓") { controller.finish() })
                    addView(button("✕") { controller.cancel() })
                },
            )
            addView(status)
            first = field("Απλό", InputType.TYPE_CLASS_TEXT)
            addView(first)
            addView(field("Πολλών γραμμών", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
            addView(field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
            addView(field("Κωδικός", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
            addView(field("Αριθμός", InputType.TYPE_CLASS_NUMBER))
            addView(field("Incognito", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        lifecycleScope.launch {
            controller.ui.collect { status.text = describe(it) }
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /**
     * For tools/smoke.sh, without uiautomator (it suspends accessibility services):
     * `am start ... --es do focus` focuses the first field and opens the keyboard,
     * `--es do finish` is the ✓ of the panel, `--es do pair --es code 12345678`
     * pairs with the code of `pesto-server pair` (the log says the result, not the code).
     */
    private fun handle(intent: Intent) {
        when (intent.getStringExtra("do")) {
            "focus" -> first.post {
                first.requestFocus()
                getSystemService(InputMethodManager::class.java).showSoftInput(first, 0)
            }
            "finish" -> controller.finish()
            "pair" -> {
                val code = intent.getStringExtra("code").orEmpty()
                lifecycleScope.launch { pairing.pair(code) }
            }
        }
    }

    private fun DictationUi.isPaused() = this is DictationUi.Active && live.phase.name == "PAUSED"

    // The debug screen may show the text: it never goes to the log.
    private fun describe(ui: DictationUi) = when (ui) {
        DictationUi.Idle -> "idle"
        is DictationUi.Active -> "${ui.live.phase} level=${"%.2f".format(ui.live.level)}\n${ui.live.text}"
        is DictationUi.Ended -> "ended: ${ui.outcome}"
    }
}
