package com.victorpolitakis.pesto.bridge;

/**
 * The Messenger contract between the patched keyboard and Pesto's bound {@code PestoDictationService}
 * (phase 2). The keyboard is the client: it sends {@link #MSG_START} with the read end of a PCM pipe,
 * the field it types into and the text around the cursor, then streams microphone audio through the
 * pipe and closes it when the user is done. The service replies with {@link #MSG_PARTIAL} and
 * {@link #MSG_LEVEL} updates, then exactly one {@link #MSG_FINAL} or {@link #MSG_ERROR}.
 *
 * <p>Pesto picks the languages (its Settings) and the profile (per app, from the field's package),
 * as for every other dictation, so the keyboard sends neither.
 *
 * <p>The values are a wire contract with the Pesto app ({@code keyboard/KeyboardProtocol.kt}); a
 * test in the app checks that the two sides agree.
 */
public final class DictationProtocol {
    private DictationProtocol() {}

    /** The Pesto package and the dictation service's fully qualified name, bound by explicit intent. */
    public static final String PESTO_PACKAGE = "com.victorpolitakis.pesto";
    public static final String DICTATION_SERVICE =
            "com.victorpolitakis.pesto.keyboard.PestoDictationService";

    // Client -> service.
    /** Start dictation. Carries the PCM pipe read end, the field and the text around the cursor. */
    public static final int MSG_START = 1;
    /** Cancel the current dictation; the service sends no final, and nothing is recorded. */
    public static final int MSG_CANCEL = 2;

    // Service -> client.
    /** A partial (in-progress) transcript, for the panel. {@link #KEY_TEXT} holds the text. */
    public static final int MSG_PARTIAL = 10;
    /** An audio level update for the panel meter. {@link #KEY_LEVEL} holds a 0..1 float. */
    public static final int MSG_LEVEL = 11;
    /**
     * The end: {@link #KEY_TEXT} is the exact piece to commit at the cursor, already joined to the
     * text around it (spaces, case). Empty when there is nothing to write.
     */
    public static final int MSG_FINAL = 12;
    /** The end, without text: {@link #KEY_REASON} says why (e.g. {@code no_server}, {@code password}). */
    public static final int MSG_ERROR = 13;

    // MSG_START data.
    /** {@link android.os.ParcelFileDescriptor}: read end of the 16 kHz mono pcm_s16le pipe. */
    public static final String KEY_PCM_FD = "pcm_fd";
    /** {@code String}: the package of the app the field belongs to ({@code EditorInfo.packageName}). */
    public static final String KEY_PACKAGE = "package";
    /** {@code int}: {@code EditorInfo.inputType}. */
    public static final String KEY_INPUT_TYPE = "input_type";
    /** {@code int}: {@code EditorInfo.imeOptions}, e.g. the no-personalized-learning flag. */
    public static final String KEY_IME_OPTIONS = "ime_options";
    /** {@code String}: {@code EditorInfo.hintText}, if any. */
    public static final String KEY_HINT = "hint";
    /** {@code String}: up to {@link #CONTEXT_CHARS} characters before the cursor. */
    public static final String KEY_TEXT_BEFORE = "text_before";
    /** {@code String}: up to {@link #CONTEXT_CHARS} characters after the cursor. */
    public static final String KEY_TEXT_AFTER = "text_after";

    // Reply data.
    /** {@code String}: transcript (MSG_PARTIAL) or the piece to commit (MSG_FINAL). */
    public static final String KEY_TEXT = "text";
    /** {@code float}: audio level 0..1 (MSG_LEVEL). */
    public static final String KEY_LEVEL = "level";
    /** {@code String}: why the dictation ended without text (MSG_ERROR). */
    public static final String KEY_REASON = "reason";

    /** How much text around the cursor the keyboard sends, enough to join the dictated text. */
    public static final int CONTEXT_CHARS = 200;

    /** 16 kHz mono 16-bit PCM, matching Pesto's dictation pipeline. */
    public static final int SAMPLE_RATE_HZ = 16000;
}
