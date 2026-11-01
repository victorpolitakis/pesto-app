package com.victorpolitakis.pesto.keyboard

/**
 * The Messenger contract with the patched keyboard ("Gboard with Pesto"), Pesto's side. The other
 * side is `DictationProtocol` of the pestobridge extension in gboard-patches; the two must agree,
 * which `KeyboardProtocolTest` checks against that file.
 *
 * The keyboard sends [MSG_START] with the read end of a PCM pipe, the field and the text around
 * the cursor, streams the microphone through the pipe and closes it when the user is done. Pesto
 * answers with [MSG_PARTIAL] and [MSG_LEVEL], then exactly one [MSG_FINAL] or [MSG_ERROR].
 */
object KeyboardProtocol {
    // Keyboard -> Pesto.
    const val MSG_START = 1
    const val MSG_CANCEL = 2

    // Pesto -> keyboard.
    const val MSG_PARTIAL = 10
    const val MSG_LEVEL = 11

    /** [KEY_TEXT]: the exact piece to commit at the cursor, already joined (TextJoiner). May be empty. */
    const val MSG_FINAL = 12

    /** [KEY_REASON]: why the dictation ended without text. */
    const val MSG_ERROR = 13

    // MSG_START data.
    const val KEY_PCM_FD = "pcm_fd"
    const val KEY_PACKAGE = "package"
    const val KEY_INPUT_TYPE = "input_type"
    const val KEY_IME_OPTIONS = "ime_options"
    const val KEY_HINT = "hint"
    const val KEY_TEXT_BEFORE = "text_before"
    const val KEY_TEXT_AFTER = "text_after"

    // Reply data.
    const val KEY_TEXT = "text"
    const val KEY_LEVEL = "level"
    const val KEY_REASON = "reason"

    /** How much text around the cursor the keyboard sends, and the most Pesto keeps of it. */
    const val CONTEXT_CHARS = 200

    /** The service, bound by the keyboard by explicit intent. */
    const val SERVICE = "com.victorpolitakis.pesto.keyboard.PestoDictationService"

    /** Reasons of [MSG_ERROR] that are Pesto's own; the others are those of `Outcome.Failed`. */
    const val REASON_PASSWORD = "password"
    const val REASON_BUSY = "busy"
    const val REASON_CANCELLED = "cancelled"
    const val REASON_BAD_REQUEST = "bad_request"
}
