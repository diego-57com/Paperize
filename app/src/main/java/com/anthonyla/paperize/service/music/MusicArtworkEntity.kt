package com.anthonyla.paperize.service.music

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single captured album cover, stored in its own database completely separate from
 * [com.anthonyla.paperize.data.database.PaperizeDatabase]. Kept independent on purpose:
 * the main database uses fallbackToDestructiveMigration, so adding a table there would
 * risk wiping the user's existing albums/wallpapers on the next schema change.
 *
 * [id] is a stable hash of "artist|title" (see [MusicArtworkGalleryRepository]), so the
 * same song played again updates [lastCapturedAt] instead of creating a duplicate row.
 */
@Entity(tableName = "music_artwork")
data class MusicArtworkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val sourcePackage: String,
    val imagePath: String,
    val firstCapturedAt: Long,
    val lastCapturedAt: Long
)
