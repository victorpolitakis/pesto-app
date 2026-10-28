package com.victorpolitakis.pesto.ui.dictionary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.components.ScreenSkeleton
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * Dictionary (C2): the terms that go to the speech recognition. Skeleton of step 6: the data come in step 8.
 * TODO(phase 6): layout and design.
 */
@Composable
fun DictionaryScreen(
    modifier: Modifier = Modifier,
) {
    ScreenSkeleton(stringResource(R.string.title_dictionary), modifier.testTag("screen_dictionary"))
}

@Preview
@Composable
private fun DictionaryPreview() {
    PestoTheme { DictionaryScreen() }
}
