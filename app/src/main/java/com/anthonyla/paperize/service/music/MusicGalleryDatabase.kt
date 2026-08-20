package com.anthonyla.paperize.service.music

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Standalone database (own file, own version history) for the music wallpaper gallery.
 * Deliberately not part of [com.anthonyla.paperize.data.database.PaperizeDatabase] - see
 * [MusicArtworkEntity] for why.
 */
@Database(entities = [MusicArtworkEntity::class], version = 1, exportSchema = false)
abstract class MusicGalleryDatabase : RoomDatabase() {
    abstract fun musicArtworkDao(): MusicArtworkDao
}
