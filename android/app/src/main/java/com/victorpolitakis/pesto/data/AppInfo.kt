package com.victorpolitakis.pesto.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.victorpolitakis.pesto.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** What the lists show for an app. [icon] is null for an app that is not installed, or not visible to Pesto. */
data class AppDisplay(val packageName: String?, val name: String, val icon: Bitmap?)

/**
 * Package → name and icon of an app, from the PackageManager, cached. Since
 * Android 11 Pesto sees only the apps of the manifest's <queries>: the ones
 * with a launcher icon. Without one (or uninstalled): the package and no icon.
 * The icons are loaded off the main thread, small, so the lists stay light.
 */
@Singleton
class AppInfo @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val cache = ConcurrentHashMap<String, AppDisplay>()
    private val iconPx by lazy { context.resources.getDimensionPixelSize(R.dimen.app_icon) }

    /** [packageName] null: a dictation without a field (no app). */
    suspend fun of(packageName: String?): AppDisplay {
        if (packageName == null) return AppDisplay(null, context.getString(R.string.app_none), null)
        cache[packageName]?.let { return it }
        return withContext(Dispatchers.IO) { load(packageName) }.also { cache[packageName] = it }
    }

    private fun load(packageName: String): AppDisplay {
        val pm = context.packageManager
        return try {
            val info = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            AppDisplay(packageName, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toBitmap(iconPx, iconPx))
        } catch (e: PackageManager.NameNotFoundException) {
            AppDisplay(packageName, packageName, null)
        }
    }
}
