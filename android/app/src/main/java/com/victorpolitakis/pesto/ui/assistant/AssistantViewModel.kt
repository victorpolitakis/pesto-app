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
import com.victorpolitakis.pesto.protocol.AssistantToolName
import com.victorpolitakis.pesto.protocol.NavigationTarget
import com.victorpolitakis.pesto.protocol.PreviousPlanState
import com.victorpolitakis.pesto.protocol.cleanAssistantKeywords
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class AssistantErrorKind { NETWORK, EXPIRED, RATE_LIMITED }

/** One bubble in the ephemeral Assistant session. */
data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String = "",
    val navigate: NavigationTarget? = null,
    val citations: List<DictationRowUi> = emptyList(),
    val isError: Boolean = false,
    val errorKind: AssistantErrorKind? = null,
    val retryAfterS: Int = 0,
    val blockedByGuard: Boolean = false,
)

/** Everything `AssistantScreen` renders. Lists live inside this data class for `compose-lints`. */
data class AssistantState(
    val paired: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val cooldownSeconds: Int = 0,
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
    private val cooldown = MutableStateFlow(0)
    private var cooldownJob: Job? = null
    private var lastPlanState: PreviousPlanState? = null
    private var nextId = 1L

    val state: StateFlow<AssistantState> = combine(
        pairing.paired,
        messages,
        busy,
        cooldown,
    ) { isPaired, list, isBusy, cd ->
        AssistantState(paired = isPaired, messages = list, busy = isBusy, cooldownSeconds = cd, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantState())

    fun send(rawQuery: String, languageCode: String) {
        val query = rawQuery.trim().take(MAX_QUERY_CHARS)
        if (query.isEmpty() || busy.value || cooldown.value > 0 || !state.value.paired) return
        val locale = if (languageCode.startsWith("el", ignoreCase = true)) "el" else "en"
        val previousQueries = messages.value
            .filter { it.isUser && !it.blockedByGuard && it.text.isNotBlank() }
            .takeLast(MAX_PREVIOUS_QUERIES)
            .map { it.text.take(MAX_PREVIOUS_QUERY_CHARS) }
        val previousState = lastPlanState
        val userMessageId = nextId++
        append(ChatMessage(id = userMessageId, isUser = true, text = query))
        busy.value = true

        viewModelScope.launch {
            try {
                // Only a successful tool turn leaves a state for the next question: a plain
                // reply, an error or a fallback clears it, so a follow-up never binds to a stale tool.
                lastPlanState = runTurn(
                    userMessageId = userMessageId,
                    locale = locale,
                    query = query,
                    previousQueries = previousQueries,
                    previousState = previousState,
                    allowConflictRetry = true,
                )
            } finally {
                busy.value = false
            }
        }
    }

    /** Returns the state a follow-up may build on, or null when the turn did not end with a tool answer. */
    private suspend fun runTurn(
        userMessageId: Long,
        locale: String,
        query: String,
        previousQueries: List<String>,
        previousState: PreviousPlanState?,
        allowConflictRetry: Boolean,
    ): PreviousPlanState? {
        val planReq = AssistantPlanRequest(
            locale = locale,
            query = query,
            previousQueries = previousQueries,
            previous = previousState,
        )
        when (val plan = api.send(planReq)) {
            is AssistantResult.Unauthorized -> return null
            is AssistantResult.Conflict -> appendError(AssistantErrorKind.EXPIRED)
            is AssistantResult.RateLimited -> appendRateLimited(plan.retryAfterS)
            is AssistantResult.Failed -> appendError(AssistantErrorKind.NETWORK)
            is AssistantResult.Ok -> when (val res = plan.response) {
                is AssistantReply -> {
                    if (res.fallbackReason?.startsWith("guard:") == true) {
                        markUserMessageBlocked(userMessageId)
                    }
                    append(
                        ChatMessage(
                            id = nextId++,
                            isUser = false,
                            text = res.reply,
                            navigate = res.navigate,
                        ),
                    )
                }
                is AssistantToolCalls -> {
                    val firstTool = res.tools.firstOrNull()
                    if (firstTool == null) {
                        appendError(AssistantErrorKind.NETWORK)
                        return null
                    }
                    val state = PreviousPlanState(
                        tool = firstTool.tool,
                        keywords = cleanAssistantKeywords(firstTool.keywords),
                        metric = firstTool.metric,
                        period = firstTool.period,
                    )
                    if (res.planId == null || firstTool.tool in LOCAL_TOOLS) {
                        val localReply = tools.formatLocalReply(firstTool, locale)
                        append(ChatMessage(id = nextId++, isUser = false, text = localReply))
                        return state
                    }
                    return answerWithTools(
                        state = state,
                        userMessageId = userMessageId,
                        locale = locale,
                        query = query,
                        previousQueries = previousQueries,
                        previousState = previousState,
                        calls = res,
                        allowConflictRetry = allowConflictRetry,
                    )
                }
            }
        }
        return null
    }

    /** Returns [state] after a clean answer, the retried turn's state after a 409, else null. */
    private suspend fun answerWithTools(
        state: PreviousPlanState,
        userMessageId: Long,
        locale: String,
        query: String,
        previousQueries: List<String>,
        previousState: PreviousPlanState?,
        calls: AssistantToolCalls,
        allowConflictRetry: Boolean,
    ): PreviousPlanState? {
        val executed = tools.execute(calls.tools)
        val step2 = AssistantAnswerRequest(
            planId = calls.planId.orEmpty(),
            locale = locale,
            query = query,
            toolResults = executed.results,
        )
        var ans = api.send(step2)
        if (ans is AssistantResult.RateLimited) {
            // `answer` is never rate limited, so this is the busy 503: the backend kept the
            // plan_id, so one retry with the same results does not cost a new `plan`.
            val waitMs = ans.retryAfterS.coerceIn(1, MAX_BUSY_RETRY_S) * 1_000L
            withContext(Dispatchers.Default) { delay(waitMs) }
            ans = api.send(step2)
        }
        when (ans) {
            is AssistantResult.Unauthorized -> Unit
            is AssistantResult.Conflict -> if (allowConflictRetry) {
                return runTurn(
                    userMessageId = userMessageId,
                    locale = locale,
                    query = query,
                    previousQueries = previousQueries,
                    previousState = previousState,
                    allowConflictRetry = false,
                )
            } else {
                appendError(AssistantErrorKind.EXPIRED)
            }
            is AssistantResult.RateLimited -> appendRateLimited(ans.retryAfterS)
            is AssistantResult.Failed -> appendError(AssistantErrorKind.NETWORK)
            is AssistantResult.Ok -> when (val res = ans.response) {
                is AssistantReply -> {
                    if (res.fallbackReason?.startsWith("guard:") == true) {
                        markUserMessageBlocked(userMessageId)
                    }
                    val pickedCitations = if (res.fallbackReason != null) {
                        emptyList()
                    } else {
                        res.relevant.distinct()
                            .filter { it in executed.citations.indices }
                            .map { executed.citations[it] }
                    }
                    append(
                        ChatMessage(
                            id = nextId++,
                            isUser = false,
                            text = res.reply,
                            navigate = null,
                            citations = pickedCitations,
                        ),
                    )
                    return if (res.fallbackReason == null) state else null
                }
                is AssistantToolCalls -> appendError(AssistantErrorKind.NETWORK)
            }
        }
        return null
    }

    private fun markUserMessageBlocked(userMessageId: Long) {
        messages.update { list ->
            list.map { msg -> if (msg.id == userMessageId) msg.copy(blockedByGuard = true) else msg }
        }
    }

    private fun appendRateLimited(retryAfterS: Int) {
        val waitS = retryAfterS.coerceAtLeast(1)
        cooldown.value = waitS
        append(
            ChatMessage(
                id = nextId++,
                isUser = false,
                isError = true,
                errorKind = AssistantErrorKind.RATE_LIMITED,
                retryAfterS = waitS,
            ),
        )
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            while (cooldown.value > 0) {
                delay(1_000)
                cooldown.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    private fun appendError(kind: AssistantErrorKind = AssistantErrorKind.NETWORK) {
        append(ChatMessage(id = nextId++, isUser = false, isError = true, errorKind = kind))
    }

    private fun append(message: ChatMessage) {
        messages.update { it + message }
    }

    companion object {
        const val MAX_QUERY_CHARS = 1_000
        private const val MAX_PREVIOUS_QUERIES = 2
        private const val MAX_PREVIOUS_QUERY_CHARS = 300
        private const val MAX_BUSY_RETRY_S = 3
        private val LOCAL_TOOLS = setOf(AssistantToolName.STAT, AssistantToolName.GET_PROFILES)
    }
}
