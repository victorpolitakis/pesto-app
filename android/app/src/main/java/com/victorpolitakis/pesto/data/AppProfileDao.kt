package com.victorpolitakis.pesto.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppProfileDao {
    @Query("SELECT * FROM app_profiles") fun all(): Flow<List<AppProfileEntity>>

    @Upsert suspend fun set(entry: AppProfileEntity)

    @Query("DELETE FROM app_profiles WHERE `package` = :packageName") suspend fun remove(packageName: String)
}
