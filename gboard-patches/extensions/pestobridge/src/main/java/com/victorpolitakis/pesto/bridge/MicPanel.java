package com.victorpolitakis.pesto.bridge;

import android.app.Dialog;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The minimal dictation panel shown over the keys while Pesto listens, like the S6 spike's: a
 * "Listening…" status with "Done" and "Cancel".
 *
 * <p>It is added to the keyboard window's decor view, at the bottom, with the height of the
 * keyboard area: the decor height minus {@code onComputeInsets().visibleTopInsets}. Anything above
 * that line does not receive touches (they go to the app behind the keyboard), so the panel must
 * stay inside it (RESEARCH §9, "Περιοχή αφής").
 *
 * <p>TODO(design-panel): the design (wave, buttons, partials) is phase 6 of the project. The only
 * styling here is an opaque background and dark text, so the keys underneath neither show nor
 * receive taps.
 */
final class MicPanel {
    private static final String TAG = "PestoBridge";

    interface Listener {
        void onDone();

        void onCancel();

        /** The keyboard window was hidden (back, or the keyboard closed) while the panel showed. */
        void onHidden();
    }

    /**
     * The panel's root. It lives in the keyboard window, so a hidden keyboard (back, a tap outside,
     * the app closing the keyboard) reaches it as a window visibility change, with no extra hook in
     * Gboard. S6 cancelled the same way ("keyboard hidden, cancel", RESEARCH §9).
     */
    private static final class PanelLayout extends LinearLayout {
        private final Runnable onHidden;

        PanelLayout(android.content.Context context, Runnable onHidden) {
            super(context);
            this.onHidden = onHidden;
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility != View.VISIBLE) onHidden.run();
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object imeService;
    private final Listener listener;

    private View root;
    private Button done;

    MicPanel(Object imeService, Listener listener) {
        this.imeService = imeService;
        this.listener = listener;
    }

    /**
     * Shows the panel. On the main thread (where the mic key is handled) it returns whether the
     * panel was attached, so the caller can fall back to Gboard's own voice input when it was not.
     * From another thread it posts the attach and returns {@code true}.
     */
    boolean show() {
        if (Looper.myLooper() == Looper.getMainLooper()) return attach();
        main.post(this::attach);
        return true;
    }

    /** Called when the user pressed "Done": the audio has ended, the transcript is on its way. */
    void speakingEnded() {
        main.post(() -> {
            if (done != null) done.setEnabled(false);
        });
    }

    void hide() {
        main.post(this::detach);
    }

    private boolean attach() {
        if (root != null) return true;
        if (!(imeService instanceof InputMethodService)) {
            Log.w(TAG, "keyboard service is not an InputMethodService; no panel");
            return false;
        }
        InputMethodService ims = (InputMethodService) imeService;
        Dialog dialog = ims.getWindow();
        Window window = dialog == null ? null : dialog.getWindow();
        View decor = window == null ? null : window.getDecorView();
        if (!(decor instanceof FrameLayout)) {
            Log.w(TAG, "keyboard window has no FrameLayout decor; no panel");
            return false;
        }

        // The keyboard area: from visibleTopInsets to the bottom of the window (S6: 1597 / 2582).
        InputMethodService.Insets insets = new InputMethodService.Insets();
        ims.onComputeInsets(insets);
        int height = decor.getHeight() - insets.visibleTopInsets;
        if (height <= 0) {
            Log.w(TAG, "keyboard area not laid out (height " + height + "); no panel");
            return false;
        }

        LinearLayout panel = new PanelLayout(ims, listener::onHidden);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(Color.WHITE);
        // Swallow taps, so they never reach the keys underneath.
        panel.setClickable(true);

        TextView status = new TextView(ims);
        status.setText("Listening…");
        status.setTextColor(Color.BLACK);
        panel.addView(status);

        LinearLayout buttons = new LinearLayout(ims);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        done = new Button(ims);
        done.setText("Done");
        done.setOnClickListener(v -> listener.onDone());
        buttons.addView(done);

        Button cancel = new Button(ims);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> listener.onCancel());
        buttons.addView(cancel);

        panel.addView(buttons);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
        ((FrameLayout) decor).addView(panel, params);
        root = panel;
        return true;
    }

    private void detach() {
        View panel = root;
        if (panel == null) return;
        if (panel.getParent() instanceof ViewGroup) ((ViewGroup) panel.getParent()).removeView(panel);
        root = null;
        done = null;
    }
}
