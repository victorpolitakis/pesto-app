package com.victorpolitakis.pesto.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * v2 (phase 3): the search index, the Dictionary terms and a profile per app.
 * The schemas are exported to app/schemas: every version change needs a
 * migration (Migrations.kt) and its test, never a destructive one.
 */
@Database(
    entities = [DictationEntity::class, DictationFts::class, TermEntity::class, AppProfileEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PestoDatabase : RoomDatabase() {
    abstract fun dictations(): DictationDao

    abstract fun terms(): TermDao

    abstract fun appProfiles(): AppProfileDao
}
