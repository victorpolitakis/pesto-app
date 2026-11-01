package com.victorpolitakis.pesto.backend

import com.victorpolitakis.pesto.FakeDeviceTokenStore
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.protocol.AssistantDestination
import com.victorpolitakis.pesto.protocol.AssistantPlanRequest
import com.victorpolitakis.pesto.protocol.AssistantReply
import com.victorpolitakis.pesto.protocol.NavigationTarget
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AssistantApiTest {
    private val config = BackendConfig("ws://127.0.0.1:8000/v1/dictate")

    private fun api(
        tokens: FakeDeviceTokenStore = FakeDeviceTokenStore("secret-token"),
        log: FakeLog = FakeLog(),
        respond: (Request) -> Response,
    ): AssistantApi {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain -> respond(chain.request()) }
            .build()
        return AssistantApi(http, config, tokens, log)
    }

    private fun jsonResponse(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun `unpaired phone returns Unauthorized without calling the network`() = runTest {
        var called = false
        val client = api(tokens = FakeDeviceTokenStore(null)) {
            called = true
            error("unreachable")
        }
        val result = client.send(AssistantPlanRequest(locale = "el", query = "Γεια"))
        assertEquals(AssistantResult.Unauthorized, result)
        assertFalse(called)
    }

    @Test
    fun `sends Bearer token to v1 assistant and decodes reply`() = runTest {
        var seenUrl = ""
        var seenAuth = ""
        var seenBody = ""
        val client = api { req ->
            seenUrl = req.url.toString()
            seenAuth = req.header("Authorization").orEmpty()
            val buf = Buffer()
            req.body!!.writeTo(buf)
            seenBody = buf.readUtf8()
            jsonResponse(req, 200, """{"type":"reply","reply":"Ορίστε.","navigate":{"destination":"DICTIONARY"}}""")
        }
        val result = client.send(AssistantPlanRequest(locale = "el", query = "Λεξικό"))
        assertEquals("http://127.0.0.1:8000/v1/assistant", seenUrl)
        assertEquals("Bearer secret-token", seenAuth)
        assertTrue("\"type\":\"plan\"" in seenBody)
        assertEquals(
            AssistantResult.Ok(AssistantReply("Ορίστε.", NavigationTarget(AssistantDestination.DICTIONARY))),
            result,
        )
    }

    @Test
    fun `403 clears the device token and returns Unauthorized`() = runTest {
        val tokens = FakeDeviceTokenStore("revoked-token")
        val client = api(tokens = tokens) { req ->
            jsonResponse(req, 403, """{"detail":"unauthorized"}""")
        }
        val result = client.send(AssistantPlanRequest(locale = "en", query = "Hello"))
        assertEquals(AssistantResult.Unauthorized, result)
        assertFalse(tokens.isPaired())
    }

    @Test
    fun `network failure and bad JSON return Failed`() = runTest {
        val ioClient = api { throw IOException("offline") }
        assertEquals(
            AssistantResult.Failed("IOException"),
            ioClient.send(AssistantPlanRequest(locale = "en", query = "Hi")),
        )

        val badJsonClient = api { req -> jsonResponse(req, 200, "not-json") }
        assertEquals(
            AssistantResult.Failed("bad_response"),
            badJsonClient.send(AssistantPlanRequest(locale = "en", query = "Hi")),
        )
    }
}
