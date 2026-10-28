package com.victorpolitakis.pesto.accessibility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.dictation.FieldRules
import com.victorpolitakis.pesto.insert.InsertMethod
import com.victorpolitakis.pesto.insert.Inserter
import com.victorpolitakis.pesto.insert.TextJoiner
import com.victorpolitakis.pesto.util.PestoLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Three methods, in order (Δ13):
 * 1. commitText through the service's input connection: inserts at the cursor
 *    like a keyboard, keeps formatting and undo (worked in the spike).
 * 2. ACTION_SET_TEXT with the whole new text, then ACTION_SET_SELECTION. Built
 *    from the text read before method 1, so the text is never inserted twice.
 * 3. The clipboard and a toast. The user's previous clip is lost: the service
 *    may write the clipboard but not read it.
 */
class AccessibilityInserter @Inject constructor(
    private val bridge: EditorBridge,
    @param:ApplicationContext private val context: Context,
    private val log: PestoLog,
) : Inserter {
    override suspend fun insert(text: String): InsertMethod {
        val node = bridge.focusedNode()
        val editor = bridge.editorInfo
        // C7, checked again at insertion: the focus may have moved to a password field.
        val password = node == null || node.isPassword ||
            (editor != null && FieldRules.inputTypeName(editor.inputType) == "password")
        if (password) return clipboard(text, "no_field_or_password")

        val around = bridge.surroundingText(BEFORE, AFTER)
        if (around != null && around.selectionEnd >= 0) {
            val all = around.text.toString()
            val end = around.selectionEnd.coerceIn(0, all.length)
            val piece = TextJoiner.join(all.substring(0, end), all.substring(end), text)
            if (piece.isEmpty()) return InsertMethod.COMMIT_TEXT
            if (around.selectionStart != around.selectionEnd) {
                // Insert after the selection, without replacing it (replacing is X1).
                val at = around.offset + end
                bridge.service?.inputMethod?.currentInputConnection?.setSelection(at, at)
            }
            if (bridge.commitText(piece)) {
                delay(VERIFY_DELAY_MS)
                val check = bridge.surroundingText(piece.length + 20, 0)?.text?.toString()
                if (check != null && check.endsWith(piece)) return InsertMethod.COMMIT_TEXT
                log.event("insert commitText not verified, falling back")
            }
            if (setText(node!!, all.substring(0, end), piece, all.substring(end))) return InsertMethod.SET_TEXT
        } else {
            val current = if (node!!.isShowingHintText) "" else node.text?.toString().orEmpty()
            val end = node.textSelectionEnd.takeIf { it in 0..current.length } ?: current.length
            val piece = TextJoiner.join(current.substring(0, end), current.substring(end), text)
            if (setText(node, current.substring(0, end), piece, current.substring(end))) return InsertMethod.SET_TEXT
        }
        return clipboard(text, "insertion_failed")
    }

    private fun setText(node: AccessibilityNodeInfo, before: String, piece: String, after: String): Boolean {
        val updated = before + piece + after
        val ok = node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated) },
        )
        if (!ok) return false
        val cursor = before.length + piece.length
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            },
        )
        node.refresh()
        return node.text?.toString() == updated
    }

    private fun clipboard(text: String, reason: String): InsertMethod {
        log.event("insert clipboard reason=$reason")
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pesto", text))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        return InsertMethod.CLIPBOARD
    }

    private companion object {
        const val BEFORE = 4000
        const val AFTER = 4000
        const val VERIFY_DELAY_MS = 60L
    }
}
