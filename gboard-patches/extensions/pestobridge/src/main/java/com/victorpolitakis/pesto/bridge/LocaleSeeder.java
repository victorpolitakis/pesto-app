package com.victorpolitakis.pesto.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seeds the patched keyboard's languages from {@code assets/pesto_locales.txt} into Gboard's own
 * preferences, once, on first start (RESEARCH §10, spike S2).
 *
 * <p>The file holds {@code key=value} lines produced by the on-device patcher from the stock
 * keyboard's enabled subtypes. The keys are Gboard's language preferences
 * ({@code enabled_input_method_entries}, {@code current_input_method_entry},
 * {@code enabled_ime_language_tags}); they are written to Gboard's device-protected shared
 * preferences before Gboard reads them. A {@code pesto_seeded} flag guards against re-seeding.
 *
 * <p>The values are written exactly as the asset gives them. There is no rule of ours on top, such
 * as {@code el-GR} to {@code el}: S2 saw Gboard store {@code enabled_ime_language_tags=en-GB;el}, and
 * the asset carries Gboard's own values.
 *
 * <p>TODO(S2): on the POCO, check that the seeded values match what Gboard itself writes for the
 * same languages, in particular {@code enabled_ime_language_tags}, and that the languages appear in
 * the first process, after {@code pm clear}.
 */
final class LocaleSeeder {
    private static final String TAG = "PestoBridge";
    private static final String ASSET = "pesto_locales.txt";
    private static final String SEEDED_KEY = "pesto_seeded";

    private LocaleSeeder() {}

    static void seedOnce(Context context) {
        if (context == null) return;
        try {
            // Gboard keeps these in device-protected storage so they are available before unlock.
            Context storage = context.createDeviceProtectedStorageContext();
            SharedPreferences prefs =
                    storage.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
            if (prefs.getBoolean(SEEDED_KEY, false)) return;

            Map<String, String> entries = readAsset(context.getAssets());
            if (entries.isEmpty()) return;

            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                editor.putString(entry.getKey(), entry.getValue());
            }
            editor.putBoolean(SEEDED_KEY, true);
            // Synchronous so the values are on disk before Gboard reads them in this same process.
            editor.commit();
            Log.i(TAG, "seeded " + entries.size() + " language preference(s)");
        } catch (RuntimeException e) {
            Log.w(TAG, "language seed failed", e);
        }
    }

    /** Parses the {@code key=value} lines of the asset; missing asset yields an empty map. */
    private static Map<String, String> readAsset(AssetManager assets) {
        Map<String, String> result = new LinkedHashMap<>();
        try (InputStream in = assets.open(ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                int eq = trimmed.indexOf('=');
                if (trimmed.isEmpty() || eq <= 0) continue;
                result.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
            }
        } catch (java.io.IOException e) {
            // No asset bundled (e.g. nothing to seed): nothing to do.
        }
        return result;
    }
}
