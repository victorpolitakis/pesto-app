package com.victorpolitakis.pesto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DictationDao {
    @Insert suspend fun insert(entity: DictationEntity): Long

    @Query("SELECT COUNT(*) FROM dictations") suspend fun count(): Int
}
