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

fun encode(message: ClientMessage): String = protocolJson.encodeToString(ClientMessage.serializer(), message)

fun decode(text: String): ServerMessage = protocolJson.decodeFromString(ServerMessage.serializer(), text)

const val SAMPLE_RATE = 16000

/** 100 ms of pcm_s16le mono, as recommended by docs/protocol.md. */
const val FRAME_BYTES = SAMPLE_RATE / 10 * 2

/** The backend's limit on [Start.terms]: Σ (characters of a term + 1). Above it, `bad_request`. */
const val MAX_TERMS_CHARS = 10_000
