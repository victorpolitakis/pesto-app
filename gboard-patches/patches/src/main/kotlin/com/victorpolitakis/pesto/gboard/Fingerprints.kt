package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.methodCall
import app.morphe.patcher.string

/**
 * Fingerprints for the "Gboard with Pesto" patches.
 *
 * Every fingerprint is built from log strings and types that stay stable across Gboard builds, not
 * from obfuscated class or method names, which change each release (RESEARCH §11). The obfuscated
 * names noted in comments (e.g. `krc`, `weg`) are only the matches observed in 18.5.4 / 18.5.6 and
 * are not relied upon.
 */

/**
 * The signature check: a static `(Context, String) -> boolean` in a class whose log tag contains
 * `SignatureUtils`, comparing two byte arrays with `Arrays.equals`.
 * Observed: `sfr.a` (18.5.4), `akft.a` (18.5.6).
 */
internal object SignatureCheckFingerprint : Fingerprint(
    isStatic = true,
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    filters = listOf(
        string("/SignatureUtils", StringComparisonType.CONTAINS),
        methodCall(
            definingClass = "Ljava/util/Arrays;",
            name = "equals",
            parameters = listOf("[B", "[B"),
        ),
    ),
)

/**
 * Gboard's own voice-input handler for the mic key. Identified by its log line
 * `handling LAUNCH_VOICE_IME, data=%s [RD]`. Its single object parameter is the key event.
 * Observed: `krc.g(Lohe;)Z` (18.5.4), `weg.g(Lacwt;)Z` (18.5.6).
 */
internal object VoiceInputHandlerFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("L"),
    filters = listOf(
        string("handling LAUNCH_VOICE_IME, data=%s [RD]", StringComparisonType.CONTAINS),
    ),
)

/**
 * The NGA ("Rambler"/Assistant) voice-input handler switcher for the same key event, identified by
 * its `NgaVoiceInputHandlerSwitcher` log tag and the `handle` string in the same method.
 * Observed: `iod.g` (18.5.4), `reg.g` (18.5.6).
 */
internal object NgaVoiceInputHandlerFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("L"),
    filters = listOf(
        string("NgaVoiceInputHandlerSwitcher", StringComparisonType.CONTAINS),
        string("handle", StringComparisonType.CONTAINS),
    ),
)

/**
 * The keyboard's own `InputMethodService` subclass, matched by its superclass and its `onCreate`.
 * There is exactly one such class. `PestoBridge.register` is called at the start of `onCreate`.
 * Observed: `phr` (18.5.4), `aeut` (18.5.6).
 */
internal object ImeServiceOnCreateFingerprint : Fingerprint(
    superclass = "Landroid/inputmethodservice/InputMethodService;",
    name = "onCreate",
    returnType = "V",
    parameters = emptyList(),
)

// The language seed needs no fingerprint: it reads the application class from the manifest and
// walks up to the superclass that declares attachBaseContext (see LanguageSeedPatch).
