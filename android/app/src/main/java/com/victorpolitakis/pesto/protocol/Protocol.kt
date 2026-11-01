package com.victorpolitakis.pesto.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    @SerialName("get_stats") GET_STATS,
    @SerialName("search_history") SEARCH_HISTORY,
    @SerialName("recent_history") RECENT_HISTORY,
    @SerialName("get_dictionary") GET_DICTIONARY,
    @SerialName("get_profiles") GET_PROFILES,
}

@Serializable
data class NavigationTarget(
    val destination: AssistantDestination,
    @SerialName("profile_id") val profileId: String? = null,
)

@Serializable
data class ToolCall(
    val tool: AssistantToolName,
    val query: String? = null,
    val limit: Int? = null,
)

@Serializable
data class AssistantTurn(
    val role: String,
    val text: String,
)

@Serializable
data class DaySummary(
    val date: String,
    val dictations: Int,
    val words: Int,
)

@Serializable
data class AppCountSummary(
    val app: String,
    val dictations: Int,
)

@Serializable
data class StatsSummary(
    val dictations: Int = 0,
    @SerialName("dictations_today") val dictationsToday: Int = 0,
    val words: Int = 0,
    @SerialName("words_today") val wordsToday: Int = 0,
    @SerialName("words_this_week") val wordsThisWeek: Int = 0,
    @SerialName("speech_minutes") val speechMinutes: Double = 0.0,
    @SerialName("minutes_saved") val minutesSaved: Double = 0.0,
    val wpm: Double? = null,
    val days: List<DaySummary> = emptyList(),
    @SerialName("top_apps") val topApps: List<AppCountSummary> = emptyList(),
    @SerialName("by_profile") val byProfile: Map<String, Int> = emptyMap(),
)

@Serializable
data class HistoryEntrySummary(
    @SerialName("created_at") val createdAt: String,
    val app: String,
    val profile: String,
    val words: Int = 0,
    val text: String,
)

@Serializable
data class DictionarySummary(
    val total: Int = 0,
    @SerialName("remaining_chars") val remainingChars: Int = 0,
    val terms: List<String> = emptyList(),
)

@Serializable
data class ProfilesSummary(
    val available: List<String> = emptyList(),
    @SerialName("default_profile") val defaultProfile: String,
    @SerialName("app_profiles") val appProfiles: Map<String, String> = emptyMap(),
)

@Serializable
data class ToolResults(
    val stats: StatsSummary? = null,
    @SerialName("history_matches") val historyMatches: List<HistoryEntrySummary>? = null,
    @SerialName("recent_history") val recentHistory: List<HistoryEntrySummary>? = null,
    val dictionary: DictionarySummary? = null,
    val profiles: ProfilesSummary? = null,
)

@Serializable
sealed interface AssistantRequest

@Serializable
@SerialName("plan")
data class AssistantPlanRequest(
    val locale: String = "el",
    val query: String,
    val context: List<AssistantTurn> = emptyList(),
) : AssistantRequest

@Serializable
@SerialName("answer")
data class AssistantAnswerRequest(
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
    @SerialName("fallback_reason") val fallbackReason: String? = null,
) : AssistantResponse

@Serializable
@SerialName("tool_calls")
data class AssistantToolCalls(
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
