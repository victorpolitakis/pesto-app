package com.victorpolitakis.pesto.dictation

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.victorpolitakis.pesto.protocol.FieldContext

/** Whether the mic button is shown, and why not. The reason goes to the `Pesto/Button` log. */
sealed interface ButtonDecision {
    data class Show(val anchor: Bounds) : ButtonDecision

    data class Hidden(val reason: String) : ButtonDecision
}

/** Pure rules about the focused field (C4, C6, C7, Δ7, Δ15). No Android objects, only their constants. */
object FieldRules {
    /** The `input_type` values of docs/protocol.md. */
    fun inputTypeName(inputType: Int): String {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER ->
                if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) "password" else "number"
            InputType.TYPE_CLASS_PHONE -> "phone"
            InputType.TYPE_CLASS_DATETIME -> "datetime"
            InputType.TYPE_CLASS_TEXT -> when (variation) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> "email"
                InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT -> "email_subject"
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> "short_message"
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE -> "long_message"
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> "person_name"
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> "postal_address"
                InputType.TYPE_TEXT_VARIATION_URI -> "uri"
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> "web_edit"
                InputType.TYPE_TEXT_VARIATION_FILTER -> "filter"
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> "password"
                else -> "text"
            }
            else -> "text"
        }
    }

    fun isPassword(field: FieldState) = field.isPassword || inputTypeName(field.inputType) == "password"

    /** The same flag that stops Gboard from learning (Chrome sets it in incognito tabs). */
    fun isIncognito(field: FieldState) = field.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0

    fun context(field: FieldState) = FieldContext(
        packageName = field.packageName,
        inputType = inputTypeName(field.inputType),
        hint = field.hint?.take(100), // third-party text: short, and never used by the backend yet
        singleLine = !field.isMultiLine,
        isPassword = isPassword(field),
        isIncognito = isIncognito(field),
    )

    /** Fields where dictation makes no sense: the button stays hidden (Δ7). */
    private val noButtonTypes = setOf("number", "phone", "datetime")

    /**
     * [field] is null when nothing editable has focus. [micAnchor] is the bounds
     * of the keyboard's own mic key, which the button covers (C4). Without it, no
     * button (Δ15).
     */
    fun button(field: FieldState?, keyboardVisible: Boolean, micAnchor: Bounds?, dictating: Boolean): ButtonDecision {
        val type = field?.let { inputTypeName(it.inputType) }
        return when {
            dictating -> ButtonDecision.Hidden("dictating")
            field == null -> ButtonDecision.Hidden("no_editable_focus")
            isPassword(field) -> ButtonDecision.Hidden("password")
            type in noButtonTypes -> ButtonDecision.Hidden("field_type=$type")
            !keyboardVisible -> ButtonDecision.Hidden("no_ime")
            micAnchor == null -> ButtonDecision.Hidden("no_mic_anchor")
            else -> ButtonDecision.Show(micAnchor)
        }
    }
}
