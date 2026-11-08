package com.victorpolitakis.pesto.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/*
 * Messages between the phone and the backend. Kotlin copy of
 * backend/src/pesto_server/protocol.py, which is the source of truth: the
 * backend rejects unknown fields, so the names must match exactly.
 */

val protocolJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

@Serializable
data class FieldContext(
    @SerialName("package") val packageName: String? = null,
    @SerialName("input_type") val inputType: String? = null,
    val hint: String? = null,
    @SerialName("single_line") val singleLine: Boolean? = null,
    @SerialName("is_password") val isPassword: Boolean? = null,
    @SerialName("is_incognito") val isIncognito: Boolean? = null,
)

@Serializable
sealed interface ClientMessage

// TODO(later): neutral field names (Δ25): language_hints is Soniox's word. Here, in
// docs/protocol.md and in protocol.py together, with the tests of both sides.
@Serializable
@SerialName("start")
data class Start(
    @SerialName("language_hints") val languageHints: List<String> = listOf("el", "en"),
    @SerialName("language_hints_strict") val languageHintsStrict: Boolean = false,
    val terms: List<String> = emptyList(),
    val profile: String = "raw",
    @SerialName("field_context") val fieldContext: FieldContext = FieldContext(),
    @SerialName("sample_rate") val sampleRate: Int = 16000,
    val encoding: String = "pcm_s16le",
) : ClientMessage

@Serializable @SerialName("pause") data object Pause : ClientMessage
@Serializable @SerialName("resume") data object Resume : ClientMessage
@Serializable @SerialName("finalize") data object Finalize : ClientMessage
@Serializable @SerialName("cancel") data object Cancel : ClientMessage

@Serializable
sealed interface ServerMessage

@Serializable @SerialName("ready") data object Ready : ServerMessage

@Serializable
@SerialName("partial")
data class Partial(
    @SerialName("final_text") val finalText: String,
    @SerialName("pending_text") val pendingText: String,
) : ServerMessage

@Serializable
data class Stats(
    @SerialName("audio_s") val audioS: Double,
    val words: Int,
    @SerialName("stt_ms") val sttMs: Int,
    @SerialName("llm_ms") val llmMs: Int? = null,
    @SerialName("total_ms") val totalMs: Int,
)

@Serializable
@SerialName("final")
data class Final(
    @SerialName("raw_text") val rawText: String,
    val text: String,
    val profile: String,
    @SerialName("fallback_reason") val fallbackReason: String? = null,
    @SerialName("end_reason") val endReason: String,
    val stats: Stats,
) : ServerMessage

@Serializable
@SerialName("error")
data class Error(val code: String, val message: String, val retryable: Boolean) : ServerMessage

/** POST /v1/pair (Δ24): the one-time code of `pesto-server pair`, and a name for this phone. */
@Serializable
data class PairRequest(val code: String, @SerialName("device_name") val deviceName: String)

/** The token of this phone. Never logged, never in the APK. */
@Serializable
data class PairResponse(val token: String) {
    override fun toString() = "PairResponse(token=…)"
}

// --- POST /v1/assistant (X3) ---

@Serializable
enum class AssistantDestination {
    HOME,
    PROFILES,
    DICTIONARY,
    HISTORY,
    SEARCH,
    ANALYTICS,
    APPS,
    SETTINGS,
    SETTINGS_LANGUAGES,
    SETTINGS_PROFILE,
    SETTINGS_OFFLINE,
    SETTINGS_PERMISSIONS,
    SETTINGS_OVERLAY,
    SETTINGS_XIAOMI,
    SETTINGS_BACKEND,
    SETTINGS_PRIVACY,
    SETTINGS_ABOUT,
    PROFILE_DETAIL,
}

@Serializable
enum class AssistantToolName {
    @SerialName("stat") STAT,
    @SerialName("get_profiles") GET_PROFILES,
    @SerialName("search_history") SEARCH_HISTORY,
    @SerialName("recent_history") RECENT_HISTORY,
    @SerialName("search_dictionary") SEARCH_DICTIONARY,
}

@Serializable
enum class AssistantStatMetric {
    @SerialName("minutes_saved") MINUTES_SAVED,
    @SerialName("words") WORDS,
    @SerialName("dictations") DICTATIONS,
    @SerialName("wpm") WPM,
    @SerialName("top_app") TOP_APP,
    @SerialName("dictionary") DICTIONARY,
}

@Serializable
enum class AssistantTimePeriod {
    @SerialName("today") TODAY,
    @SerialName("week") WEEK,
    @SerialName("all") ALL,
}

