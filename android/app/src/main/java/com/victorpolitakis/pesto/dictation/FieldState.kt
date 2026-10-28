package com.victorpolitakis.pesto.dictation

/** Screen rectangle in pixels, without Android types so the rules stay testable on the JVM. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
}

/** Plain data about the focused field, from its EditorInfo and AccessibilityNodeInfo. */
data class FieldState(
    val packageName: String,
    val inputType: Int,
    val imeOptions: Int,
    val hint: String?,
    val isPassword: Boolean,
    val isMultiLine: Boolean,
)
