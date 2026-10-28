package com.victorpolitakis.pesto.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.Term
import com.victorpolitakis.pesto.data.TermResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The terms and the dialog of one term. The repository checks every save; the dialog says why one failed. */
@HiltViewModel
class DictionaryViewModel @Inject constructor(private val dictionary: DictionaryRepository) : ViewModel() {
    private val editor = MutableStateFlow<TermEditor?>(null)

    val state: StateFlow<DictionaryState> = combine(dictionary.terms, editor) { terms, editor ->
        DictionaryState(terms, DictionaryRepository.remaining(terms.map { it.term }), editor, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DictionaryState())

    fun add() {
        editor.value = TermEditor()
    }

    fun edit(term: Term) {
        editor.value = TermEditor(term.id, term.term)
    }

    fun type(text: String) {
        editor.update { it?.copy(text = text, error = null) }
    }

    fun dismiss() {
        editor.value = null
    }

    fun save() {
        val current = editor.value ?: return
        viewModelScope.launch {
            val result = if (current.id == null) dictionary.add(current.text) else dictionary.update(current.id, current.text)
            if (result == TermResult.OK) editor.value = null else editor.update { it?.copy(error = result) }
        }
    }

    /** Deletes [term]. What it returns adds it back: the undo of the snackbar, as in History (Δ15). */
    suspend fun delete(term: Term): suspend () -> Unit {
        dictionary.delete(term.id)
        return { dictionary.add(term.term) }
    }
}
