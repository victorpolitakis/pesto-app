package com.victorpolitakis.pesto.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.SearchText
import com.victorpolitakis.pesto.data.Snippet
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.components.DictationRow
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.components.EmptyState
import com.victorpolitakis.pesto.ui.components.PestoTopBar
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/** One result: the row of the History, and the text around the words found. */
data class SearchHit(val row: DictationRowUi, val snippet: Snippet)

/** The results for [query] (the last one searched, a moment behind the field). */
data class SearchState(val query: String = "", val hits: List<SearchHit> = emptyList())

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onOpenDictation: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    // The field's text stays here, in step with the keyboard; the search follows it.
    var query by rememberSaveable { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        query = query,
        state = state,
        onQueryChange = {
            query = it
            viewModel.search(it)
        },
        onBack = onBack,
        onOpenDictation = onOpenDictation,
        modifier = modifier,
    )
}

/**
 * Search (C8): the field at the top, with the keyboard open, and the results
 * as rows of the History, with the words found in bold. Every word is a prefix,
 * accents and case do not matter (Δ9), at least 2 letters.
 * TODO(phase 6): layout and design.
 */
@Composable
fun SearchScreen(
    query: String,
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenDictation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    Column(modifier.fillMaxSize().testTag("screen_search")) {
        PestoTopBar(stringResource(R.string.title_search), onBack)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focus)
                .testTag("search_field"),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(painterResource(AppIcons.Search), contentDescription = null) },
            singleLine = true,
        )
        when {
            query.isBlank() -> Unit
            SearchText.ftsQuery(query) == null -> Hint(stringResource(R.string.search_too_short))
            // Still the results of an earlier query: wait for this one.
            state.query != query -> Unit
            state.hits.isEmpty() -> EmptyState(stringResource(R.string.search_none))
            else -> {
                Hint(pluralStringResource(R.plurals.results, state.hits.size, state.hits.size), Modifier.testTag("search_count"))
                LazyColumn(Modifier.fillMaxSize().testTag("search_results")) {
                    items(state.hits, key = { it.row.id }) { hit ->
                        DictationRow(hit.row, onClick = { onOpenDictation(hit.row.id) }, snippet = hit.snippet)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(16.dp),
    )
}

private fun hits(name: String, text: String, count: Int) = List(count) { i ->
    SearchHit(
        DictationRowUi(i.toLong() + 1, AppDisplay("com.whatsapp", name, null), System.currentTimeMillis() - i * 86_400_000L, 20),
        Snippet(text, listOf(0..7)),
    )
}

@Preview
@Composable
private fun SearchEmptyPreview() {
    PestoTheme { SearchScreen("", SearchState(), onQueryChange = {}, onBack = {}, onOpenDictation = {}) }
}

@Preview
@Composable
private fun SearchPreview() {
    PestoTheme {
        SearchScreen(
            "καλημ",
            SearchState("καλημ", hits("WhatsApp", "Καλημέρα Γιώργο, το meeting πάει για την Πέμπτη.", 3)),
            onQueryChange = {},
            onBack = {},
            onOpenDictation = {},
        )
    }
}

@Preview
@Composable
private fun SearchLongGreekPreview() {
    PestoTheme {
        SearchScreen(
            "καλημέρα σε όλους",
            SearchState("καλημέρα σε όλους", hits("Εφαρμογή με πολύ μεγάλο ελληνικό όνομα", "Καλημέρα σε όλους, ".repeat(12), 12)),
            onQueryChange = {},
            onBack = {},
            onOpenDictation = {},
        )
    }
}
