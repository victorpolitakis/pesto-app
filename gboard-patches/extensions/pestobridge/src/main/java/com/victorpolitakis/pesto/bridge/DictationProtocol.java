package com.victorpolitakis.pesto.bridge;

/**
 * The Messenger contract between the patched keyboard and Pesto's bound {@code PestoDictationService}
 * (phase 2). The keyboard is the client; it sends {@link #MSG_START} with a read end of a PCM pipe
 * and the languages, then streams microphone audio through the pipe. The service replies with
 * {@link #MSG_PARTIAL}/{@link #MSG_LEVEL} updates and a final {@link #MSG_FINAL}, or acknowledges a
 * {@link #MSG_CANCEL}.
 *
 * <p>The values are a stable wire contract shared with the Pesto app, so they must not change
 * without updating both sides.
 */
public final class DictationProtocol {
    private DictationProtocol() {}

    /** The Pesto package and the dictation service's fully qualified name, bound by explicit intent. */
    public static final String PESTO_PACKAGE = "com.victorpolitakis.pesto";
    public static final String DICTATION_SERVICE =
            "com.victorpolitakis.pesto.dictation.PestoDictationService";

    // Client -> service.
    /** Start dictation. Carries the PCM pipe read end and the session parameters. */
    public static final int MSG_START = 1;
    /** Cancel the current dictation; any result that arrives afterwards is discarded. */
    public static final int MSG_CANCEL = 2;

    // Service -> client.
    /** A partial (in-progress) transcript. {@link #KEY_TEXT} holds the text. */
    public static final int MSG_PARTIAL = 10;
    /** An audio level update for the panel meter. {@link #KEY_LEVEL} holds a 0..1 float. */
    public static final int MSG_LEVEL = 11;
    /** The final transcript. {@link #KEY_TEXT} holds the text to commit. */
    public static final int MSG_FINAL = 12;

    // Message data keys.
    /** {@link android.os.ParcelFileDescriptor} read end of the 16 kHz mono PCM pipe (MSG_START). */
    public static final String KEY_PCM_FD = "pcm_fd";
    /** {@code String} language tags, semicolon separated, e.g. {@code en-GB;el} (MSG_START). */
    public static final String KEY_LANGUAGES = "languages";
    /** {@code String} dictation profile id (MSG_START). */
    public static final String KEY_PROFILE = "profile";
    /** {@code String} transcript (MSG_PARTIAL, MSG_FINAL). */
    public static final String KEY_TEXT = "text";
    /** {@code float} audio level 0..1 (MSG_LEVEL). */
    public static final String KEY_LEVEL = "level";

    /** 16 kHz mono 16-bit PCM, matching Pesto's dictation pipeline. */
    public static final int SAMPLE_RATE_HZ = 16000;
}
