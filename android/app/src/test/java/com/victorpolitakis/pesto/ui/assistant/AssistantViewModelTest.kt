package com.victorpolitakis.pesto.ui.assistant

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.FakeBackendApi
import com.victorpolitakis.pesto.FakeDeviceTokenStore
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.backend.AssistantApi
import com.victorpolitakis.pesto.backend.BackendConfig
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.AppProfileEntity
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.data.NOW
import com.victorpolitakis.pesto.data.PairingRepository
import com.victorpolitakis.pesto.data.PestoDatabase
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.data.StatsRepository
import com.victorpolitakis.pesto.data.inMemoryDatabase
import com.victorpolitakis.pesto.data.newDictation
import com.victorpolitakis.pesto.protocol.AssistantAnswerRequest
import com.victorpolitakis.pesto.protocol.AssistantDestination
import com.victorpolitakis.pesto.protocol.AssistantToolName
import com.victorpolitakis.pesto.protocol.NavigationTarget
import com.victorpolitakis.pesto.protocol.ToolCall
import com.victorpolitakis.pesto.protocol.protocolJson
import com.victorpolitakis.pesto.util.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AssistantViewModelTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var db: PestoDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var history: HistoryRepository
    private lateinit var stats: StatsRepository
    private lateinit var dictionary: DictionaryRepository
    private lateinit var profiles: ProfilesRepository
    private lateinit var tools: AssistantTools

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryDatabase()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val clock = WallClock { NOW }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = storeScope) { File(folder.root, "settings.preferences_pb") },
            storeScope,
        )
        history = HistoryRepository(db.dictations())
        stats = StatsRepository(db.dictations(), clock) { ZoneOffset.UTC }
        dictionary = DictionaryRepository(db.terms(), clock)
        profiles = ProfilesRepository(db.appProfiles(), settings)
        tools = AssistantTools(stats, history, dictionary, profiles, AppInfo(context)) { ZoneOffset.UTC }
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `AssistantTools enforces caps and collects local DictationRowUi citations`() = runBlocking {
        val longText = "Καλημέρα Γιώργο ".repeat(40)
        repeat(7) { i ->
            history.record(
                newDictation(
                    rawText = longText,
                    text = longText,
                    packageName = "com.whatsapp",
                    profile = "mail",
                    createdAt = NOW - i * 1_000L,
                ),
            )
        }
        dictionary.add("Πολιτάκης")
        db.appProfiles().set(AppProfileEntity("com.whatsapp", "chatter"))

        val out = tools.execute(
            listOf(
                ToolCall(AssistantToolName.GET_STATS),
                ToolCall(AssistantToolName.SEARCH_HISTORY, query = "Γιώργο"),
                ToolCall(AssistantToolName.GET_DICTIONARY),
            ),
        )
        assertEquals(7, out.results.stats!!.dictations)
        assertEquals(5, out.results.historyMatches!!.size)
        assertTrue(out.results.historyMatches!!.first().text.length <= AssistantTools.MAX_TEXT_CHARS)
        assertEquals(5, out.citations.size)
        assertEquals(listOf("Πολιτάκης"), out.results.dictionary!!.terms)
    }

    @Test
    fun `ViewModel executes two-step tool flow and attaches citations`() = runBlocking {
        history.record(newDictation("Συνάντηση την Πέμπτη στις δέκα", packageName = "com.google.android.gm", profile = "mail"))
        val requests = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val buf = Buffer()
            chain.request().body!!.writeTo(buf)
            val body = buf.readUtf8()
            requests += body
            val replyJson = if (requests.size == 1) {
                """{"type":"tool_calls","tools":[{"tool":"search_history","query":"Πέμπτη"}]}"""
            } else {
                """{"type":"reply","reply":"Η συνάντηση είναι την Πέμπτη στις 10."}"""
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(replyJson.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val tokens = FakeDeviceTokenStore("tok")
        val log = FakeLog()
        val api = AssistantApi(http, BackendConfig("ws://127.0.0.1:8000/v1/dictate"), tokens, log)
        val pairing = PairingRepository(FakeBackendApi(), tokens, { "Test Phone" }, log)
        val vm = AssistantViewModel(api, tools, pairing)

        // Start collecting StateFlow (`WhileSubscribed`) and wait for `loading == false`.
        val collector = CoroutineScope(Dispatchers.Unconfined)
        try {
            vm.state.first { !it.loading }
            vm.send("Τι είπα για την Πέμπτη;", "el")
            val done = vm.state.first { it.messages.size == 2 && !it.busy }
            assertEquals(true, done.messages[0].isUser)
            assertEquals("Η συνάντηση είναι την Πέμπτη στις 10.", done.messages[1].text)
            assertEquals(1, done.messages[1].citations.size)
            assertEquals(null, done.messages[1].navigate)

            val step2 = protocolJson.decodeFromString(AssistantAnswerRequest.serializer(), requests[1])
            assertEquals(1, step2.toolResults.historyMatches!!.size)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `ViewModel handles direct navigation reply in one step`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """{"type":"reply","reply":"Opening Dictionary.","navigate":{"destination":"DICTIONARY"}}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }.build()
        val tokens = FakeDeviceTokenStore("tok")
        val log = FakeLog()
        val api = AssistantApi(http, BackendConfig("ws://127.0.0.1:8000/v1/dictate"), tokens, log)
        val pairing = PairingRepository(FakeBackendApi(), tokens, { "Test Phone" }, log)
        val vm = AssistantViewModel(api, tools, pairing)

        vm.state.first { !it.loading }
        vm.send("Open dictionary", "en")
        val done = vm.state.first { it.messages.size == 2 && !it.busy }
        assertEquals(NavigationTarget(AssistantDestination.DICTIONARY), done.messages[1].navigate)
    }
}
