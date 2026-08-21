package com.anthonyla.paperize.presentation.screens.music_wallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.service.music.GalleryArtwork
import com.anthonyla.paperize.service.music.MusicArtworkGalleryRepository
import com.anthonyla.paperize.service.music.MusicSourcePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryUiState(
    val items: List<GalleryArtwork> = emptyList(),
    val defaultWallpaperPath: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class MusicGalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: MusicSourcePreferences,
    private val galleryRepository: MusicArtworkGalleryRepository
) : ViewModel() {

    val uiState: StateFlow<GalleryUiState> = combine(
        galleryRepository.gallery,
        preferences.defaultWallpaperPath
    ) { items, defaultPath ->
        GalleryUiState(items = items, defaultWallpaperPath = defaultPath, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = GalleryUiState()
    )

    fun setDefaultFromGallery(item: GalleryArtwork) {
        viewModelScope.launch { preferences.setDefaultWallpaperPath(item.imagePath) }
    }

    fun clearDefault() {
        viewModelScope.launch { preferences.setDefaultWallpaperPath(null) }
    }

    fun deleteFromGallery(item: GalleryArtwork) {
        viewModelScope.launch {
            val wasDefault = preferences.getDefaultWallpaperPathSnapshot() == item.imagePath
            withContext(Dispatchers.IO) { galleryRepository.deleteEntry(item.id) }
            if (wasDefault) preferences.setDefaultWallpaperPath(null)
        }
    }

    /** Copies a device photo picked via the system Photo Picker into internal storage and sets it as default. */
    fun setDefaultFromDeviceUri(uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                try {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: return@withContext null

                    val dir = File(context.filesDir, "music_wallpaper_default").apply { mkdirs() }
                    val file = File(dir, "custom_default.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    file.absolutePath
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy picked photo", e)
                    null
                }
            }
            if (savedPath != null) {
                preferences.setDefaultWallpaperPath(savedPath)
            }
        }
    }

    companion object {
        private const val TAG = "MusicGalleryViewModel"
    }
}
