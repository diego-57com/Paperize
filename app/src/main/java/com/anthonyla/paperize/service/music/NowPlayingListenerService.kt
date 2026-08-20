package com.anthonyla.paperize.service.music

import android.app.WallpaperManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.util.blurBitmap
import com.anthonyla.paperize.core.util.darkenBitmap
import com.anthonyla.paperize.core.util.grayscaleBitmap
import com.anthonyla.paperize.core.util.setBitmapChecked
import com.anthonyla.paperize.core.util.vignetteBitmap
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Detects the currently playing media session and, if the source app is in the user's
 * allow-list, sets the track's album art as the device wallpaper. Every captured cover is
 * also saved to the [MusicArtworkGalleryRepository] gallery. If playback stops for longer
 * than [STOP_GRACE_PERIOD_MS] and "revert to default" is enabled, falls back to the user's
 * chosen default wallpaper (from the gallery or a device photo).
 *
 * Requires "Notification access" granted to Paperize in system settings, which is what
 * allows [MediaSessionManager.getActiveSessions] to be called at all. This service never
 * reads notification text/content, only active media sessions.
 *
 * IMPORTANT: wallpapers set here are only visible while Paperize's wallpaper mode is STATIC.
 * A Live wallpaper is a separate rendering engine bound as the system wallpaper, so calls to
 * [WallpaperManager.setBitmap] have no visible effect while Live mode is active - this is
 * checked before applying anything below.
 */
@AndroidEntryPoint
class NowPlayingListenerService : NotificationListenerService() {

    @Inject
    lateinit var preferences: MusicSourcePreferences

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var galleryRepository: MusicArtworkGalleryRepository

