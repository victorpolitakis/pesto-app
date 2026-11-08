package com.victorpolitakis.pesto.ui.assistant

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.protocol.AssistantDestination
import com.victorpolitakis.pesto.protocol.NavigationTarget
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.components.DictationRow
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.components.PestoTopBar
import com.victorpolitakis.pesto.ui.theme.PestoTheme

@Composable
fun AssistantRoute(
    onBack: () -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
    onOpenDictation: (Long) -> Unit,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    var input by rememberSaveable { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].language
    AssistantScreen(
        input = input,
        state = state,
        onInputChange = { input = it },
        onSend = { query ->
            input = ""
            viewModel.send(query, language)
        },
        onBack = onBack,
        onNavigate = onNavigate,
        onOpenDictation = onOpenDictation,
        onOpenPairing = onOpenPairing,
        modifier = modifier,
    )
}

/**
 * X3 Assistant (chatbot): stateless chat screen over `POST /v1/assistant`, with
 * starter suggestions, 1-tap navigation action chips, and History citations.
 * TODO(phase 6): layout and bubble styling, with the rest of the screens.
 */
@Composable
fun AssistantScreen(
    input: String,
    state: AssistantState,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
    onOpenDictation: (Long) -> Unit,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.busy) {
        val total = state.messages.size + if (state.busy) 1 else 0
        if (total > 0) listState.scrollToItem(total - 1)
    }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .testTag("screen_assistant"),
    ) {
        PestoTopBar(stringResource(R.string.cd_chatbot), onBack)
        if (!state.loading && !state.paired) {
            UnpairedCard(onOpenPairing, Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("assistant_list"),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.loading && state.messages.isEmpty() && !state.busy) {
                    item(key = "intro") {
                        StarterPrompts(onPick = onSend, Modifier.padding(horizontal = 16.dp))
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    MessageItem(
                        message = message,
                        onNavigate = onNavigate,
                        onOpenDictation = onOpenDictation,
                    )
                }
                if (state.busy) {
                    item(key = "thinking") {
                        ThinkingBubble(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            ComposerBar(
                input = input,
                enabled = state.paired && !state.busy && state.cooldownSeconds == 0,
                onInputChange = onInputChange,
                onSend = { if (input.isNotBlank()) onSend(input) },
            )
        }
    }
}

@Composable
private fun UnpairedCard(onOpenPairing: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("assistant_unpaired"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.assistant_unpaired),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onOpenPairing,
            modifier = Modifier.heightIn(min = 48.dp).testTag("assistant_open_pairing"),
        ) {
            Text(stringResource(R.string.assistant_pair_open))
        }
    }
}

@Composable
private fun StarterPrompts(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        "suggestion_stats" to stringResource(R.string.assistant_suggestion_stats),
        "suggestion_recent" to stringResource(R.string.assistant_suggestion_recent),
        "suggestion_offline" to stringResource(R.string.assistant_suggestion_offline),
    )
    Column(
        modifier.fillMaxWidth().testTag("assistant_suggestions"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.assistant_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        for ((tag, prompt) in suggestions) {
            Surface(
                onClick = { onPick(prompt) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Text(prompt, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    onNavigate: (NavigationTarget) -> Unit,
    onOpenDictation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        message.isUser -> MaterialTheme.colorScheme.primaryContainer
        message.isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        message.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val body = if (message.isError) {
        when (message.errorKind) {
            AssistantErrorKind.EXPIRED -> stringResource(R.string.assistant_error_expired)
            AssistantErrorKind.RATE_LIMITED -> pluralStringResource(
                R.plurals.assistant_error_rate_limited,
                message.retryAfterS,
                message.retryAfterS,
            )
            AssistantErrorKind.NETWORK, null -> stringResource(R.string.assistant_error_network)
        }
    } else {
        message.text
    }

    Column(
        modifier.fillMaxWidth().testTag("message_${message.id}"),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 320.dp),
            shape = MaterialTheme.shapes.large,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        val target = message.navigate
        if (target != null) {
            val label = stringResource(R.string.assistant_open_destination, destinationLabel(target))
            OutlinedButton(
                onClick = { onNavigate(target) },
                modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp).testTag("assistant_nav_${message.id}"),
            ) {
                Text(label)
            }
        }
        for (row in message.citations) {
            key(row.id) {
                DictationRow(row, onClick = { onOpenDictation(row.id) })
            }
        }
    }
}

@Composable
private fun ThinkingBubble(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("assistant_thinking"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            stringResource(R.string.assistant_thinking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ComposerBar(
    input: String,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = enabled && input.isNotBlank()
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("assistant_composer"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f).testTag("assistant_input"),
            enabled = enabled,
            placeholder = { Text(stringResource(R.string.assistant_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
        )
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.testTag("assistant_send"),
        ) {
            Icon(painterResource(AppIcons.Send), contentDescription = stringResource(R.string.cd_send))
        }
    }
}

@Composable
private fun destinationLabel(target: NavigationTarget): String {
    @StringRes val resId = when (target.destination) {
        AssistantDestination.HOME -> R.string.tab_home
        AssistantDestination.PROFILES -> R.string.title_profiles
        AssistantDestination.DICTIONARY -> R.string.title_dictionary
        AssistantDestination.HISTORY -> R.string.title_history
        AssistantDestination.SEARCH -> R.string.title_search
        AssistantDestination.ANALYTICS -> R.string.title_analytics
        AssistantDestination.APPS -> R.string.title_apps
        AssistantDestination.SETTINGS -> R.string.title_settings
        AssistantDestination.SETTINGS_LANGUAGES -> R.string.settings_languages
        AssistantDestination.SETTINGS_PROFILE -> R.string.settings_default_profile
        AssistantDestination.SETTINGS_OFFLINE -> R.string.settings_offline
        AssistantDestination.SETTINGS_PERMISSIONS -> R.string.settings_permissions
        AssistantDestination.SETTINGS_OVERLAY -> R.string.settings_overlay
        AssistantDestination.SETTINGS_XIAOMI -> R.string.settings_xiaomi
        AssistantDestination.SETTINGS_BACKEND -> R.string.settings_backend
        AssistantDestination.SETTINGS_PRIVACY -> R.string.settings_privacy
        AssistantDestination.SETTINGS_ABOUT -> R.string.settings_about
        AssistantDestination.PROFILE_DETAIL ->
            ProfilesCatalog.find(target.profileId)?.name ?: R.string.title_profiles
    }
    return stringResource(resId)
}

@Composable
private fun ScreenPreview(state: AssistantState, input: String = "") {
    PestoTheme {
        AssistantScreen(
            input = input,
            state = state,
            onInputChange = {},
            onSend = {},
            onBack = {},
            onNavigate = {},
            onOpenDictation = {},
            onOpenPairing = {},
        )
    }
}

@Preview
@Composable
private fun AssistantEmptyPreview() {
    ScreenPreview(AssistantState(paired = true, loading = false))
}

@Preview(heightDp = 800)
@Composable
private fun AssistantChatPreview() {
    val gmail = AppDisplay("com.google.android.gm", "Gmail", null)
    val messages = listOf(
        ChatMessage(id = 1, isUser = true, text = "Τι υπαγόρευσα σήμερα;"),
        ChatMessage(
            id = 2,
            isUser = false,
            text = "Σήμερα έκανες 2 υπαγορεύσεις στο Gmail με συνολικά 56 λέξεις.",
            navigate = NavigationTarget(AssistantDestination.ANALYTICS),
            citations = listOf(DictationRowUi(1, gmail, System.currentTimeMillis(), 56)),
        ),
    )
    ScreenPreview(AssistantState(paired = true, messages = messages, loading = false), input = "Ευχαριστώ")
}

@Preview(heightDp = 900, fontScale = 1.5f)
@Composable
private fun AssistantLongGreekPreview() {
    val longApp = AppDisplay("gr.example", "Εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα για έλεγχο", null)
    val messages = listOf(
        ChatMessage(id = 1, isUser = true, text = "Πόσα λεπτά έχω εξοικονομήσει αυτή την εβδομάδα στις υπαγορεύσεις μου;"),
        ChatMessage(
            id = 2,
            isUser = false,
            text = "Αυτή την εβδομάδα έχεις εξοικονομήσει 163,8 λεπτά σε 128 υπαγορεύσεις με μέση ταχύτητα 132 λέξεις το λεπτό.",
            navigate = NavigationTarget(AssistantDestination.SETTINGS_LANGUAGES),
            citations = listOf(DictationRowUi(2, longApp, System.currentTimeMillis(), 12_345)),
        ),
    )
    ScreenPreview(AssistantState(paired = true, messages = messages, loading = false))
}
