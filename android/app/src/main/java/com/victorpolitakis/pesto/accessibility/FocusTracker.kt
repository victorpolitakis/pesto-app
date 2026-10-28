package com.victorpolitakis.pesto.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.victorpolitakis.pesto.dictation.Bounds
import com.victorpolitakis.pesto.dictation.FieldState

/** What the service sees right now: the editable field with focus, and the keyboard. */
data class Snapshot(val field: FieldState?, val keyboardVisible: Boolean, val micAnchor: Bounds?)

/** Reads the focused field and the keyboard from the service. Only the field with focus, never other text. */
class FocusTracker(private val bridge: EditorBridge) {
    fun snapshot(service: PestoAccessibilityService): Snapshot {
        val keyboard = service.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        // The keyboard window's own bounds were wrong on the emulator (spike): only the mic key's are used.
        val anchor = keyboard?.root?.let(MicAnchor::find)
        return Snapshot(field(bridge.focusedNode()), keyboard != null, anchor)
    }

    private fun field(node: AccessibilityNodeInfo?): FieldState? {
        node ?: return null
        val packageName = node.packageName?.toString() ?: return null
        // The EditorInfo is more complete (imeOptions, incognito), but only if it is the same field's app.
        val editor = bridge.editorInfo?.takeIf { it.packageName == packageName }
        return FieldState(
            packageName = packageName,
            inputType = editor?.inputType ?: node.inputType,
            imeOptions = editor?.imeOptions ?: 0,
            hint = (editor?.hintText ?: node.hintText)?.toString(),
            isPassword = node.isPassword,
            isMultiLine = node.isMultiLine,
        )
    }
}
