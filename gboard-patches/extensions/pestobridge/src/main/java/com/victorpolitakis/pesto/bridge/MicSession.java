package com.victorpolitakis.pesto.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.inputmethodservice.InputMethodService;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One microphone dictation: binds to Pesto's dictation service, streams 16 kHz PCM from the
 * microphone through a pipe, and commits the transcript the service returns into the keyboard's
 * input connection.
 *
 * <p>Only one session runs at a time (see {@link PestoBridge}). A cancelled session discards any
 * transcript that arrives afterwards — the bug the S6 spike called out (RESEARCH §9).
 */
final class MicSession {
    private static final String TAG = "PestoBridge";

    private final Context context;
    private final Object imeService;
    private final MicPanel panel;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    /** "Done" was pressed: stop the microphone, which closes the pipe and ends the audio. */
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private HandlerThread callbackThread;
    private Messenger incoming;
    private Messenger service;
    private ServiceConnection connection;
    private AudioRecord recorder;
    private Thread audioThread;
    private ParcelFileDescriptor writeEnd;

    MicSession(Context context, Object imeService) {
        this.context = context.getApplicationContext();
        this.imeService = imeService;
        this.panel = new MicPanel(imeService, new MicPanel.Listener() {
            @Override
            public void onDone() {
                stopSpeaking();
            }

            @Override
            public void onCancel() {
                cancel();
            }

            @Override
            public void onHidden() {
                Log.i(TAG, "keyboard hidden, cancel");
                cancel();
            }
        });
    }

