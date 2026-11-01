package com.victorpolitakis.pesto.backend

import com.victorpolitakis.pesto.data.DeviceTokenStore
import com.victorpolitakis.pesto.protocol.AssistantRequest
import com.victorpolitakis.pesto.protocol.AssistantResponse
import com.victorpolitakis.pesto.protocol.decodeAssistant
import com.victorpolitakis.pesto.protocol.encodeAssistant
import com.victorpolitakis.pesto.util.PestoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of one `POST /v1/assistant` step (`plan` or `answer`). */
sealed interface AssistantResult {
    data class Ok(val response: AssistantResponse) : AssistantResult

    /** Not paired, or the backend refused the device token (`HTTP 403`). */
    data object Unauthorized : AssistantResult

    /** Network failure, server error, or malformed JSON. */
    data class Failed(val reason: String) : AssistantResult
}

/**
 * Stateless HTTP client for X3 Assistant (`POST /v1/assistant`).
 * Cold-starting `claude -p` on the backend takes ~4–8s, so the read timeout is 20s.
 */
@Singleton
class AssistantApi @Inject constructor(
    http: OkHttpClient,
    config: BackendConfig,
    private val tokens: DeviceTokenStore,
    private val log: PestoLog,
) {
    private val http = http.newBuilder()
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val endpoint = httpUrl(config.url).resolve("assistant")!!

    suspend fun send(request: AssistantRequest): AssistantResult = withContext(Dispatchers.IO) {
        val token = tokens.current() ?: return@withContext AssistantResult.Unauthorized
        val body = encodeAssistant(request).toRequestBody(JSON)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        try {
            http.newCall(httpRequest).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val raw = response.body.string()
                        AssistantResult.Ok(decodeAssistant(raw))
                    }
                    403 -> {
                        tokens.clear()
                        log.event("assistant unauthorized: token cleared")
                        AssistantResult.Unauthorized
                    }
                    else -> {
                        log.event("assistant failed http:${response.code}")
                        AssistantResult.Failed("http:${response.code}")
                    }
                }
            }
        } catch (e: IOException) {
            log.event("assistant failed ${e.javaClass.simpleName}")
            AssistantResult.Failed(e.javaClass.simpleName)
        } catch (e: SerializationException) {
            log.event("assistant failed bad_response")
            AssistantResult.Failed("bad_response")
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
