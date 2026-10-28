package com.victorpolitakis.pesto.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.victorpolitakis.pesto.accessibility.EditorBridge
import com.victorpolitakis.pesto.accessibility.PestoAccessibilityService

/** What C3 asks for, in this order (PLAN_phase-2.md, section 4). */
enum class Requirement { MICROPHONE, ACCESSIBILITY, BATTERY, XIAOMI, OVERLAY }

/**
 * Pure: which requirement to ask for now. Only the first missing one, never
 * one already asked in this visit (no loop when the user comes back without
 * granting it); a new visit (the user left the app) asks again.
 */
class PermissionFlow(private val order: List<Requirement>) {
    private val asked = mutableSetOf<Requirement>()
    private var awaitingReturn = false

    /** Called on every resume with what is missing. Returns what to ask for, or null. */
    fun next(missing: Set<Requirement>): Requirement? {
        awaitingReturn = false
        val next = order.firstOrNull { it in missing && it !in asked } ?: return null
        asked += next
        awaitingReturn = true
        return next
    }

    /** The app went to the background on its own (not to a page we opened): the visit is over. */
    fun onStop() {
        if (!awaitingReturn) asked.clear()
    }
}

/** The Android checks. Everything granted is detected here, so it is never asked again. */
class RequirementChecks(private val context: Context, private val bridge: EditorBridge, private val withOverlay: Boolean) {
    val order: List<Requirement> =
        listOf(Requirement.MICROPHONE, Requirement.ACCESSIBILITY, Requirement.BATTERY) +
            (if (isXiaomi) listOf(Requirement.XIAOMI) else emptyList()) +
            (if (withOverlay) listOf(Requirement.OVERLAY) else emptyList())

    /** [xiaomiShown]: the Xiaomi page cannot be checked, so it counts as done once it was opened. */
    fun missing(xiaomiShown: Boolean): Set<Requirement> = order.filterNot { isGranted(it, xiaomiShown) }.toSet()

    fun isGranted(r: Requirement, xiaomiShown: Boolean): Boolean = when (r) {
        Requirement.MICROPHONE ->
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        // Enabled AND connected: on Xiaomi the toggle can say "On" while the service is frozen.
        Requirement.ACCESSIBILITY -> accessibilityEnabled() && bridge.isConnected
        Requirement.BATTERY ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        Requirement.XIAOMI -> xiaomiShown
        Requirement.OVERLAY -> Settings.canDrawOverlays(context)
    }

    /**
     * HyperOS kills the accessibility service when the app is swiped away from
     * recents (spike S6 on the POCO, 28/10): Autostart, battery "No restrictions"
     * and locking the app in recents are needed. POCO and Redmi report "Xiaomi".
     */
    private val isXiaomi get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    fun accessibilityEnabled(): Boolean {
        val ours = ComponentName(context, PestoAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }
}