    /**
     * Shows the panel and binds to the Pesto dictation service; audio starts once it is connected.
     *
     * @return {@code false} if the panel could not be shown, in which case nothing was started and
     *         the caller falls back to Gboard's own voice input
     */
    boolean start() {
        if (!panel.show()) return false;

        callbackThread = new HandlerThread("PestoMicCallback");
        callbackThread.start();
        incoming = new Messenger(new Handler(callbackThread.getLooper(), this::handleServiceMessage));

        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                service = new Messenger(binder);
                beginStreaming();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service = null;
                finish();
            }
        };

        Intent intent = new Intent();
        intent.setClassName(DictationProtocol.PESTO_PACKAGE, DictationProtocol.DICTATION_SERVICE);
        try {
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                Log.w(TAG, "could not bind Pesto dictation service");
                finish();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "not allowed to bind Pesto dictation service", e);
            finish();
        }
        return true;
    }

    /** "Done": stop the microphone. The pipe closes, the service sees the end and sends the result. */
    void stopSpeaking() {
        if (stopRequested.getAndSet(true)) return;
        panel.speakingEnded();
    }

    private void beginStreaming() {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readEnd = pipe[0];
            writeEnd = pipe[1];

            Message start = Message.obtain(null, DictationProtocol.MSG_START);
            start.replyTo = incoming;
            Bundle data = new Bundle();
            data.putParcelable(DictationProtocol.KEY_PCM_FD, readEnd);
            putField(data);
            start.setData(data);
            service.send(start);
            // The service owns the read end now.
            readEnd.close();

            startRecording(writeEnd);
        } catch (IOException | RemoteException | RuntimeException e) {
            Log.w(TAG, "could not start streaming", e);
            finish();
        }
    }

    /**
     * The field the keyboard types into, and the text around the cursor: Pesto picks the profile from
     * the package, keeps incognito fields out of its History, refuses password fields, and joins the
     * dictated text to the text around it. The text around the cursor stays on the phone.
     */
    private void putField(Bundle data) {
        if (imeService instanceof InputMethodService) {
            EditorInfo editor = ((InputMethodService) imeService).getCurrentInputEditorInfo();
            if (editor != null) {
                data.putString(DictationProtocol.KEY_PACKAGE, editor.packageName);
                data.putInt(DictationProtocol.KEY_INPUT_TYPE, editor.inputType);
                data.putInt(DictationProtocol.KEY_IME_OPTIONS, editor.imeOptions);
                if (editor.hintText != null) {
                    data.putString(DictationProtocol.KEY_HINT, editor.hintText.toString());
                }
            }
        }
        InputConnection ic = GboardReflection.currentInputConnection(imeService);
        if (ic != null) {
            CharSequence before = ic.getTextBeforeCursor(DictationProtocol.CONTEXT_CHARS, 0);
            CharSequence after = ic.getTextAfterCursor(DictationProtocol.CONTEXT_CHARS, 0);
            data.putString(DictationProtocol.KEY_TEXT_BEFORE, before == null ? "" : before.toString());
            data.putString(DictationProtocol.KEY_TEXT_AFTER, after == null ? "" : after.toString());
        }
    }

    private void startRecording(ParcelFileDescriptor pcmSink) {
        int minBuffer = AudioRecord.getMinBufferSize(
                DictationProtocol.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) minBuffer = DictationProtocol.SAMPLE_RATE_HZ; // fallback buffer

        try {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    DictationProtocol.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "could not create AudioRecord", e);
            finish();
            return;
        }
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord not initialized (microphone permission?)");
            finish();
            return;
        }

        final int bufferSize = minBuffer;
        audioThread = new Thread(() -> pump(pcmSink, bufferSize), "PestoMicPump");
        audioThread.start();
    }

    private void pump(ParcelFileDescriptor pcmSink, int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pcmSink)) {
            recorder.startRecording();
            while (!cancelled.get() && !finished.get() && !stopRequested.get()) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    out.write(buffer, 0, read);
                } else if (read < 0) {
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            // Closing the pipe or stopping mid-read ends the loop; nothing more to do.
        } finally {
            stopRecorder();
        }
    }

    private boolean handleServiceMessage(Message message) {
        switch (message.what) {
            case DictationProtocol.MSG_PARTIAL:
                // Partial transcripts drive the panel (phase 6 design); not committed.
                return true;
            case DictationProtocol.MSG_LEVEL:
                // Audio level for the panel meter; no UI yet.
                return true;
            case DictationProtocol.MSG_FINAL:
                Bundle data = message.getData();
                String text = data == null ? null : data.getString(DictationProtocol.KEY_TEXT);
                // Commit on the main thread, like the keyboard's own input; the cancel check runs
                // there too, so a cancel that lands first still discards the text.
                main.post(() -> commitIfLive(text));
                finish();
                return true;
            case DictationProtocol.MSG_ERROR:
                Bundle error = message.getData();
                // TODO(design-panel): show the reason in the panel before it closes.
                Log.i(TAG, "dictation ended without text: "
                        + (error == null ? null : error.getString(DictationProtocol.KEY_REASON)));
                finish();
                return true;
            default:
                return false;
        }
    }

    /** Commits the transcript, unless the session was cancelled, in which case it is discarded. */
    private void commitIfLive(String text) {
        // The text is already joined to the text around the cursor by Pesto: commit it as is.
        if (text == null || text.isEmpty() || cancelled.get()) return;
        InputConnection ic = GboardReflection.currentInputConnection(imeService);
        if (ic != null) {
            ic.commitText(text, 1);
        } else {
            Log.w(TAG, "no input connection to commit into");
        }
    }

    /** Cancels the session; a transcript that arrives afterwards is discarded. */
    void cancel() {
        // A finished session has nothing to cancel: its result is already on the way to the field,
        // and the service must not get a stray MSG_CANCEL (the panel's removal can report "hidden").
        if (finished.get()) return;
        if (cancelled.getAndSet(true)) return;
        if (service != null) {
            try {
                Message cancel = Message.obtain(null, DictationProtocol.MSG_CANCEL);
                service.send(cancel);
            } catch (RemoteException e) {
                // The service is gone; cleanup below still runs.
            }
        }
        finish();
    }

    private void stopRecorder() {
        AudioRecord r = recorder;
        if (r != null) {
            try {
                if (r.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) r.stop();
            } catch (IllegalStateException ignored) {
                // Already stopped.
            }
            r.release();
            recorder = null;
        }
    }

    /** Stops audio, unbinds and releases resources. Safe to call more than once. */
    void finish() {
        if (finished.getAndSet(true)) return;
        panel.hide();
        stopRecorder();
        // Close the pipe, so the service never waits for audio that will not come (e.g. when the
        // microphone failed before recording began). Closing it twice is harmless.
        closeQuietly(writeEnd);
        if (connection != null) {
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // Not bound.
            }
            connection = null;
        }
        if (callbackThread != null) {
            callbackThread.quitSafely();
            callbackThread = null;
        }
    }

    private static void closeQuietly(ParcelFileDescriptor fd) {
        if (fd == null) return;
        try {
            fd.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }
}
