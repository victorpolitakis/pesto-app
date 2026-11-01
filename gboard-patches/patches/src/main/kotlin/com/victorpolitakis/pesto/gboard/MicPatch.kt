package com.victorpolitakis.pesto.gboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.p0Register

private const val EXTENSION_CLASS = "Lcom/victorpolitakis/pesto/bridge/PestoBridge;"

/**
 * Routes Gboard's microphone key to Pesto dictation.
 *
 * Both handlers of the `LAUNCH_VOICE_IME` key event (Gboard's own voice input and the NGA/Assistant
 * switcher) get the same hook at their entry: they call `PestoBridge.onVoiceKey(handler, event)`.
 * The bridge inspects the event (keycode `-10042` with null data, RESEARCH §1, §9) and, when it is
 * the mic key, starts Pesto dictation and returns `true` so the handler consumes the event. For any
 * other event, or when Pesto declines, the bridge returns `false` and the handler runs unchanged,
 * which keeps Gboard's own voice input as the fallback.
 *
 * The event inspection and the lookup of the handler's `Context` field are done in the extension
 * with reflection rather than in smali, so the patch does not depend on the obfuscated field names
 * that change between builds.
 *
 * `PestoBridge.register` is also called at the start of the keyboard's `InputMethodService.onCreate`
 * so the bridge has the service to commit text through (RESEARCH §9).
 */
@Suppress("unused")
val micPatch = bytecodePatch(
    name = "Microphone to Pesto",
    description = "Routes the keyboard's microphone key to Pesto dictation.",
    default = true,
) {
    compatibleWith(Gboard.COMPATIBILITY)

    extendWith("extensions/pestobridge.mpe")

    execute {
        // Register the bridge with the keyboard service.
        ImeServiceOnCreateFingerprint.method.addInstructions(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS->register(Ljava/lang/Object;)V",
        )

        // Hook both handlers of the mic key event.
        listOf(VoiceInputHandlerFingerprint, NgaVoiceInputHandlerFingerprint).forEach { fingerprint ->
            fingerprint.method.apply {
                val p0 = p0Register
                val event = p0 + 1
                val free = findFreeRegister(0, p0, event)
                addInstructions(
                    0,
                    """
                        invoke-static/range { p0 .. p1 }, $EXTENSION_CLASS->onVoiceKey(Ljava/lang/Object;Ljava/lang/Object;)Z
                        move-result v$free
                        if-eqz v$free, :pesto_pass
                        const v$free, 0x1
                        return v$free
                        :pesto_pass
                        nop
                    """,
                )
            }
        }
    }
}
