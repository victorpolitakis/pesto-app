package com.victorpolitakis.pesto.dictation

import com.victorpolitakis.pesto.data.AppSettings
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.di.AppScope
import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.protocol.MAX_TERMS_CHARS
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.util.PestoLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the start message of every dictation, whatever started it: the
 * languages (Settings), all the Dictionary terms, and the profile (Δ5: the
 * app's, else the default, else raw). It runs while the microphone already
 * records. The first tap reads the stores; from then on they stay in memory
 * and follow every change, so the idle service has nothing to load before it.
 */
@Singleton
class StartBuilder @Inject constructor(
    private val settings: SettingsRepository,
    private val dictionary: DictionaryRepository,
    private val profiles: ProfilesRepository,
    private val log: PestoLog,
    @param:AppScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var inputs: StateFlow<Inputs>? = null

    /**
     * Never fails: with an unreadable store the dictation still starts, with what
     * phase 2 sent (Greek and English, no terms, raw).
     */
    suspend fun build(context: FieldContext): Start = try {
        val i = inputs()
        // TODO(X13): custom_profile and style go here, next to the id of the profile.
        // TODO(X2): the hint of the field for the prompt (third-party text: tags, spotlighting).
        // TODO(X6, X10): a tile or a gesture starts without a field, so no package: default, else raw.
        Start(
            languageHints = i.settings.languageHints,
            languageHintsStrict = i.settings.strictLanguages,
            terms = fit(i.terms),
            profile = ProfilesRepository.choose(context.packageName, i.appProfiles, i.settings.defaultProfile),
            fieldContext = context,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.event("start with defaults: ${e.javaClass.simpleName}")
        Start(fieldContext = context)
    }

    private suspend fun inputs(): Inputs = mutex.withLock {
        val cached = inputs ?: combine(settings.stored, dictionary.terms, profiles.appProfiles) { s, terms, apps ->
            Inputs(s, terms.map { it.term }, apps)
        }
            .catch { e ->
                log.event("start inputs failed: ${e.javaClass.simpleName}")
                emit(Inputs(AppSettings(), emptyList(), emptyMap()))
            }
            .stateIn(scope)
            .also { inputs = it }
        cached.value
    }

    private data class Inputs(val settings: AppSettings, val terms: List<String>, val appProfiles: Map<String, String>)

    companion object {
        /**
         * The terms that fit the backend's limit, in order. The Dictionary screen
         * never lets them grow past it: this is so a dictation never fails on them.
         */
        fun fit(terms: List<String>): List<String> {
            val kept = mutableListOf<String>()
            var size = 0
            for (term in terms) {
                size += DictionaryRepository.cost(term)
                if (size > MAX_TERMS_CHARS) break
                kept += term
            }
            return kept
        }
    }
}