private val FTS_OPERATORS = setOf("and", "or", "not", "near")

/** Mirrors `validate_keyword` in `protocol.py` (2..30 Unicode code points, alnum or internal `-`). */
fun isValidAssistantKeyword(raw: String): Boolean {
    val kw = raw.trim()
    val cpCount = kw.codePointCount(0, kw.length)
    if (cpCount !in 2..30) return false
    if (kw.lowercase(Locale.ROOT) in FTS_OPERATORS) return false
    if ("--" in kw) return false
    if (!Character.isLetterOrDigit(kw.codePointAt(0)) || !Character.isLetterOrDigit(kw.codePointBefore(kw.length))) {
        return false
    }
    var i = 0
    while (i < kw.length) {
        val cp = kw.codePointAt(i)
        if (!Character.isLetterOrDigit(cp) && cp != '-'.code) return false
        i += Character.charCount(cp)
    }
    return true
}

/** Trims, drops invalid FTS tokens, deduplicates in order, and caps at 3 keywords. */
fun cleanAssistantKeywords(raw: List<String>?): List<String> =
    raw.orEmpty().map { it.trim() }.filter(::isValidAssistantKeyword).distinct().take(3)

@Serializable
data class NavigationTarget(
    val destination: AssistantDestination,
    @SerialName("profile_id") val profileId: String? = null,
)

@Serializable
data class ToolCall(
    val tool: AssistantToolName,
    val keywords: List<String>? = null,
    val metric: AssistantStatMetric? = null,
    val period: AssistantTimePeriod? = null,
    val limit: Int? = null,
)

@Serializable
data class PreviousPlanState(
    val tool: AssistantToolName,
    val keywords: List<String> = emptyList(),
    val metric: AssistantStatMetric? = null,
    val period: AssistantTimePeriod? = null,
)

@Serializable
data class HistoryEntrySummary(
    @SerialName("created_at") val createdAt: String,
    val app: String,
    val profile: String,
    val words: Int = 0,
    val snippet: String,
)

@Serializable
data class DictionarySummary(
    val total: Int = 0,
    @SerialName("remaining_chars") val remainingChars: Int = 0,
    val terms: List<String> = emptyList(),
)

@Serializable
data class ToolResults(
    @SerialName("history_matches") val historyMatches: List<HistoryEntrySummary>? = null,
    @SerialName("recent_history") val recentHistory: List<HistoryEntrySummary>? = null,
    val dictionary: DictionarySummary? = null,
)

@Serializable
sealed interface AssistantRequest

@Serializable
@SerialName("plan")
data class AssistantPlanRequest(
    val locale: String = "el",
    val query: String,
    @SerialName("previous_queries") val previousQueries: List<String> = emptyList(),
    val previous: PreviousPlanState? = null,
) : AssistantRequest

@Serializable
@SerialName("answer")
data class AssistantAnswerRequest(
    @SerialName("plan_id") val planId: String,
    val locale: String = "el",
    val query: String,
    @SerialName("tool_results") val toolResults: ToolResults,
) : AssistantRequest

@Serializable
sealed interface AssistantResponse

@Serializable
@SerialName("reply")
data class AssistantReply(
    val reply: String,
    val navigate: NavigationTarget? = null,
    val relevant: List<Int> = emptyList(),
    @SerialName("fallback_reason") val fallbackReason: String? = null,
) : AssistantResponse

@Serializable
@SerialName("tool_calls")
data class AssistantToolCalls(
    @SerialName("plan_id") val planId: String? = null,
    val tools: List<ToolCall>,
) : AssistantResponse

fun encodeAssistant(request: AssistantRequest): String =
    protocolJson.encodeToString(AssistantRequest.serializer(), request)

fun decodeAssistant(text: String): AssistantResponse =
    protocolJson.decodeFromString(AssistantResponse.serializer(), text)

fun encode(message: ClientMessage): String = protocolJson.encodeToString(ClientMessage.serializer(), message)

fun decode(text: String): ServerMessage = protocolJson.decodeFromString(ServerMessage.serializer(), text)

const val SAMPLE_RATE = 16000

/** 100 ms of pcm_s16le mono, as recommended by docs/protocol.md. */
const val FRAME_BYTES = SAMPLE_RATE / 10 * 2

/** The backend's limit on [Start.terms]: Σ (characters of a term + 1). Above it, `bad_request`. */
const val MAX_TERMS_CHARS = 10_000
