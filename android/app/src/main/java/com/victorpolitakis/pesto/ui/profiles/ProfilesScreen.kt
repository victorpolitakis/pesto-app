package com.victorpolitakis.pesto.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.components.ScreenSkeleton
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * Profiles (C9): the ready-made profiles of the backend. Skeleton of step 6: the data come in step 9.
 * Until then [onOpenProfile] is not used.
 * TODO(phase 6): layout and design.
 */
@Composable
fun ProfilesScreen(
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSkeleton(stringResource(R.string.title_profiles), modifier.testTag("screen_profiles"))
}

@Preview
@Composable
private fun ProfilesPreview() {
    PestoTheme { ProfilesScreen(onOpenProfile = {}) }
}
