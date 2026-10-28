package com.victorpolitakis.pesto.overlay

/**
 * The two overlay methods compared in phase 2 (Δ16):
 * [ACCESSIBILITY] = TYPE_ACCESSIBILITY_OVERLAY (no extra permission),
 * [APPLICATION] = TYPE_APPLICATION_OVERLAY (needs "display over other apps").
 * The debug build can switch between them; the release build uses a constant.
 */
enum class OverlayMethod { ACCESSIBILITY, APPLICATION }

fun interface OverlayMethodProvider {
    fun current(): OverlayMethod
}
