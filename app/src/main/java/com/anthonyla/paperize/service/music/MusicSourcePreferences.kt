package com.anthonyla.paperize.service.music

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.musicWallpaperDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "music_wallpaper_preferences"
)

/**
 * Standalone preference store for the "Now Playing" music wallpaper add-on.
 *
 * Kept deliberately independent from [com.anthonyla.paperize.data.datastore.PreferencesManager]
 * (its own DataStore file) so this add-on feature never touches Paperize's existing
 * settings schema, album system, or scheduling logic.
 */
@Singleton
class MusicSourcePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.musicWallpaperDataStore

    /** Whether the now-playing wallpaper feature is turned on at all. */
    val isEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLED] ?: false
    }

    /** Package names of apps allowed to trigger a wallpaper change when they play music. */
    val allowedPackages: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_ALLOWED_PACKAGES] ?: emptySet()
    }

    suspend fun getIsEnabledSnapshot(): Boolean =
        dataStore.data.first()[KEY_ENABLED] ?: false

    suspend fun getAllowedPackagesSnapshot(): Set<String> =
        dataStore.data.first()[KEY_ALLOWED_PACKAGES] ?: emptySet()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
    }

    suspend fun setPackageAllowed(packageName: String, allowed: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_ALLOWED_PACKAGES] ?: emptySet()
            prefs[KEY_ALLOWED_PACKAGES] = if (allowed) {
                current + packageName
            } else {
                current - packageName
            }
        }
    }

    /** Absolute file path of the wallpaper to fall back to when music stops (or null = none set). */
    val defaultWallpaperPath: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_WALLPAPER_PATH]
    }

    suspend fun getDefaultWallpaperPathSnapshot(): String? =
        dataStore.data.first()[KEY_DEFAULT_WALLPAPER_PATH]

    suspend fun setDefaultWallpaperPath(path: String?) {
        dataStore.edit { prefs ->
            if (path == null) prefs.remove(KEY_DEFAULT_WALLPAPER_PATH) else prefs[KEY_DEFAULT_WALLPAPER_PATH] = path
        }
    }

    /** true = revert to the default wallpaper when music stops; false = keep the last album art. */
    val revertToDefaultOnStop: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_REVERT_ON_STOP] ?: false
    }

    suspend fun getRevertToDefaultOnStopSnapshot(): Boolean =
        dataStore.data.first()[KEY_REVERT_ON_STOP] ?: false

    suspend fun setRevertToDefaultOnStop(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_REVERT_ON_STOP] = value }
    }

    /**
     * Snapshot of Paperize's own "changer enabled" setting from right before Music mode was
     * turned on, so it can be restored exactly when Music mode is turned back off - regardless
     * of whether the user had it on or off beforehand.
     */
    suspend fun getChangerEnabledSnapshot(): Boolean =
        dataStore.data.first()[KEY_CHANGER_SNAPSHOT] ?: true

    suspend fun setChangerEnabledSnapshot(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_CHANGER_SNAPSHOT] = value }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("music_wallpaper_enabled")
        private val KEY_ALLOWED_PACKAGES = stringSetPreferencesKey("music_wallpaper_allowed_packages")
        private val KEY_DEFAULT_WALLPAPER_PATH = stringPreferencesKey("music_wallpaper_default_path")
        private val KEY_REVERT_ON_STOP = booleanPreferencesKey("music_wallpaper_revert_on_stop")
        private val KEY_CHANGER_SNAPSHOT = booleanPreferencesKey("music_wallpaper_changer_snapshot")
    }
}
