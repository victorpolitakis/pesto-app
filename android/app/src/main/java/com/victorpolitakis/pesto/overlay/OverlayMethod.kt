package com.victorpolitakis.pesto.overlay

/**
 * The two overlay methods compared in phase 2 (Δ16):
 * [ACCESSIBILITY] = TYPE_ACCESSIBILITY_OVERLAY (no extra permission), the main one,
 * [APPLICATION] = TYPE_APPLICATION_OVERLAY (needs "display over other apps", and
 * Android shows a notification for as long as it is chosen), experimental.
 * Chosen in Settings, in both build types (Δ12).
 */
enum class OverlayMethod { ACCESSIBILITY, APPLICATION }

fun interface OverlayMethodProvider {
    fun current(): OverlayMethod
}
