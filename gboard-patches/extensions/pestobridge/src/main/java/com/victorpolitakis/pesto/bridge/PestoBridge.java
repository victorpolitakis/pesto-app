package com.victorpolitakis.pesto.bridge;

import android.content.Context;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * Entry points the "Gboard with Pesto" patches call into. The patches add only tiny calls to these
 * static methods; all the behaviour and every Gboard-specific reflection lives here, so the patches
 * stay small and independent of obfuscated names (RESEARCH §9, §11).
 *
 * <ul>
 *   <li>{@link #register(Object)} — called at the start of the keyboard's
 *       {@code InputMethodService.onCreate}; keeps the service to commit text through.</li>
 *   <li>{@link #onVoiceKey(Object, Object)} — called at the start of each mic-key handler; starts
 *       Pesto dictation for the mic key and returns whether the handler should consume the event.</li>
 *   <li>{@link #seedLanguages(Context)} — called from the application's {@code attachBaseContext};
 *       seeds the keyboard's languages on first start.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class PestoBridge {
    private static final String TAG = "PestoBridge";

    private static WeakReference<Object> imeServiceRef = new WeakReference<>(null);
    private static MicSession activeSession;

    private PestoBridge() {}

    /** Keeps a reference to the keyboard's {@code InputMethodService} for committing text. */
    public static synchronized void register(Object imeService) {
        imeServiceRef = new WeakReference<>(imeService);
    }

    /**
     * Called at the start of a mic-key handler. For the microphone key with no data it starts a
     * Pesto dictation and returns {@code true} so the handler consumes the event; otherwise returns
     * {@code false} so the handler runs unchanged (keeping Gboard's own voice input as fallback).
     *
     * @param handler the voice-input handler, used to find its {@link Context}
     * @param event   the key event object
     */
    public static synchronized boolean onVoiceKey(Object handler, Object event) {
        try {
            if (!GboardReflection.isMicKeyEvent(event)) return false;

            Context context = GboardReflection.findContext(handler);
            if (context == null) {
                Log.w(TAG, "no context on voice handler; falling back to Gboard voice");
                return false;
            }
            Object imeService = imeServiceRef.get();
            if (imeService == null) {
                Log.w(TAG, "keyboard service not registered; falling back to Gboard voice");
                return false;
            }

            // Cancel any previous session before starting a new one.
            if (activeSession != null) {
                activeSession.cancel();
                activeSession = null;
            }
            MicSession session = new MicSession(context, imeService);
            if (!session.start()) {
                // No panel could be shown: start nothing, let Gboard's own voice input run.
                return false;
            }
            activeSession = session;
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "onVoiceKey failed; falling back to Gboard voice", e);
            return false;
        }
    }

    /** Cancels the current dictation, if any; a late transcript is discarded. */
    public static synchronized void cancelDictation() {
        if (activeSession != null) {
            activeSession.cancel();
            activeSession = null;
        }
    }

    /** Seeds the keyboard's languages from the bundled asset, once. */
    public static void seedLanguages(Context context) {
        LocaleSeeder.seedOnce(context);
    }
}
