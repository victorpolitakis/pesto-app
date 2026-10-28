package com.victorpolitakis.pesto.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase

@Dao
interface DictationDao {
    @Insert suspend fun insert(entity: DictationEntity): Long

    @Query("SELECT COUNT(*) FROM dictations") suspend fun count(): Int
}

/** The schema is exported to app/schemas, so phase 3 can migrate without losing dictations. */
@Database(entities = [DictationEntity::class], version = 1, exportSchema = true)
abstract class PestoDatabase : RoomDatabase() {
    abstract fun dictations(): DictationDao
}
