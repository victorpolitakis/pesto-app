package com.victorpolitakis.pesto.local

import com.victorpolitakis.pesto.data.SettingsRepository
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Experimental, off by default (PLAN_offline-mode.md §9): the offline model loads when
 * the button shows, so a short dictation does not wait for the 3–5 s of the load. Only
 * if the dictation would run offline. Costs ~1 GB while the button is there. The heavy
 * pieces are [Lazy]: with the setting off, the idle service creates nothing.
 */
@Singleton
class OfflinePreload @Inject constructor(
    private val settings: SettingsRepository,
    private val router: Lazy<RoutingBackendClient>,
    private val transcriber: Lazy<Transcriber>,
) {
    private var preloading = false

    fun buttonShown() {
        if (preloading || !settings.settings.value.preloadOffline || !router.get().wouldLoadModel()) return
        preloading = true
        transcriber.get().prepare()
    }

    /** The tap hides it too: the dictation's own prepare() then keeps the same load. */
    fun buttonHidden() {
        if (!preloading) return
        preloading = false
        transcriber.get().release()
    }
}
