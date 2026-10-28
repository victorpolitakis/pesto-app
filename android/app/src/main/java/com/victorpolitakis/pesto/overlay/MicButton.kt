package com.victorpolitakis.pesto.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/** The round button that covers the keyboard's mic key. TODO(phase 6): the design. */
@Composable
fun MicButton(onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onTap)
            .testTag("mic_button"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(AppIcons.Mic),
            contentDescription = stringResource(R.string.cd_mic),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth(0.5f),
        )
    }
}

@Preview
@Composable
private fun MicButtonPreview() {
    PestoTheme { Box(Modifier.size(56.dp).padding(4.dp)) { MicButton(onTap = {}) } }
}
