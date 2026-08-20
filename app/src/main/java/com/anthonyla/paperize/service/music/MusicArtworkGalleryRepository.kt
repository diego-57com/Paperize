package com.anthonyla.paperize.service.music

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GalleryArtwork(
    val id: String,
    val title: String,
    val artist: String,
    val sourcePackage: String,
    val imagePath: String,
    val lastCapturedAt: Long
)

/**
 * Persists every captured "now playing" album cover to internal storage and keeps an
 * index of them in [MusicGalleryDatabase], so past covers can be browsed and picked as
 * a default wallpaper at any time - independent of whatever song is currently playing.
 */
@Singleton
class MusicArtworkGalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MusicArtworkDao
) {
    private val galleryDir: File by lazy {
        File(context.filesDir, "music_wallpaper_gallery").apply { mkdirs() }
    }

    val gallery: Flow<List<GalleryArtwork>> = dao.getAllFlow().map { rows ->
        rows.map {
            GalleryArtwork(
                id = it.id,
                title = it.title,
                artist = it.artist,
                sourcePackage = it.sourcePackage,
                imagePath = it.imagePath,
                lastCapturedAt = it.lastCapturedAt
            )
        }
    }

    /** Saves [bitmap] to internal storage and upserts its gallery entry. Call from an IO dispatcher. */
    suspend fun saveCapture(bitmap: Bitmap, title: String?, artist: String?, sourcePackage: String): String {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: "Unknown title"
        val safeArtist = artist?.takeIf { it.isNotBlank() } ?: "Unknown artist"
        val id = stableId(safeArtist, safeTitle)
        val file = File(galleryDir, "$id.jpg")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        val now = System.currentTimeMillis()
        val existing = dao.getById(id)
        dao.upsert(
            MusicArtworkEntity(
                id = id,
                title = safeTitle,
                artist = safeArtist,
                sourcePackage = sourcePackage,
                imagePath = file.absolutePath,
                firstCapturedAt = existing?.firstCapturedAt ?: now,
                lastCapturedAt = now
            )
        )
        return file.absolutePath
    }

    /** Call from an IO dispatcher. */
    suspend fun deleteEntry(id: String) {
        dao.getById(id)?.let { entry -> File(entry.imagePath).delete() }
        dao.deleteById(id)
    }

    private fun stableId(artist: String, title: String): String {
        val normalized = "${artist.trim().lowercase()}|${title.trim().lowercase()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
