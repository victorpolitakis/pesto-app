package com.victorpolitakis.pesto.di

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.victorpolitakis.pesto.BuildConfig
import com.victorpolitakis.pesto.accessibility.AccessibilityInserter
import com.victorpolitakis.pesto.backend.BackendApi
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.backend.BackendConfig
import com.victorpolitakis.pesto.backend.OkHttpBackendApi
import com.victorpolitakis.pesto.backend.OkHttpBackendClient
import com.victorpolitakis.pesto.data.AppProfileDao
import com.victorpolitakis.pesto.data.DeviceName
import com.victorpolitakis.pesto.data.DeviceTokenStore
import com.victorpolitakis.pesto.data.DictationDao
import com.victorpolitakis.pesto.data.DictationHistory
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.data.KeystoreTokenStore
import com.victorpolitakis.pesto.data.MIGRATION_1_2
import com.victorpolitakis.pesto.data.MIGRATION_2_3
import com.victorpolitakis.pesto.data.PestoDatabase
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.data.TermDao
import com.victorpolitakis.pesto.dictation.Delivery
import com.victorpolitakis.pesto.dictation.OutcomeDelivery
import com.victorpolitakis.pesto.insert.Inserter
import com.victorpolitakis.pesto.local.AndroidNetworkCheck
import com.victorpolitakis.pesto.local.AsrClient
import com.victorpolitakis.pesto.local.LocalBackendClient
import com.victorpolitakis.pesto.local.ModelFiles
import com.victorpolitakis.pesto.local.ModelStore
import com.victorpolitakis.pesto.local.NetworkCheck
import com.victorpolitakis.pesto.local.RoutingBackendClient
import com.victorpolitakis.pesto.local.Transcriber
import com.victorpolitakis.pesto.overlay.OverlayMethodProvider
import com.victorpolitakis.pesto.util.AndroidLog
import com.victorpolitakis.pesto.util.PestoLog
import com.victorpolitakis.pesto.util.WallClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Lives as long as the app process, on the main thread. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds abstract fun log(impl: AndroidLog): PestoLog

    @Binds abstract fun delivery(impl: OutcomeDelivery): Delivery

    @Binds abstract fun inserter(impl: AccessibilityInserter): Inserter

    @Binds abstract fun history(impl: HistoryRepository): DictationHistory

    // The only binding of the overlay method, for both build types (Δ12).
    @Binds abstract fun overlayMethod(impl: SettingsRepository): OverlayMethodProvider

    @Binds abstract fun tokens(impl: KeystoreTokenStore): DeviceTokenStore

    @Binds abstract fun api(impl: OkHttpBackendApi): BackendApi

    @Binds abstract fun transcriber(impl: AsrClient): Transcriber

    @Binds abstract fun modelFiles(impl: ModelStore): ModelFiles

    @Binds abstract fun network(impl: AndroidNetworkCheck): NetworkCheck

    companion object {
        @Provides @Singleton @AppScope
        fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        // Soniox through the backend, or the model on the phone (PLAN_offline-mode.md §4).
        @Provides @Singleton
        fun router(
            online: OkHttpBackendClient,
            local: LocalBackendClient,
            settings: SettingsRepository,
            network: NetworkCheck,
            log: PestoLog,
        ) = RoutingBackendClient(online, local, { settings.settings.value.offlineDefault }, network, log)

        @Provides fun backend(router: RoutingBackendClient): BackendClient = router

        // Only the URL: the token is the phone's own, from pairing (Δ24).
        @Provides @Singleton
        fun backendConfig() = BackendConfig(BuildConfig.BACKEND_URL)

        @Provides @Singleton
        fun http(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // a dictation may stay open for minutes
            .build()

        @Provides fun webSockets(http: OkHttpClient): WebSocket.Factory = http

        @Provides fun deviceName(@ApplicationContext context: Context) = DeviceName {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?: "${Build.MANUFACTURER} ${Build.MODEL}"
        }

        // Room opens the file on the first query, i.e. at the end of the first dictation.
        // Never destructive: a version without its migration fails, the dictations stay.
        @Provides @Singleton
        fun database(@ApplicationContext context: Context): PestoDatabase =
            Room.databaseBuilder(context, PestoDatabase::class.java, "pesto.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        @Provides fun dictationDao(db: PestoDatabase): DictationDao = db.dictations()

        @Provides fun termDao(db: PestoDatabase): TermDao = db.terms()

        @Provides fun appProfileDao(db: PestoDatabase): AppProfileDao = db.appProfiles()

        @Provides @Singleton
        fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

        @Provides fun wallClock() = WallClock(System::currentTimeMillis)
    }
}
