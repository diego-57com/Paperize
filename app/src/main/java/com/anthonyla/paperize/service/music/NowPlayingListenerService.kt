package com.anthonyla.paperize.service.music

import android.app.WallpaperManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log
import com.anthonyla.paperize.core.util.setBitmapChecked
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Detects the currently playing media session and, if the source app is in the
 * user's allow-list (configured in the Music Wallpaper settings screen), sets the
 * track's embedded album art as the device wallpaper.
 *
 * This requires the user to grant "Notification access" to Paperize in system settings.
 * That grant is what allows [MediaSessionManager.getActiveSessions] to be called at all;
 * this service never reads notification text/content, only active media sessions.
 */
@AndroidEntryPoint
class NowPlayingListenerService : NotificationListenerService() {

    @Inject
    lateinit var preferences: MusicSourcePreferences

    // Callback registration must happen on a thread with a Looper (Main is simplest).
    // The actual preference checks + wallpaper I/O are dispatched to IO inside handleMetadata.
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var lastAppliedKey: String? = null

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
            // Notification access was revoked or not yet fully propagated; nothing to do
            // until the listener reconnects.
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
            }
            try {
                controller.registerCallback(callback)
                controllerCallbacks[controller] = callback
                handleMetadata(controller.packageName, controller.metadata)
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
    }

    private fun handleMetadata(packageName: String, metadata: MediaMetadata?) {
        if (metadata == null) return
        mainScope.launch(Dispatchers.IO) {
            if (!preferences.getIsEnabledSnapshot()) return@launch
            val allowed = preferences.getAllowedPackagesSnapshot()
            if (packageName !in allowed) return@launch

            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val trackKey = "$packageName|$title|$artist"
            if (trackKey == lastAppliedKey) return@launch

            // Prefer ALBUM_ART; some apps (e.g. some podcast/video players) only set ART.
            val art: Bitmap? = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            if (art == null) {
                Log.d(TAG, "No embedded artwork for $packageName - $title")
                return@launch
            }

            try {
                val wallpaperManager = WallpaperManager.getInstance(applicationContext)
                // NOTE: unlike Paperize's own prepared bitmaps, this Bitmap is owned by the
                // media session (it may still be referenced by the system media notification),
                // so it is intentionally NOT recycled here.
                wallpaperManager.setBitmapChecked(
                    art,
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                )
                lastAppliedKey = trackKey
                Log.d(TAG, "Wallpaper updated from $packageName: $title")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set wallpaper from now-playing art", e)
            }
        }
    }

    companion object {
        private const val TAG = "NowPlayingListener"
    }
}
