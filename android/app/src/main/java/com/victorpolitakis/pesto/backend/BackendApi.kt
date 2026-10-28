package com.victorpolitakis.pesto.backend

import com.victorpolitakis.pesto.protocol.PairRequest
import com.victorpolitakis.pesto.protocol.PairResponse
import com.victorpolitakis.pesto.protocol.protocolJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How a pairing ended (Δ24). */
sealed interface PairResult {
    data class Paired(val token: String) : PairResult {
        override fun toString() = "Paired"
    }

    /** Wrong, used or expired code, or no active one: the backend does not say which. */
    data object WrongCode : PairResult

    /** No answer, or an unexpected one. [reason] is for the log: an HTTP code or an exception name. */
    data class Failed(val reason: String) : PairResult
}

/** The HTTP side of the backend: the pairing, and whether it answers (Settings). */
interface BackendApi {
    suspend fun health(): Boolean

    suspend fun pair(code: String, deviceName: String): PairResult
}

@Singleton
class OkHttpBackendApi @Inject constructor(http: OkHttpClient, config: BackendConfig) : BackendApi {
    // The WebSocket client never times out a read (a dictation lasts minutes); these calls do.
    private val http = http.newBuilder().readTimeout(5, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
    private val dictate = httpUrl(config.url)

    override suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(dictate.resolve("../health")!!).build()).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun pair(code: String, deviceName: String): PairResult = withContext(Dispatchers.IO) {
        val body = protocolJson.encodeToString(PairRequest.serializer(), PairRequest(code, deviceName)).toRequestBody(JSON)
        val request = Request.Builder().url(dictate.resolve("pair")!!).post(body).build()
        try {
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val text = response.body?.string().orEmpty()
                        PairResult.Paired(protocolJson.decodeFromString(PairResponse.serializer(), text).token)
                    }
                    403 -> PairResult.WrongCode
                    else -> PairResult.Failed("http:${response.code}")
                }
            }
        } catch (e: IOException) {
            PairResult.Failed(e.javaClass.simpleName)
        } catch (e: SerializationException) {
            PairResult.Failed("bad_response")
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/**
 * The http(s) URL of the dictation, from its ws(s) URL in BuildConfig. The other
 * routes are relative to it («pair», «../health»), so a path prefix stays.
 */
fun httpUrl(wsUrl: String): HttpUrl = when {
    wsUrl.startsWith("ws:", ignoreCase = true) -> "http:" + wsUrl.substring(3)
    wsUrl.startsWith("wss:", ignoreCase = true) -> "https:" + wsUrl.substring(4)
    else -> wsUrl
}.toHttpUrl()
