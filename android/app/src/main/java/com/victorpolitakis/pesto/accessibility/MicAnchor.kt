package com.victorpolitakis.pesto.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.victorpolitakis.pesto.dictation.Bounds

/**
 * Finds the keyboard's own mic key, which the Pesto button covers (C4).
 * On Gboard (spike, 28/10): id `key_pos_header_power_key`, description
 * "Use voice typing".
 * TODO(phase 6): the position of the button (Δ19). On the POCO (28/10) it at times does not cover
 * the mic key, comes late after a paste, or covers another spot: first the logs of Pesto/Button
 * (the bounds at every «shown») in those cases, with the POCO at hand.
 */
object MicAnchor {
    private val descriptions = listOf("voice typing", "voice input", "φωνητικ", "μικρόφων", "microphone")

    /** Pure: does this node look like a mic key? */
    fun isMicKey(description: String?, viewId: String?): Boolean {
        val d = description.orEmpty().lowercase()
        val id = viewId.orEmpty().lowercase()
        return descriptions.any { it in d } || "voice" in id
    }

    /** Bounds of the first visible mic key in the keyboard window, or null. */
    fun find(keyboardRoot: AccessibilityNodeInfo): Bounds? {
        val queue = ArrayDeque(listOf(keyboardRoot))
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 400) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && isMicKey(node.contentDescription?.toString(), node.viewIdResourceName)) {
                val r = Rect().also(node::getBoundsInScreen)
                return Bounds(r.left, r.top, r.right, r.bottom)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }
}
