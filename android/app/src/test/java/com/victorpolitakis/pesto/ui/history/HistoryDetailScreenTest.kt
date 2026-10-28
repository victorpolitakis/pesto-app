package com.victorpolitakis.pesto.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.DictationDetail
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** C8: the text first, copy and delete, then the record for phase 4. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryDetailScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val detail = DictationDetail(
        id = 1, text = "Καλημέρα Γιώργο.", rawText = "εεε καλημέρα γιώργο", packageName = "com.google.android.gm",
        inputType = "text", profile = "mail", startedAt = 1_000_000, createdAt = 1_007_500, audioS = 6.0, words = 3,
        outputWords = 2, fallbackReason = null, endReason = "user", insertMethod = "COMMIT_TEXT",
        sttMs = 152, llmMs = 1496, totalMs = 1648,
    )

    private fun show(d: DictationDetail? = detail, onCopy: () -> Unit = {}, onDelete: () -> Unit = {}) {
        rule.setContent {
            PestoTheme {
                HistoryDetailScreen(
                    HistoryDetailState(d, AppDisplay("com.google.android.gm", "Gmail", null), loading = false),
                    onBack = {},
                    onCopy = onCopy,
                    onDelete = onDelete,
                )
            }
        }
    }

    @Test
    fun `the text comes first, with copy and delete`() {
        var copied = 0
        var deleted = 0
        show(onCopy = { copied++ }, onDelete = { deleted++ })
        rule.onNodeWithTag("dictation_text").assertTextContains("Καλημέρα Γιώργο.")
        rule.onNodeWithTag("copy").performClick()
        rule.onNodeWithTag("delete").performClick()
        assertEquals(1, copied)
        assertEquals(1, deleted)
    }

    @Test
    fun `the record has the data of phase 4`() {
        show()
        rule.onNodeWithTag("field_raw_text").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("field_app").performScrollTo().assertTextContains("Gmail (com.google.android.gm)")
        rule.onNodeWithTag("field_profile").performScrollTo().assertTextContains("Email (mail)")
        rule.onNodeWithTag("field_duration").performScrollTo().assertTextContains("7", substring = true)
        rule.onNodeWithTag("field_wpm").performScrollTo().assertTextContains("30")
        rule.onNodeWithTag("field_insert_method").performScrollTo().assertTextContains("COMMIT_TEXT")
        rule.onNodeWithTag("field_total_ms").performScrollTo().assertTextContains("1648 ms")
    }

    @Test
    fun `the raw text shows only when the profile changed it`() {
        show(detail.copy(rawText = detail.text))
        rule.onNodeWithTag("field_raw_text").assertDoesNotExist()
    }

    @Test
    fun `a deleted dictation says so`() {
        show(d = null)
        rule.onNodeWithTag("empty").assertIsDisplayed()
        rule.onNodeWithTag("copy").assertDoesNotExist()
    }
}
