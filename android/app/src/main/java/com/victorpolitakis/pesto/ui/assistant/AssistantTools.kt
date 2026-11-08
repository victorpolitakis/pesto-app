package com.victorpolitakis.pesto.ui.assistant

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.DictationItem
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.SearchResult
import com.victorpolitakis.pesto.data.SearchText
import com.victorpolitakis.pesto.data.StatsRepository
import com.victorpolitakis.pesto.protocol.AssistantStatMetric
import com.victorpolitakis.pesto.protocol.AssistantTimePeriod
import com.victorpolitakis.pesto.protocol.AssistantToolName
import com.victorpolitakis.pesto.protocol.DictionarySummary
import com.victorpolitakis.pesto.protocol.HistoryEntrySummary
import com.victorpolitakis.pesto.protocol.ToolCall
import com.victorpolitakis.pesto.protocol.ToolResults
import com.victorpolitakis.pesto.protocol.cleanAssistantKeywords
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.components.toRow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Bounded read-only tool payload for Step 2 (`answer`), plus local UI citations. */
data class ExecutedTools(
    val results: ToolResults,
    val citations: List<DictationRowUi>,
)

@Singleton
class AssistantTools(
    private val context: Context,
    private val stats: StatsRepository,
    private val history: HistoryRepository,
    private val dictionary: DictionaryRepository,
    private val profiles: ProfilesRepository,
    private val apps: AppInfo,
    private val zone: () -> ZoneId,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        stats: StatsRepository,
        history: HistoryRepository,
        dictionary: DictionaryRepository,
        profiles: ProfilesRepository,
        apps: AppInfo,
    ) : this(context, stats, history, dictionary, profiles, apps, ZoneId::systemDefault)

    suspend fun execute(calls: List<ToolCall>): ExecutedTools {
        val bounded = calls.take(MAX_TOOLS)
        val seenIds = linkedSetOf<Long>()
        var historyMatches: List<HistoryEntrySummary>? = null
        var recentHistory: List<HistoryEntrySummary>? = null
        var dictionarySummary: DictionarySummary? = null
        val matchCitations = mutableListOf<DictationRowUi>()
        val recentCitations = mutableListOf<DictationRowUi>()

        // Execute SEARCH_HISTORY first so citation indices 0..M-1 align with backend's <history_matches>.
        for (call in bounded.filter { it.tool == AssistantToolName.SEARCH_HISTORY }) {
            val kws = cleanAssistantKeywords(call.keywords)
            val period = call.period ?: AssistantTimePeriod.ALL
            val since = stats.sinceEpochMillis(period)
            val budget = (MAX_TOTAL_SNIPPETS - seenIds.size).coerceAtLeast(0)
            if (kws.isEmpty() || budget == 0) {
                if (historyMatches == null) historyMatches = emptyList()
                continue
            }
            val combined = history.searchSince(kws.joinToString(" "), since, budget).first()
            val hits = if (combined.isNotEmpty() || kws.size == 1) {
                combined
            } else {
                fallbackPerKeyword(kws, since, budget)
            }
            val summaries = mutableListOf<HistoryEntrySummary>()
            for (hit in hits) {
                if (seenIds.size >= MAX_TOTAL_SNIPPETS) break
                if (!seenIds.add(hit.item.id)) continue
                val detail = history.get(hit.item.id)
                val appLabel = apps.of(hit.item.packageName).name.take(MAX_APP_CHARS)
                val profileId = (detail?.profile ?: ProfilesCatalog.RAW).take(MAX_ID_CHARS)
                summaries += HistoryEntrySummary(
                    createdAt = formatTime(hit.item.createdAt),
                    app = appLabel,
                    profile = profileId,
                    words = hit.item.words.coerceAtLeast(0),
                    snippet = sanitizeSnippet(hit.snippet.text),
                )
                matchCitations += hit.item.toRow(apps)
            }
            historyMatches = historyMatches.orEmpty() + summaries
        }

        // Execute RECENT_HISTORY next so citation indices M..M+R-1 align with backend's <recent_history>.
        for (call in bounded.filter { it.tool == AssistantToolName.RECENT_HISTORY }) {
            val requested = (call.limit ?: MAX_TOTAL_SNIPPETS).coerceIn(1, MAX_TOTAL_SNIPPETS)
            val budget = (MAX_TOTAL_SNIPPETS - seenIds.size).coerceAtMost(requested).coerceAtLeast(0)
            if (budget == 0) {
                if (recentHistory == null) recentHistory = emptyList()
                continue
            }
            val recentItems = history.list(limit = MAX_TOTAL_SNIPPETS * 2).first()
            val summaries = mutableListOf<HistoryEntrySummary>()
            for (item in recentItems) {
                if (summaries.size >= budget || seenIds.size >= MAX_TOTAL_SNIPPETS) break
                if (!seenIds.add(item.id)) continue
                summaries += item.toRecentSummary()
                recentCitations += item.toRow(apps)
            }
            recentHistory = recentHistory.orEmpty() + summaries
        }

        for (call in bounded.filter { it.tool == AssistantToolName.SEARCH_DICTIONARY }) {
            val allTerms = dictionary.terms.first().map { it.term }
            val normalizedKws = cleanAssistantKeywords(call.keywords)
                .map { SearchText.normalize(it) }
                .filter { it.isNotEmpty() }
            val matched = if (normalizedKws.isEmpty()) {
                emptyList()
            } else {
                allTerms.filter { term ->
                    val norm = SearchText.normalize(term)
                    normalizedKws.any { kw -> kw in norm }
                }.take(MAX_DICTIONARY_MATCHES).map { it.take(MAX_TERM_CHARS) }
            }
            dictionarySummary = DictionarySummary(
                total = allTerms.size,
                remainingChars = DictionaryRepository.remaining(allTerms).coerceAtLeast(0),
                terms = matched,
            )
        }

        return ExecutedTools(
            results = ToolResults(
                historyMatches = historyMatches,
                recentHistory = recentHistory,
                dictionary = dictionarySummary,
            ),
            citations = matchCitations + recentCitations,
        )
    }

    suspend fun formatLocalReply(call: ToolCall, locale: String): String {
        val res = localizedResources(locale)
        return when (call.tool) {
            AssistantToolName.STAT -> formatStatReply(call, res)
            AssistantToolName.GET_PROFILES -> formatProfilesReply(res)
            else -> ""
        }
    }

    private suspend fun fallbackPerKeyword(
        keywords: List<String>,
        since: Long,
        budget: Int,
    ): List<SearchResult> {
        val byId = linkedMapOf<Long, SearchResult>()
        for (kw in keywords) {
            for (hit in history.searchSince(kw, since, budget).first()) {
                byId.putIfAbsent(hit.item.id, hit)
            }
        }
        return byId.values
            .sortedWith(compareByDescending<SearchResult> { it.item.createdAt }.thenByDescending { it.item.id })
            .take(budget)
    }

    private suspend fun DictationItem.toRecentSummary(): HistoryEntrySummary {
        val detail = history.get(id)
        val rawSnippet = SearchText.snippet(detail?.text.orEmpty(), "", length = 150).text
        return HistoryEntrySummary(
            createdAt = formatTime(createdAt),
            app = apps.of(packageName).name.take(MAX_APP_CHARS),
            profile = (detail?.profile ?: ProfilesCatalog.RAW).take(MAX_ID_CHARS),
            words = words.coerceAtLeast(0),
            snippet = sanitizeSnippet(rawSnippet),
        )
    }

    private suspend fun formatStatReply(call: ToolCall, res: Resources): String {
        val metric = call.metric ?: AssistantStatMetric.MINUTES_SAVED
        val period = call.period ?: AssistantTimePeriod.ALL
        val periodLabel = when (period) {
            AssistantTimePeriod.TODAY -> res.getString(R.string.assistant_period_today)
            AssistantTimePeriod.WEEK -> res.getString(R.string.assistant_period_week)
            AssistantTimePeriod.ALL -> res.getString(R.string.assistant_period_all)
        }
        return when (metric) {
            AssistantStatMetric.DICTIONARY -> {
                val allTerms = dictionary.terms.first().map { it.term }
                if (allTerms.isEmpty()) {
                    res.getString(R.string.assistant_stat_dictionary_empty)
                } else {
                    val countText = res.getQuantityString(R.plurals.terms, allTerms.size, allTerms.size)
                    val remText = res.getString(
                        R.string.value_int,
                        DictionaryRepository.remaining(allTerms).coerceAtLeast(0),
                    )
                    res.getString(R.string.assistant_stat_dictionary, countText, remText)
                }
            }
            AssistantStatMetric.TOP_APP -> {
                val top = stats.topApps(period, limit = 3)
                if (top.isEmpty()) {
                    res.getString(R.string.assistant_stat_top_app_none, periodLabel)
                } else {
                    val items = top.map { u ->
                        val label = apps.of(u.packageName).name.take(MAX_APP_CHARS)
                        val uses = res.getQuantityString(R.plurals.dictations, u.dictations, u.dictations)
                        "$label ($uses)"
                    }
                    res.getString(R.string.assistant_stat_top_app, periodLabel, items.joinToString(", "))
                }
            }
            AssistantStatMetric.WPM -> {
                val snap = stats.snapshot(period)
                val wpm = snap.wpm?.roundToInt()
                if (wpm == null) {
                    res.getString(R.string.assistant_stat_wpm_none, periodLabel)
                } else {
                    res.getString(R.string.assistant_stat_wpm, periodLabel, res.getString(R.string.value_int, wpm))
                }
            }
            AssistantStatMetric.MINUTES_SAVED,
            AssistantStatMetric.WORDS,
            AssistantStatMetric.DICTATIONS,
            -> {
                val snap = stats.snapshot(period)
                if (snap.dictations == 0) {
                    res.getString(R.string.assistant_stat_none, periodLabel)
                } else {
                    val dictationsText = res.getQuantityString(R.plurals.dictations, snap.dictations, snap.dictations)
                    val wordsText = res.getQuantityString(R.plurals.words, snap.words, snap.words)
                    when (metric) {
                        AssistantStatMetric.MINUTES_SAVED -> {
                            val minText = res.getString(R.string.value_decimal, snap.minutesSaved)
                            res.getString(R.string.assistant_stat_minutes_saved, periodLabel, minText, dictationsText)
                        }
                        AssistantStatMetric.WORDS ->
                            res.getString(R.string.assistant_stat_words, periodLabel, wordsText, dictationsText)
                        else ->
                            res.getString(R.string.assistant_stat_dictations, periodLabel, dictationsText, wordsText)
                    }
                }
            }
        }
    }

    private suspend fun formatProfilesReply(res: Resources): String {
        val defaultId = profiles.defaultProfile.first()
        val defaultName = profileDisplayName(defaultId, res)
        val entries = profiles.appProfiles.first().entries.take(MAX_APP_PROFILES)
        if (entries.isEmpty()) {
            return res.getString(R.string.assistant_profiles_default_only, defaultName)
        }
        val pairs = entries.map { (pkg, profId) ->
            val appName = apps.of(pkg).name.take(MAX_APP_CHARS)
            val profName = profileDisplayName(profId, res)
            "$appName → $profName"
        }
        return res.getString(R.string.assistant_profiles_with_apps, defaultName, pairs.joinToString(", "))
    }

    private fun profileDisplayName(profileId: String, res: Resources): String {
        val nameRes = ProfilesCatalog.find(profileId)?.name
        return if (nameRes != null) res.getString(nameRes) else profileId.take(MAX_ID_CHARS)
    }

    private fun localizedResources(locale: String): Resources {
        val tag = if (locale.startsWith("el", ignoreCase = true)) "el" else "en"
        val loc = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration).apply { setLocale(loc) }
        return context.createConfigurationContext(config).resources
    }

    private fun formatTime(epochMillis: Long): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zone())).take(MAX_ID_CHARS)

    companion object {
        const val MAX_TOOLS = 3
        const val MAX_TOTAL_SNIPPETS = 3
        const val MAX_SNIPPET_CHARS = 160
        const val MAX_DICTIONARY_MATCHES = 5
        const val MAX_TERM_CHARS = 100
        const val MAX_APP_PROFILES = 20
        const val MAX_APP_CHARS = 80
        const val MAX_ID_CHARS = 32

        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

        /**
         * Strips unpaired UTF-16 surrogates across the entire string (since [SearchText.snippet]
         * slices with `take`/`substring` around `…`) and truncates to [maxChars] without splitting
         * a surrogate pair at the boundary.
         */
        fun sanitizeSnippet(raw: String, maxChars: Int = MAX_SNIPPET_CHARS): String {
            val scrubbed = buildString(raw.length) {
                var i = 0
                while (i < raw.length) {
                    val c = raw[i]
                    if (c.isHighSurrogate()) {
                        if (i + 1 < raw.length && raw[i + 1].isLowSurrogate()) {
                            append(c)
                            append(raw[i + 1])
                            i += 2
                        } else {
                            i += 1
                        }
                    } else if (c.isLowSurrogate()) {
                        i += 1
                    } else {
                        append(c)
                        i += 1
                    }
                }
            }
            if (scrubbed.length <= maxChars) return scrubbed
            val end = if (maxChars > 0 && scrubbed[maxChars - 1].isHighSurrogate()) maxChars - 1 else maxChars
            return scrubbed.substring(0, end)
        }
    }
}
