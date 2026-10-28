package com.victorpolitakis.pesto.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.victorpolitakis.pesto.BuildConfig
import com.victorpolitakis.pesto.accessibility.AccessibilityInserter
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.backend.BackendConfig
import com.victorpolitakis.pesto.backend.OkHttpBackendClient
import com.victorpolitakis.pesto.data.AppProfileDao
import com.victorpolitakis.pesto.data.DictationDao
import com.victorpolitakis.pesto.data.DictationHistory
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.data.MIGRATION_1_2
import com.victorpolitakis.pesto.data.PestoDatabase
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.data.TermDao
import com.victorpolitakis.pesto.dictation.Delivery
import com.victorpolitakis.pesto.dictation.OutcomeDelivery
import com.victorpolitakis.pesto.insert.Inserter
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
    @Binds abstract fun backend(impl: OkHttpBackendClient): BackendClient

    @Binds abstract fun log(impl: AndroidLog): PestoLog

    @Binds abstract fun delivery(impl: OutcomeDelivery): Delivery

    @Binds abstract fun inserter(impl: AccessibilityInserter): Inserter

    @Binds abstract fun history(impl: HistoryRepository): DictationHistory

    // The only binding of the overlay method, for both build types (Δ12).
    @Binds abstract fun overlayMethod(impl: SettingsRepository): OverlayMethodProvider

    companion object {
        @Provides @Singleton @AppScope
        fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        @Provides @Singleton
        fun backendConfig() = BackendConfig(BuildConfig.BACKEND_URL, BuildConfig.APP_TOKEN)

        @Provides @Singleton
        fun http(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // a dictation may stay open for minutes
            .build()

        // Room opens the file on the first query, i.e. at the end of the first dictation.
        // Never destructive: a version without its migration fails, the dictations stay.
        @Provides @Singleton
        fun database(@ApplicationContext context: Context): PestoDatabase =
            Room.databaseBuilder(context, PestoDatabase::class.java, "pesto.db")
                .addMigrations(MIGRATION_1_2)
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
