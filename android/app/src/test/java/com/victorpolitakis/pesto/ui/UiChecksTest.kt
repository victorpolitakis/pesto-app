package com.victorpolitakis.pesto.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.overlay.ListeningPanel
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The checks of levels 2 and 3 catch what they are for (spike P2): one
 * deliberate error each. The panel of phase 2 passes them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiChecksTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Panel() {
        ListeningPanel(
            status = "Ακούω…",
            profile = "Email",
            text = "Καλημέρα Γιώργο, το meeting πάει για την Πέμπτη.",
            levels = List(24) { (it % 7) / 7f },
            paused = false,
            onCancel = {},
            onPauseResume = {},
            onFinish = {},
            modifier = Modifier.height(300.dp),
        )
    }

    private fun failure(check: () -> Unit): String = assertThrows(Throwable::class.java) { check() }.toString()

    @Test
    fun `the listening panel passes levels 2 and 3`() {
        rule.setContent { PestoTheme { Panel() } }
        rule.assertLayout()
        rule.assertAccessible()
    }

    @Test
    fun `P2 - a button too small for a finger fails the accessibility checks`() {
        rule.setContent {
            PestoTheme {
                Column {
                    Panel()
                    // The deliberate error: a control of 16dp, a third of a finger.
                    Box(Modifier.size(16.dp).clickable {}.semantics { contentDescription = "Μικρό κουμπί" })
                }
            }
        }
        val message = failure { rule.assertAccessible() }
        assertTrue(message, message.contains("touch target", ignoreCase = true))
    }

    @Test
    fun `two controls on top of each other fail the layout checks`() {
        rule.setContent {
            PestoTheme {
                Box {
                    Button(onClick = {}) { Text("Πρώτο") }
                    Button(onClick = {}, modifier = Modifier.offset(x = 24.dp)) { Text("Δεύτερο") }
                }
            }
        }
        val message = failure { rule.assertLayout() }
        assertTrue(message, "controls overlap" in message)
    }

    @Test
    fun `a text past the side of the screen fails the layout checks`() {
        rule.setContent {
            PestoTheme {
                Box(Modifier.fillMaxWidth()) {
                    Text("Εκτός οθόνης", Modifier.align(Alignment.TopEnd).offset(x = 48.dp))
                }
            }
        }
        val message = failure { rule.assertLayout() }
        assertTrue(message, "off the side of the screen" in message)
    }

    @Test
    fun `a word wider than its box fails the layout checks`() {
        rule.setContent {
            PestoTheme { Text("Μιαπάραπολύμεγάληλέξηχωρίςκενά", Modifier.width(60.dp), softWrap = false, maxLines = 1) }
        }
        val message = failure { rule.assertLayout() }
        assertTrue(message, "text wider than its box" in message)
    }
}
