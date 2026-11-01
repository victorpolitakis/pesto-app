package com.victorpolitakis.pesto.gboard

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The class side of [SignatureCheckFingerprint]: the `SignatureUtils` tag is found in any method of
 * the class, such as `<clinit>`, not only in the check. Built from dexlib2 objects, no APK.
 */
class SignatureCheckFingerprintTest {
    private val tag = "com/google/android/libraries/inputmethod/utils/SignatureUtils"

    private fun method(name: String, vararg strings: String): Method {
        val instructions = strings.map { ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(it)) } +
            ImmutableInstruction10x(Opcode.RETURN_VOID)
        return ImmutableMethod(
            "Lsfr;", name, emptyList(), "V", AccessFlags.STATIC.value, null, null,
            ImmutableMethodImplementation(1, instructions, null, null),
        )
    }

    private fun classOf(vararg methods: Method) =
        ImmutableClassDef("Lsfr;", AccessFlags.PUBLIC.value, "Ljava/lang/Object;", null, null, null, null, methods.toList())

    @Test
    fun `the tag in clinit marks the class, as in 18_5_4 and 18_5_6`() {
        val classDef = classOf(
            method("<clinit>", tag),
            // The check itself, a(Context, String)Z, holds no such string.
            method("a"),
        )
        assertTrue(classDef.containsString(SIGNATURE_UTILS_TAG))
    }

    @Test
    fun `the tag in another method's log marks the class too`() {
        assertTrue(classOf(method("a"), method("c", "computing digest for %s in $tag")).containsString(SIGNATURE_UTILS_TAG))
    }

    @Test
    fun `a class without the tag does not match`() {
        assertFalse(classOf(method("a", "SignatureHelper"), method("<clinit>")).containsString(SIGNATURE_UTILS_TAG))
    }
}
