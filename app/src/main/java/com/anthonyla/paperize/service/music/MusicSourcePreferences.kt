package com.anthonyla.paperize.service.music

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("music_wallpaper_enabled")
        private val KEY_ALLOWED_PACKAGES = stringSetPreferencesKey("music_wallpaper_allowed_packages")
    }
}
