package com.victorpolitakis.pesto.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.ui.components.DictationRow
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.components.EmptyState
import com.victorpolitakis.pesto.ui.components.ScreenTitle
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/** What History shows: the rows loaded so far, out of [total]. */
data class HistoryState(
    val rows: List<DictationRowUi> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
)

@Composable
fun HistoryRoute(
    onOpenDictation: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(state, onOpenDictation, onLoadMore = viewModel::loadMore, modifier = modifier)
}

/**
 * History (C8): every dictation, newest first, 100 at a time (no Paging). A row
 * opens the dictation. No warnings here: C3 asks for what is missing.
 * TODO(phase 6): layout and design, after Victor's sketch.
 */
@Composable
fun HistoryScreen(
    state: HistoryState,
    onOpenDictation: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadMore by rememberUpdatedState(onLoadMore)
    Column(modifier.fillMaxSize().testTag("screen_history")) {
        ScreenTitle(stringResource(R.string.title_history))
        when {
            state.loading -> Unit
            state.rows.isEmpty() -> EmptyState(stringResource(R.string.history_empty))
            else -> {
                Text(
                    pluralStringResource(R.plurals.dictations, state.total, state.total),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("history_count"),
                )
                LazyColumn(Modifier.fillMaxSize().testTag("history_list")) {
                    items(state.rows, key = { it.id }) { row ->
                        DictationRow(row, onClick = { onOpenDictation(row.id) })
                    }
                    if (state.rows.size < state.total) {
                        // The end of the list is on screen: the next 100.
                        item(key = "more") { LaunchedEffect(state.rows.size) { loadMore() } }
                    }
                }
            }
        }
    }
}

private fun sampleRows(name: String, count: Int) = List(count) { i ->
    DictationRowUi(i.toLong() + 1, AppDisplay("com.whatsapp", name, null), System.currentTimeMillis() - i * 3_600_000L, 10 + i)
}

@Preview
@Composable
private fun HistoryEmptyPreview() {
    PestoTheme { HistoryScreen(HistoryState(loading = false), onOpenDictation = {}, onLoadMore = {}) }
}

@Preview
@Composable
private fun HistoryPreview() {
    PestoTheme { HistoryScreen(HistoryState(sampleRows("WhatsApp", 8), total = 8, loading = false), onOpenDictation = {}, onLoadMore = {}) }
}

@Preview
@Composable
private fun HistoryLongGreekPreview() {
    PestoTheme {
        HistoryScreen(
            HistoryState(sampleRows("Μια εφαρμογή με πολύ μεγάλο ελληνικό όνομα που δεν χωράει", 20), total = 250, loading = false),
            onOpenDictation = {},
            onLoadMore = {},
        )
    }
}
