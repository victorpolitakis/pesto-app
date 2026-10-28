package com.victorpolitakis.pesto.ui.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.Term
import com.victorpolitakis.pesto.data.TermResult
import com.victorpolitakis.pesto.protocol.MAX_TERMS_CHARS
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.components.EmptyState
import com.victorpolitakis.pesto.ui.components.ScreenTitle
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import kotlinx.coroutines.launch

/** The dialog of one term: a new one ([id] null) or one of the list. [error]: why the last save failed. */
data class TermEditor(val id: Long? = null, val text: String = "", val error: TermResult? = null)

/** The terms in order, and how many characters are left of the backend's limit (C2). */
data class DictionaryState(
    val terms: List<Term> = emptyList(),
    val remaining: Int = MAX_TERMS_CHARS,
    val editor: TermEditor? = null,
    val loading: Boolean = true,
) {
    /** Room for one more term, of one letter. */
    val canAdd: Boolean get() = remaining >= DictionaryRepository.cost("a")

    /** The characters left once [editor] is saved, without the old text of the term it edits. Negative: it does not fit. */
    fun leftAfter(editor: TermEditor): Int {
        val old = terms.firstOrNull { it.id == editor.id }?.let { DictionaryRepository.cost(it.term) } ?: 0
        val text = DictionaryRepository.clean(editor.text)
        return remaining + old - if (text.isEmpty()) 0 else DictionaryRepository.cost(text)
    }
}

/** [offerUndo]: a term was deleted; what it gets adds it back (the snackbar of the app). */
@Composable
fun DictionaryRoute(
    offerUndo: (undo: suspend () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DictionaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    DictionaryScreen(
        state,
        onAdd = viewModel::add,
        onEdit = viewModel::edit,
        onDelete = { term -> scope.launch { offerUndo(viewModel.delete(term)) } },
        onType = viewModel::type,
        onSave = viewModel::save,
        onDismiss = viewModel::dismiss,
        modifier = modifier,
    )
}

/**
 * Dictionary (C2): the terms that the speech recognition gets with every
 * dictation, so that names and rare words come out right. A term opens its
 * dialog, the bin deletes it (with «Αναίρεση»). What is left of the backend's
 * 10.000 characters is always on screen, and no save goes past it.
 * TODO(phase 6): layout and design.
 */
@Composable
fun DictionaryScreen(
    state: DictionaryState,
    onAdd: () -> Unit,
    onEdit: (Term) -> Unit,
    onDelete: (Term) -> Unit,
    onType: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("screen_dictionary")) {
        ScreenTitle(stringResource(R.string.title_dictionary))
        if (!state.loading) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(R.plurals.terms, state.terms.size, state.terms.size),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.testTag("term_count"),
                    )
                    Text(
                        stringResource(R.string.dictionary_remaining, state.remaining.coerceAtLeast(0), MAX_TERMS_CHARS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("term_remaining"),
                    )
                }
                Button(onClick = onAdd, enabled = state.canAdd, modifier = Modifier.testTag("add_term")) {
                    Icon(painterResource(AppIcons.Add), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.term_new))
                }
            }
            if (!state.canAdd) {
                Text(
                    stringResource(R.string.dictionary_full),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp).testTag("dictionary_full"),
                )
            }
            if (state.terms.isEmpty()) {
                EmptyState(stringResource(R.string.dictionary_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize().testTag("term_list")) {
                    items(state.terms, key = { it.id }) { term ->
                        TermRow(term, onEdit = { onEdit(term) }, onDelete = { onDelete(term) })
                    }
                }
            }
        }
        state.editor?.let { editor -> TermDialog(editor, state.leftAfter(editor), onType, onSave, onDismiss) }
    }
}

@Composable
private fun TermRow(term: Term, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(term.term) },
        modifier = modifier
            .clickable(onClickLabel = stringResource(R.string.term_edit), onClick = onEdit)
            .testTag("term_${term.id}"),
        trailingContent = {
            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_term_${term.id}")) {
                Icon(painterResource(AppIcons.Delete), contentDescription = stringResource(R.string.cd_delete_term, term.term))
            }
        },
    )
}

/**
 * Add or change one term. As the user types: how many characters are left,
 * or by how much the term goes past the limit (and no save). Without
 * autocorrect, which «corrects» exactly the names a Dictionary is for.
 */
@Composable
private fun TermDialog(
    editor: TermEditor,
    left: Int,
    onType: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fits = left >= 0
    val canSave = editor.text.isNotBlank() && fits
    val message = when {
        editor.error == TermResult.DUPLICATE -> stringResource(R.string.term_duplicate)
        editor.error == TermResult.EMPTY -> stringResource(R.string.term_empty)
        !fits -> pluralStringResource(R.plurals.term_over, -left, -left)
        // Terms added from elsewhere meanwhile.
        editor.error == TermResult.TOO_LONG -> stringResource(R.string.term_too_long)
        else -> pluralStringResource(R.plurals.term_left, left, left)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave, modifier = Modifier.testTag("term_save")) {
                Text(stringResource(R.string.save))
            }
        },
        modifier = modifier.testTag("term_dialog"),
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("term_cancel")) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(if (editor.id == null) R.string.term_new else R.string.term_edit)) },
        text = {
            // Inside the dialog's own window, where the field is.
            val focus = remember { FocusRequester() }
            LaunchedEffect(focus) { focus.requestFocus() }
            OutlinedTextField(
                value = editor.text,
                onValueChange = onType,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("term_field"),
                label = { Text(stringResource(R.string.term_label)) },
                supportingText = { Text(message, modifier = Modifier.testTag("term_message")) },
                isError = editor.error != null || !fits,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onSave() }),
                singleLine = true,
            )
        },
    )
}

private val sampleTerms = listOf("Pesto", "Soniox", "Πολιτάκης", "IBM App Connect", "Κηφισιά").mapIndexed { i, t -> Term(i + 1L, t) }

@Composable
private fun ScreenPreview(state: DictionaryState) {
    PestoTheme { DictionaryScreen(state, onAdd = {}, onEdit = {}, onDelete = {}, onType = {}, onSave = {}, onDismiss = {}) }
}

@Preview
@Composable
private fun DictionaryEmptyPreview() {
    ScreenPreview(DictionaryState(loading = false))
}

@Preview
@Composable
private fun DictionaryPreview() {
    ScreenPreview(DictionaryState(sampleTerms, remaining = DictionaryRepository.remaining(sampleTerms.map { it.term }), loading = false))
}

@Preview
@Composable
private fun DictionaryDialogPreview() {
    ScreenPreview(
        DictionaryState(
            sampleTerms,
            remaining = DictionaryRepository.remaining(sampleTerms.map { it.term }),
            editor = TermEditor(text = "πολιτακης", error = TermResult.DUPLICATE),
            loading = false,
        ),
    )
}

@Preview
@Composable
private fun DictionaryLongGreekPreview() {
    val terms = List(40) { i -> Term(i + 1L, "Ένας πολύ μεγάλος ελληνικός όρος με πολλές λέξεις, αριθμός ${i + 1}") }
    ScreenPreview(DictionaryState(terms, remaining = 1, loading = false))
}
