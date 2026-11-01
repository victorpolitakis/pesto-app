package com.victorpolitakis.pesto.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.backend.BackendConnection
import com.victorpolitakis.pesto.util.PestoLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether the phone has a network with internet now. An interface for the JVM tests. */
fun interface NetworkCheck {
    fun hasInternet(): Boolean
}

class AndroidNetworkCheck @Inject constructor(@param:ApplicationContext private val context: Context) : NetworkCheck {
    /** Internet, not «validated»: a network the backend does not answer on stays `no_server`, as before. */
    override fun hasInternet(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Soniox or the model on the phone, at every tap (PLAN_offline-mode.md §4). Soniox is
 * the default and does not change. Offline: no network (always), or the setting «Offline
 * by default» with the model on the phone. Decided once per dictation: a network that
 * goes away in the middle is the ConnectionLost of today.
 */
class RoutingBackendClient(
    private val online: BackendClient,
    private val local: LocalBackendClient,
    private val offlineDefault: () -> Boolean,
    private val network: NetworkCheck,
    private val log: PestoLog,
) : BackendClient {
    override fun open(): BackendConnection {
        val route = offlineReason()
        log.event("route ${route?.let { "offline reason=$it" } ?: "online"}")
        return if (route != null) local.open() else online.open()
    }

    /** Why a dictation now would run offline, or null for Soniox. The experimental preload asks it too. */
    fun offlineReason(): String? = when {
        // Without the model too: it fails with model_missing, nothing else could work.
        !network.hasInternet() -> "no_network"
        // The setting before the file is read (first tap after the process starts) is the default: Soniox.
        offlineDefault() && local.isReady -> "setting"
        else -> null
    }

    /** A dictation now would run offline, with the model there to load. */
    fun wouldLoadModel() = offlineReason() != null && local.isReady
}
