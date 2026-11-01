package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Fingerprints for the "Gboard with Pesto" patches.
 *
 * Every fingerprint is built from log strings and types that stay stable across Gboard builds, not
 * from obfuscated class or method names, which change each release (RESEARCH §11). The obfuscated
 * names noted in comments (e.g. `krc`, `weg`) are only the matches observed in 18.5.4 / 18.5.6 and
 * are not relied upon.
 */

/** Part of the log tag of the signature utilities class, `.../inputmethod/utils/SignatureUtils`. */
internal const val SIGNATURE_UTILS_TAG = "/SignatureUtils"

/**
 * The signature check: a `public static (Context, String) -> boolean` that compares two byte arrays
 * with `Arrays.equals`, in the class whose log tag contains `SignatureUtils`.
 *
 * The tag is not in the check itself: it is in the class's `<clinit>` and in the logs of
 * `c(Context, String)[B`. So the class is matched by the tag in any of its methods, and the method
 * by its flags, signature and the `Arrays.equals` call.
 * Observed: `sfr.a` (18.5.4), `akft.a` (18.5.6).
 */
internal object SignatureCheckFingerprint : Fingerprint(
    isStatic = true,
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    filters = listOf(
        methodCall(smali = "Ljava/util/Arrays;->equals([B[B)Z"),
    ),
    custom = { method, classDef ->
        AccessFlags.PUBLIC.isSet(method.accessFlags) && classDef.containsString(SIGNATURE_UTILS_TAG)
    },
)

/** Whether any method of the class loads a string constant that contains [substring]. */
internal fun ClassDef.containsString(substring: String): Boolean =
    methods.any { method ->
        method.implementation?.instructions?.any { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is StringReference && substring in reference.string
        } == true
    }

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

/**
 * Every fingerprint of these patches. A fingerprint is a global object that keeps its match; the
 * patcher clears them in `Patcher.get()`, and code that runs patches without it (the tests) clears
 * them with `clearMatch()` through this list.
 */
internal val gboardFingerprints: List<Fingerprint> = listOf(
    SignatureCheckFingerprint,
    VoiceInputHandlerFingerprint,
    NgaVoiceInputHandlerFingerprint,
    ImeServiceOnCreateFingerprint,
)