    // Callback registration must happen on a thread with a Looper (Main is simplest).
    // Actual preference checks + wallpaper/gallery I/O are dispatched to IO below.
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var lastAppliedKey: String? = null
    private var stopWatchJob: Job? = null

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            rebindControllers(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        val componentName = ComponentName(this, NowPlayingListenerService::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            rebindControllers(manager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read active media sessions yet", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        clearControllers()
    }

    override fun onDestroy() {
        clearControllers()
        mainScope.cancel()
        super.onDestroy()
    }

    private fun rebindControllers(controllers: List<MediaController>) {
        clearControllers()
        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    handleMetadata(controller.packageName, metadata)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    handlePlaybackState(controller.packageName, state)
                }
            }
            try {
                controller.registerCallback(callback)
                controllerCallbacks[controller] = callback
                handleMetadata(controller.packageName, controller.metadata)
                handlePlaybackState(controller.packageName, controller.playbackState)
            } catch (e: Exception) {
                Log.w(TAG, "Could not register callback for ${controller.packageName}", e)
            }
        }
    }

    private fun clearControllers() {
        controllerCallbacks.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {
                // Session may already be gone; safe to ignore.
            }
        }
        controllerCallbacks.clear()
        stopWatchJob?.cancel()
        stopWatchJob = null
    }

    private fun handleMetadata(packageName: String, metadata: MediaMetadata?) {
        if (metadata == null) return
        mainScope.launch(Dispatchers.IO) {
            if (!preferences.getIsEnabledSnapshot()) return@launch
            if (packageName !in preferences.getAllowedPackagesSnapshot()) return@launch

            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val trackKey = "$packageName|$title|$artist"
            if (trackKey == lastAppliedKey) return@launch

            val rawArt: Bitmap? = resolveArtwork(metadata)
            if (rawArt == null) {
                Log.d(TAG, "No artwork (embedded or via URI) for $packageName - $title")
                return@launch
            }

            // A new track is playing, so cancel any pending "revert to default" countdown.
            stopWatchJob?.cancel()
            stopWatchJob = null

            galleryRepository.saveCapture(rawArt, title, artist, packageName)

            if (applyToSystemWallpaper(rawArt)) {
                lastAppliedKey = trackKey
                Log.d(TAG, "Wallpaper updated from $packageName: $title")
            }
        }
    }

    private fun handlePlaybackState(packageName: String, state: PlaybackState?) {
        mainScope.launch(Dispatchers.IO) {
            if (!preferences.getIsEnabledSnapshot()) return@launch
            if (packageName !in preferences.getAllowedPackagesSnapshot()) return@launch

            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                stopWatchJob?.cancel()
                stopWatchJob = null
                return@launch
            }

            // Not playing (paused/stopped/none/buffering-out): start a grace-period countdown
            // before reverting, so brief pauses (e.g. skipping to the next track) don't flicker.
            stopWatchJob?.cancel()
            stopWatchJob = mainScope.launch(Dispatchers.IO) {
                delay(STOP_GRACE_PERIOD_MS)
                if (!preferences.getRevertToDefaultOnStopSnapshot()) return@launch
                applyDefaultWallpaper()
            }
        }
    }

    private suspend fun applyDefaultWallpaper() {
        val path = preferences.getDefaultWallpaperPathSnapshot() ?: return
        val bitmap = try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        } ?: return

        if (applyToSystemWallpaper(bitmap)) {
            lastAppliedKey = "__default__"
            Log.d(TAG, "Reverted to default wallpaper after playback stopped")
        }
    }

    /**
     * Applies [source] to both home and lock wallpaper, each processed with whatever
     * blur/darken/vignette/grayscale the user already configured for that screen in
     * Paperize's normal Effects settings. Returns false (no-op) if Paperize's wallpaper
     * mode isn't STATIC, since a Live wallpaper would hide the result anyway.
     */
    private suspend fun applyToSystemWallpaper(source: Bitmap): Boolean {
        return try {
            val mode = try {
                settingsRepository.getWallpaperMode()
            } catch (e: Exception) {
                null
            }
            if (mode != WallpaperMode.STATIC) {
                Log.d(TAG, "Skipping: wallpaper mode is $mode, not STATIC")
                return false
            }

            val schedule = try {
                settingsRepository.getScheduleSettings()
            } catch (e: Exception) {
                null
            }

            val wallpaperManager = WallpaperManager.getInstance(applicationContext)

            val homeArt = applyEffects(source, schedule?.homeEffects ?: WallpaperEffects.none())
            wallpaperManager.setBitmapChecked(homeArt, WallpaperManager.FLAG_SYSTEM)

            val lockArt = applyEffects(source, schedule?.lockEffects ?: WallpaperEffects.none())
            wallpaperManager.setBitmapChecked(lockArt, WallpaperManager.FLAG_LOCK)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set wallpaper", e)
            false
        }
    }

    private fun applyEffects(source: Bitmap, effects: WallpaperEffects): Bitmap {
        var result = source
        if (effects.enableGrayscale && effects.grayscalePercentage > 0) {
            result = grayscaleBitmap(result, effects.grayscalePercentage)
        }
        if (effects.enableBlur && effects.blurPercentage > 0) {
            result = blurBitmap(result, effects.blurPercentage)
        }
        if (effects.enableVignette && effects.vignettePercentage > 0) {
            result = vignetteBitmap(result, effects.vignettePercentage)
        }
        if (effects.enableDarken && effects.darkenPercentage > 0) {
            result = darkenBitmap(result, effects.darkenPercentage)
        }
        return result
    }

    /**
     * Many apps (Spotify included) don't embed a [Bitmap] directly in [MediaMetadata] - they
     * only provide a content:// / http(s):// URI and expect the reader to fetch the image
     * itself. Tries the direct embedded bitmap first, then falls back to whichever art URI
     * key is present.
     */
    private fun resolveArtwork(metadata: MediaMetadata): Bitmap? {
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }

        val uriString = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: return null

        return try {
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "http" || uri.scheme == "https") {
                java.net.URL(uriString).openStream().use { BitmapFactory.decodeStream(it) }
            } else {
                applicationContext.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load artwork from URI: $uriString", e)
            null
        }
    }

    companion object {
        private const val TAG = "NowPlayingListener"
        private const val STOP_GRACE_PERIOD_MS = 15_000L
    }
}
