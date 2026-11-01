package com.victorpolitakis.pesto.ui.assistant

import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.DictationItem
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.StatsRepository
import com.victorpolitakis.pesto.protocol.AppCountSummary
import com.victorpolitakis.pesto.protocol.AssistantToolName
import com.victorpolitakis.pesto.protocol.DaySummary
import com.victorpolitakis.pesto.protocol.DictionarySummary
import com.victorpolitakis.pesto.protocol.HistoryEntrySummary
import com.victorpolitakis.pesto.protocol.ProfilesSummary
import com.victorpolitakis.pesto.protocol.StatsSummary
import com.victorpolitakis.pesto.protocol.ToolCall
import com.victorpolitakis.pesto.protocol.ToolResults
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.components.toRow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Bounded read-only tool payload for Step 2 (`answer`), plus local UI citations. */
data class ExecutedTools(
    val results: ToolResults,
    val citations: List<DictationRowUi>,
)

/**
 * Executes the read-only local tools requested by `POST /v1/assistant` (`plan`)
 * over the phone's Repositories, enforcing strict item and character caps.
 * Dictation database IDs stay on the phone as [DictationRowUi] citations.
 */
@Singleton
class AssistantTools(
    private val stats: StatsRepository,
    private val history: HistoryRepository,
    private val dictionary: DictionaryRepository,
    private val profiles: ProfilesRepository,
    private val apps: AppInfo,
    private val zone: () -> ZoneId,
) {
    @Inject constructor(
        stats: StatsRepository,
        history: HistoryRepository,
        dictionary: DictionaryRepository,
        profiles: ProfilesRepository,
        apps: AppInfo,
    ) : this(stats, history, dictionary, profiles, apps, ZoneId::systemDefault)

    suspend fun execute(calls: List<ToolCall>): ExecutedTools {
        var statsOut: StatsSummary? = null
        var matchesOut: List<HistoryEntrySummary>? = null
        var recentOut: List<HistoryEntrySummary>? = null
        var dictionaryOut: DictionarySummary? = null
        var profilesOut: ProfilesSummary? = null
        val citations = linkedMapOf<Long, DictationRowUi>()

        for (call in calls.take(MAX_TOOLS)) {
            when (call.tool) {
                AssistantToolName.GET_STATS -> statsOut = readStats()
                AssistantToolName.SEARCH_HISTORY -> {
                    val query = call.query?.trim().orEmpty()
                    val items = if (query.isEmpty()) {
                        history.list(MAX_HISTORY).first()
                    } else {
                        history.search(query, MAX_HISTORY).first().map { it.item }
                    }
                    val (summaries, rows) = summarizeItems(items)
                    matchesOut = summaries
                    rows.forEach { if (citations.size < MAX_HISTORY) citations.putIfAbsent(it.id, it) }
                }
                AssistantToolName.RECENT_HISTORY -> {
                    val limit = (call.limit ?: MAX_HISTORY).coerceIn(1, MAX_HISTORY)
                    val items = history.list(limit).first()
                    val (summaries, rows) = summarizeItems(items)
                    recentOut = summaries
                    rows.forEach { if (citations.size < MAX_HISTORY) citations.putIfAbsent(it.id, it) }
                }
                AssistantToolName.GET_DICTIONARY -> dictionaryOut = readDictionary()
                AssistantToolName.GET_PROFILES -> profilesOut = readProfiles()
            }
        }

        return ExecutedTools(
            results = ToolResults(
                stats = statsOut,
                historyMatches = matchesOut,
                recentHistory = recentOut,
                dictionary = dictionaryOut,
                profiles = profilesOut,
            ),
            citations = citations.values.toList(),
        )
    }

    private suspend fun readStats(): StatsSummary {
        val totals = stats.totals().first()
        val days = stats.perDay(WEEK_DAYS).first().map {
            DaySummary(date = it.day.toString(), dictations = it.dictations, words = it.words)
        }
        val topApps = stats.apps().first().take(MAX_TOP_APPS).map { usage ->
            AppCountSummary(app = apps.of(usage.packageName).name.take(MAX_APP_CHARS), dictations = usage.dictations)
        }
        val byProfile = stats.byProfile().first()
            .mapNotNull { kc -> kc.value?.take(MAX_ID_CHARS)?.let { it to kc.dictations } }
            .take(10)
            .toMap()
        return StatsSummary(
            dictations = totals.dictations,
            dictationsToday = totals.dictationsToday,
            words = totals.words,
            wordsToday = totals.wordsToday,
            wordsThisWeek = totals.wordsThisWeek,
            speechMinutes = totals.speechMinutes,
            minutesSaved = totals.minutesSaved,
            wpm = totals.wpm,
            days = days,
            topApps = topApps,
            byProfile = byProfile,
        )
    }

    private suspend fun summarizeItems(
        items: List<DictationItem>,
    ): Pair<List<HistoryEntrySummary>, List<DictationRowUi>> {
        val summaries = mutableListOf<HistoryEntrySummary>()
        val rows = mutableListOf<DictationRowUi>()
        val z = zone()
        for (item in items.take(MAX_HISTORY)) {
            val detail = history.get(item.id) ?: continue
            val row = item.toRow(apps)
            rows += row
            val stamp = TIMESTAMP.format(Instant.ofEpochMilli(detail.createdAt).atZone(z))
            summaries += HistoryEntrySummary(
                createdAt = stamp,
                app = row.app.name.take(MAX_APP_CHARS),
                profile = detail.profile.take(MAX_ID_CHARS),
                words = item.words,
                text = detail.text.take(MAX_TEXT_CHARS),
            )
        }
        return summaries to rows
    }

    private suspend fun readDictionary(): DictionarySummary {
        val all = dictionary.terms.first().map { it.term }
        return DictionarySummary(
            total = all.size,
            remainingChars = DictionaryRepository.remaining(all),
            terms = all.take(MAX_TERMS).map { it.take(MAX_TERM_CHARS) },
        )
    }

    private suspend fun readProfiles(): ProfilesSummary {
        val defaultProfile = profiles.defaultProfile.first().take(MAX_ID_CHARS)
        val assigned = linkedMapOf<String, String>()
        for ((pkg, profileId) in profiles.appProfiles.first().entries.take(MAX_APP_PROFILES)) {
            assigned[apps.of(pkg).name.take(MAX_APP_CHARS)] = profileId.take(MAX_ID_CHARS)
        }
        return ProfilesSummary(
            available = profiles.catalog.map { it.id },
            defaultProfile = defaultProfile,
            appProfiles = assigned,
        )
    }

    companion object {
        const val MAX_TOOLS = 3
        const val MAX_HISTORY = 5
        const val MAX_TOP_APPS = 5
        const val MAX_TERMS = 50
        const val MAX_APP_PROFILES = 20
        const val MAX_TEXT_CHARS = 400
        const val MAX_TERM_CHARS = 80
        const val MAX_APP_CHARS = 80
        const val MAX_ID_CHARS = 32
        private const val WEEK_DAYS = 7
        private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
