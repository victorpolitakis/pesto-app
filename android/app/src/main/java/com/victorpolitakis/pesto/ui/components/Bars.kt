package com.victorpolitakis.pesto.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * The top of the screens without the bottom bar (Δ20): «back» and the title.
 * TODO(phase 6): the design of the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PestoTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.testTag("top_bar"),
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                Icon(painterResource(AppIcons.Back), contentDescription = stringResource(R.string.cd_back))
            }
        },
        actions = actions,
        // The Scaffold of PestoNavHost already keeps the content below the status bar.
        windowInsets = WindowInsets(0),
    )
}

/** The title of a tab of the bottom bar. TODO(phase 6): the design of the titles. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { heading() }
            .testTag("title"),
    )
}

@Preview
@Composable
private fun TopBarPreview() {
    PestoTheme { PestoTopBar("Στατιστικά", onBack = {}) }
}

@Preview
@Composable
private fun TopBarLongGreekPreview() {
    PestoTheme { PestoTopBar("Ρυθμίσεις της σύνδεσης με τον backend και του απορρήτου", onBack = {}) }
}

@Preview
@Composable
private fun ScreenTitlePreview() {
    PestoTheme { ScreenTitle("Ιστορικό") }
}
