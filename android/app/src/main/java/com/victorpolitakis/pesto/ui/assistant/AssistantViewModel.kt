package com.victorpolitakis.pesto.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.backend.AssistantApi
import com.victorpolitakis.pesto.backend.AssistantResult
import com.victorpolitakis.pesto.data.PairingRepository
import com.victorpolitakis.pesto.protocol.AssistantAnswerRequest
import com.victorpolitakis.pesto.protocol.AssistantPlanRequest
import com.victorpolitakis.pesto.protocol.AssistantReply
import com.victorpolitakis.pesto.protocol.AssistantToolCalls
import com.victorpolitakis.pesto.protocol.AssistantTurn
import com.victorpolitakis.pesto.protocol.NavigationTarget
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One bubble in the ephemeral Assistant session. */
data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String = "",
    val navigate: NavigationTarget? = null,
    val citations: List<DictationRowUi> = emptyList(),
    val isError: Boolean = false,
)

/** Everything `AssistantScreen` renders. Lists live inside this data class for `compose-lints`. */
data class AssistantState(
    val paired: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val api: AssistantApi,
    private val tools: AssistantTools,
    pairing: PairingRepository,
) : ViewModel() {
    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val busy = MutableStateFlow(false)
    private var nextId = 1L

    val state: StateFlow<AssistantState> = combine(pairing.paired, messages, busy) { isPaired, list, isBusy ->
        AssistantState(paired = isPaired, messages = list, busy = isBusy, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantState())

    fun send(rawQuery: String, languageCode: String) {
        val query = rawQuery.trim().take(MAX_QUERY_CHARS)
        if (query.isEmpty() || busy.value || !state.value.paired) return
        val locale = if (languageCode.startsWith("el", ignoreCase = true)) "el" else "en"
        val context = messages.value
            .filter { !it.isError && it.text.isNotBlank() }
            .takeLast(MAX_CONTEXT_TURNS)
            .map { AssistantTurn(role = if (it.isUser) "user" else "assistant", text = it.text.take(MAX_CONTEXT_CHARS)) }

        append(ChatMessage(id = nextId++, isUser = true, text = query))
        busy.value = true

        viewModelScope.launch {
            try {
                when (val plan = api.send(AssistantPlanRequest(locale = locale, query = query, context = context))) {
                    is AssistantResult.Unauthorized -> Unit // `paired` updates from DeviceTokenStore
                    is AssistantResult.Failed -> appendError()
                    is AssistantResult.Ok -> when (val res = plan.response) {
                        is AssistantReply -> append(
                            ChatMessage(id = nextId++, isUser = false, text = res.reply, navigate = res.navigate),
                        )
                        is AssistantToolCalls -> answerWithTools(locale, query, res)
                    }
                }
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun answerWithTools(locale: String, query: String, calls: AssistantToolCalls) {
        val executed = tools.execute(calls.tools)
        val step2 = AssistantAnswerRequest(locale = locale, query = query, toolResults = executed.results)
        when (val ans = api.send(step2)) {
            is AssistantResult.Unauthorized -> Unit
            is AssistantResult.Failed -> appendError()
            is AssistantResult.Ok -> when (val res = ans.response) {
                is AssistantReply -> append(
                    ChatMessage(
                        id = nextId++,
                        isUser = false,
                        text = res.reply,
                        navigate = null,
                        citations = executed.citations,
                    ),
                )
                is AssistantToolCalls -> appendError()
            }
        }
    }

    private fun appendError() {
        append(ChatMessage(id = nextId++, isUser = false, isError = true))
    }

    private fun append(message: ChatMessage) {
        messages.update { it + message }
    }

    companion object {
        const val MAX_QUERY_CHARS = 1_000
        private const val MAX_CONTEXT_TURNS = 4
        private const val MAX_CONTEXT_CHARS = 300
    }
}
