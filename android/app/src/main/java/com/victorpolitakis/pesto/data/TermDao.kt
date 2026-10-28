package com.victorpolitakis.pesto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TermDao {
    /** In the order of [TermEntity.termKey]: alphabetical, without accents. */
    @Query("SELECT * FROM terms ORDER BY term_key") fun all(): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms") suspend fun list(): List<TermEntity>

    @Insert suspend fun insert(term: TermEntity): Long

    @Update suspend fun update(term: TermEntity)

    @Query("DELETE FROM terms WHERE id = :id") suspend fun delete(id: Long)
}
