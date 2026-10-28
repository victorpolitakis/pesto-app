package com.victorpolitakis.pesto.dictation

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.victorpolitakis.pesto.accessibility.MicAnchor
import com.victorpolitakis.pesto.protocol.FieldContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldRulesTest {
    private val text = InputType.TYPE_CLASS_TEXT
    private fun field(inputType: Int = text, imeOptions: Int = 0, isPassword: Boolean = false, hint: String? = "Μήνυμα") =
        FieldState("com.whatsapp", inputType, imeOptions, hint, isPassword, isMultiLine = true)

    private val anchor = Bounds(965, 1517, 1075, 1633)

    @Test
    fun `input types`() {
        val cases = mapOf(
            text to "text",
            text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS to "email",
            text or InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT to "email_subject",
            text or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE or InputType.TYPE_TEXT_FLAG_MULTI_LINE to "short_message",
            text or InputType.TYPE_TEXT_VARIATION_URI to "uri",
            text or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT to "web_edit",
            text or InputType.TYPE_TEXT_VARIATION_FILTER to "filter",
            text or InputType.TYPE_TEXT_VARIATION_PASSWORD to "password",
            text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD to "password",
            InputType.TYPE_CLASS_NUMBER to "number",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD to "password",
            InputType.TYPE_CLASS_PHONE to "phone",
            InputType.TYPE_CLASS_DATETIME to "datetime",
            0 to "text",
        )
        cases.forEach { (type, name) -> assertEquals("0x${type.toString(16)}", name, FieldRules.inputTypeName(type)) }
    }

    @Test
    fun `field context`() {
        assertEquals(
            FieldContext("com.whatsapp", "text", "Μήνυμα", singleLine = false, isPassword = false, isIncognito = false),
            FieldRules.context(field()),
        )
    }

    @Test
    fun `incognito comes from the no personalized learning flag`() {
        assertTrue(FieldRules.context(field(imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)).isIncognito!!)
        assertFalse(FieldRules.context(field(imeOptions = EditorInfo.IME_ACTION_SEND)).isIncognito!!)
    }

    @Test
    fun `the hint is cut to 100 characters`() {
        assertEquals(100, FieldRules.context(field(hint = "α".repeat(500))).hint!!.length)
    }

    @Test
    fun `password by flag or by input type`() {
        assertTrue(FieldRules.isPassword(field(isPassword = true)))
        assertTrue(FieldRules.isPassword(field(inputType = text or InputType.TYPE_TEXT_VARIATION_PASSWORD)))
        assertFalse(FieldRules.isPassword(field()))
    }

    @Test
    fun `button decisions and their log reasons`() {
        fun reason(d: ButtonDecision) = (d as? ButtonDecision.Hidden)?.reason ?: "shown"
        assertEquals("shown", reason(FieldRules.button(field(), true, anchor, dictating = false)))
        assertEquals(ButtonDecision.Show(anchor), FieldRules.button(field(), true, anchor, dictating = false))
        assertEquals("no_editable_focus", reason(FieldRules.button(null, true, anchor, false)))
        assertEquals("password", reason(FieldRules.button(field(isPassword = true), true, anchor, false)))
        assertEquals("field_type=number", reason(FieldRules.button(field(InputType.TYPE_CLASS_NUMBER), true, anchor, false)))
        assertEquals("field_type=phone", reason(FieldRules.button(field(InputType.TYPE_CLASS_PHONE), true, anchor, false)))
        assertEquals("no_ime", reason(FieldRules.button(field(), false, null, false)))
        assertEquals("no_mic_anchor", reason(FieldRules.button(field(), true, null, false)))
        assertEquals("dictating", reason(FieldRules.button(field(), true, anchor, true)))
    }

    @Test
    fun `mic key of Gboard`() {
        assertTrue(MicAnchor.isMicKey("Use voice typing", "com.google.android.inputmethod.latin:id/key_pos_header_power_key"))
        assertTrue(MicAnchor.isMicKey("Χρήση φωνητικής πληκτρολόγησης", null))
        assertFalse(MicAnchor.isMicKey("Emoji", "com.google.android.inputmethod.latin:id/key_pos_header_access_points_menu"))
        assertFalse(MicAnchor.isMicKey(null, null))
    }
}
