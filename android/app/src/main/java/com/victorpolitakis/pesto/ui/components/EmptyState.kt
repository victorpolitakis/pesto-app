package com.victorpolitakis.pesto.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/** What a list shows with nothing in it yet. TODO(phase 6): an illustration and the design. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp).testTag("empty"), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    PestoTheme { EmptyState("Καμία υπαγόρευση ακόμα") }
}

@Preview
@Composable
private fun EmptyStateLongGreekPreview() {
    PestoTheme { EmptyState("Δεν υπάρχει τίποτα εδώ ακόμα. ".repeat(6)) }
}
