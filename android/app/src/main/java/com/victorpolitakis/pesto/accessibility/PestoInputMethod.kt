package com.victorpolitakis.pesto.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.view.inputmethod.EditorInfo

/**
 * The input method an accessibility service gets with flagInputMethodEditor
 * (API 33+). It runs next to Gboard, not instead of it, and is created by
 * AccessibilityService.onCreateInputMethod(): it is NOT an IME service and
 * must not be registered in the manifest.
 */
class PestoInputMethod(
    service: AccessibilityService,
    private val bridge: EditorBridge,
    private val onChange: () -> Unit,
) : InputMethod(service) {
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        bridge.editorInfo = attribute
        onChange()
    }

    override fun onFinishInput() {
        bridge.editorInfo = null
        super.onFinishInput()
        onChange()
    }
}
