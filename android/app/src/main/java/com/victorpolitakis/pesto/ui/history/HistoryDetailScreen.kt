package com.victorpolitakis.pesto.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.DictationDetail
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.components.EmptyState
import com.victorpolitakis.pesto.ui.components.PestoTopBar
import com.victorpolitakis.pesto.ui.components.exactTime
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One dictation and its app. [detail] null once loaded: deleted meanwhile. */
data class HistoryDetailState(
    val detail: DictationDetail? = null,
    val app: AppDisplay? = null,
    val loading: Boolean = true,
)

/** [onDeleted]: the dictation is gone; what it gets puts it back (the undo of the snackbar, Δ15). */
@Composable
fun HistoryDetailRoute(
    onBack: () -> Unit,
    onDeleted: (undo: suspend () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    HistoryDetailScreen(
        state,
        onBack = onBack,
        onCopy = { state.detail?.let { copy(context, it.text) } },
        onDelete = { scope.launch { viewModel.delete()?.let(onDeleted) } },
        modifier = modifier,
    )
}

/** Android 13+ shows its own confirmation of the copy. */
private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pesto", text))
}

/**
 * History detail (C8): first the text that was dictated, with copy and delete.
 * Below it, the whole record, for the data check of phase 4.
 * TODO(phase 6): layout and design.
 */
@Composable
fun HistoryDetailScreen(
    state: HistoryDetailState,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("screen_history_detail")) {
        PestoTopBar(stringResource(R.string.title_dictation), onBack)
        val detail = state.detail
        when {
            state.loading -> Unit
            detail == null -> EmptyState(stringResource(R.string.history_detail_gone))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                SelectionContainer {
                    Text(detail.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("dictation_text"))
                }
                Spacer(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCopy, modifier = Modifier.testTag("copy")) {
                        Icon(painterResource(AppIcons.Copy), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.copy))
                    }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.testTag("delete")) {
                        Icon(painterResource(AppIcons.Delete), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete))
                    }
                    // TODO(X9): «Re-style» here, on the raw text, through the /v1/rewrite of X13.
                }
                Spacer(Modifier.size(24.dp))
                HorizontalDivider()
                Spacer(Modifier.size(16.dp))
                Record(detail, state.app)
            }
        }
    }
}

/** Every column of the record (the table of phase 4), as label and value. */
@Composable
private fun Record(d: DictationDetail, app: AppDisplay?, modifier: Modifier = Modifier) {
    val none = stringResource(R.string.value_none)
    val appText = when {
        app == null -> none
        app.packageName == null -> app.name
        else -> stringResource(R.string.value_with_id, app.name, app.packageName)
    }
    Column(modifier.fillMaxWidth().testTag("record"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.detail_data), style = MaterialTheme.typography.titleMedium)
        if (d.rawText != d.text) Field(stringResource(R.string.detail_raw_text), d.rawText, "field_raw_text")
        Field(stringResource(R.string.detail_app), appText, "field_app")
        Field(stringResource(R.string.detail_input_type), d.inputType ?: none, "field_input_type")
        Field(stringResource(R.string.detail_started), exactTime(d.startedAt), "field_started")
        Field(stringResource(R.string.detail_ended), exactTime(d.createdAt), "field_ended")
        Field(stringResource(R.string.detail_duration), stringResource(R.string.value_seconds, (d.createdAt - d.startedAt) / 1000.0), "field_duration")
        Field(stringResource(R.string.detail_audio), d.audioS?.let { stringResource(R.string.value_seconds, it) } ?: none, "field_audio")
        Field(stringResource(R.string.detail_words), d.words.toString(), "field_words")
        Field(stringResource(R.string.detail_output_words), d.outputWords?.toString() ?: none, "field_output_words")
        Field(stringResource(R.string.detail_wpm), d.wpm?.roundToInt()?.toString() ?: none, "field_wpm")
        val profile = ProfilesCatalog.find(d.profile)
        Field(
            stringResource(R.string.detail_profile),
            if (profile == null) d.profile else stringResource(R.string.value_with_id, stringResource(profile.name), d.profile),
            "field_profile",
        )
        Field(stringResource(R.string.detail_fallback), d.fallbackReason ?: none, "field_fallback")
        Field(stringResource(R.string.detail_end_reason), d.endReason, "field_end_reason")
        Field(stringResource(R.string.detail_insert_method), d.insertMethod, "field_insert_method")
        Field(stringResource(R.string.detail_stt_ms), d.sttMs?.let { stringResource(R.string.value_ms, it) } ?: none, "field_stt_ms")
        Field(stringResource(R.string.detail_llm_ms), d.llmMs?.let { stringResource(R.string.value_ms, it) } ?: none, "field_llm_ms")
        Field(stringResource(R.string.detail_total_ms), d.totalMs?.let { stringResource(R.string.value_ms, it) } ?: none, "field_total_ms")
    }
}

/** One node for TalkBack: the label, then the value. */
@Composable
private fun Field(label: String, value: String, tag: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}.testTag(tag)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
    }
}

private val sample = DictationDetail(
    id = 1, text = "Καλημέρα Γιώργο,\n\nΤο meeting πάει για την Πέμπτη στις 10:00.", rawText = "καλημέρα γιώργο το meeting πάει για την πέμπτη στις δέκα",
    packageName = "com.google.android.gm", inputType = "text", profile = "mail", startedAt = 1_761_660_000_000, createdAt = 1_761_660_007_500,
    audioS = 5.7, words = 10, outputWords = 11, fallbackReason = null, endReason = "user", insertMethod = "COMMIT_TEXT",
    sttMs = 152, llmMs = 1496, totalMs = 1648,
)

@Preview
@Composable
private fun HistoryDetailGonePreview() {
    PestoTheme { HistoryDetailScreen(HistoryDetailState(loading = false), onBack = {}, onCopy = {}, onDelete = {}) }
}

@Preview(heightDp = 1200)
@Composable
private fun HistoryDetailPreview() {
    PestoTheme {
        HistoryDetailScreen(
            HistoryDetailState(sample, AppDisplay("com.google.android.gm", "Gmail", null), loading = false),
            onBack = {},
            onCopy = {},
            onDelete = {},
        )
    }
}

@Preview(heightDp = 1200)
@Composable
private fun HistoryDetailLongGreekPreview() {
    PestoTheme {
        HistoryDetailScreen(
            HistoryDetailState(
                sample.copy(text = "Καλημέρα σε όλους και ευχαριστώ για την υπομονή σας. ".repeat(30), fallbackReason = "guard:length"),
                AppDisplay("gr.example.app", "Εφαρμογή με πολύ μεγάλο ελληνικό όνομα", null),
                loading = false,
            ),
            onBack = {},
            onCopy = {},
            onDelete = {},
        )
    }
}
