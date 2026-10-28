package com.victorpolitakis.pesto.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.SurroundingText
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The system creates the accessibility service, so Hilt cannot hand it to
 * other classes. The service and its input method register here while they
 * are connected, and everyone else reads the field through this bridge.
 */
@Singleton
class EditorBridge @Inject constructor() {
    @Volatile var service: PestoAccessibilityService? = null
        internal set

    /** The EditorInfo of the last onStartInput, or null after onFinishInput. */
    @Volatile var editorInfo: EditorInfo? = null
        internal set

    val isConnected get() = service != null

    fun focusedNode(): AccessibilityNodeInfo? =
        service?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    /** Text around the cursor from the field itself, the reliable source (spike S4). */
    fun surroundingText(before: Int, after: Int): SurroundingText? =
        service?.inputMethod?.currentInputConnection?.getSurroundingText(before, after, 0)

    /** Inserts at the cursor like a keyboard. False when there is no input connection. */
    fun commitText(text: String): Boolean {
        val connection = service?.inputMethod?.currentInputConnection ?: return false
        connection.commitText(text, 1, null)
        return true
    }
}
