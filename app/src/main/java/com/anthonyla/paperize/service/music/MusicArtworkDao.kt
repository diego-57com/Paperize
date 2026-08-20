package com.anthonyla.paperize.service.music

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicArtworkDao {

    @Query("SELECT * FROM music_artwork ORDER BY lastCapturedAt DESC")
    fun getAllFlow(): Flow<List<MusicArtworkEntity>>

    @Query("SELECT * FROM music_artwork WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MusicArtworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MusicArtworkEntity)

    @Query("DELETE FROM music_artwork WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM music_artwork")
    suspend fun clearAll()
}
