package com.victorpolitakis.pesto.bridge;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads the few Gboard internals the microphone hook needs through reflection, so the patch itself
 * does not depend on the obfuscated field and class names that change between Gboard builds
 * (RESEARCH §11). Everything here fails safe: a lookup that does not find what it expects returns a
 * neutral value so the keyboard falls back to its own behaviour.
 */
final class GboardReflection {
    private static final String TAG = "PestoBridge";

    /** The microphone key event code, {@code LAUNCH_VOICE_IME} (RESEARCH §1). */
    static final int LAUNCH_VOICE_IME = -10042;

    private GboardReflection() {}

    /**
     * Whether a key event object is the microphone key with no data: some {@code int} field equals
     * {@link #LAUNCH_VOICE_IME} and some object-typed field is null (RESEARCH §1, §11).
     */
    static boolean isMicKeyEvent(Object event) {
        if (event == null) return false;
        boolean keycodeMatches = false;
        boolean nullData = false;
        for (Class<?> c = event.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                Class<?> type = field.getType();
                try {
                    field.setAccessible(true);
                    if (type == int.class) {
                        if (field.getInt(event) == LAUNCH_VOICE_IME) keycodeMatches = true;
                    } else if (!type.isPrimitive()) {
                        if (field.get(event) == null) nullData = true;
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // Ignore a field we cannot read and keep scanning.
                }
            }
        }
        return keycodeMatches && nullData;
    }

    /** The first {@link Context} field of a handler object, or null if none is found. */
    static Context findContext(Object handler) {
        if (handler == null) return null;
        for (Class<?> c = handler.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Context.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(handler);
                        if (value instanceof Context) return (Context) value;
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        // Try the next candidate field.
                    }
                }
            }
        }
        return null;
    }

    /**
     * The current {@link InputConnection} of the keyboard's {@code InputMethodService}, obtained
     * through its public {@code getCurrentInputConnection()} method. Null if unavailable.
     */
    static InputConnection currentInputConnection(Object imeService) {
        if (imeService == null) return null;
        try {
            Method method = imeService.getClass().getMethod("getCurrentInputConnection");
            Object ic = method.invoke(imeService);
            return (ic instanceof InputConnection) ? (InputConnection) ic : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "no input connection", e);
            return null;
        }
    }
}
