package com.anthonyla.paperize.presentation.screens.music_wallpaper

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.service.music.MusicSourcePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

data class MusicWallpaperUiState(
    val enabled: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = true,
    val revertToDefaultOnStop: Boolean = false,
    val wallpaperMode: WallpaperMode? = null
)

/** Intermediate grouping used only to stay within combine()'s 5-flow lambda overload. */
private data class CoreState(
    val enabled: Boolean,
    val allowedPackages: Set<String>,
    val revertToDefaultOnStop: Boolean,
    val wallpaperMode: WallpaperMode
)

@HiltViewModel
class MusicWallpaperViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: MusicSourcePreferences,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isLoadingApps = MutableStateFlow(true)

    private val coreState = combine(
        preferences.isEnabled,
        preferences.allowedPackages,
        preferences.revertToDefaultOnStop,
        settingsRepository.getWallpaperModeFlow()
    ) { enabled, allowed, revert, mode ->
        CoreState(enabled, allowed, revert, mode)
    }

    val uiState: StateFlow<MusicWallpaperUiState> = combine(
        coreState,
        installedApps,
        isLoadingApps
    ) { core, apps, loading ->
        MusicWallpaperUiState(
            enabled = core.enabled,
            allowedPackages = core.allowedPackages,
            installedApps = apps,
            isLoadingApps = loading,
            revertToDefaultOnStop = core.revertToDefaultOnStop,
            wallpaperMode = core.wallpaperMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = MusicWallpaperUiState()
    )

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            isLoadingApps.value = true
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .asSequence()
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { info: ApplicationInfo ->
                        InstalledAppInfo(
                            packageName = info.packageName,
                            label = pm.getApplicationLabel(info).toString()
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
            installedApps.value = apps
            isLoadingApps.value = false
        }
    }

    /**
     * Turning Music mode on/off also pauses/resumes Paperize's own scheduled wallpaper
     * rotation ("changer"), so the two don't fight over the wallpaper. The changer's prior
     * state is snapshotted before pausing it, and restored exactly (not just forced back to
     * "on") when Music mode is turned off.
     */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (enabled) {
                    val currentlyEnabled = settingsRepository.getScheduleSettings().enableChanger
                    preferences.setChangerEnabledSnapshot(currentlyEnabled)
                    settingsRepository.updateEnableChanger(false)
                } else {
                    val previous = preferences.getChangerEnabledSnapshot()
                    settingsRepository.updateEnableChanger(previous)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not toggle Paperize's own changer", e)
            }
            preferences.setEnabled(enabled)
        }
    }

    fun setPackageAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { preferences.setPackageAllowed(packageName, allowed) }
    }

    fun setRevertToDefaultOnStop(value: Boolean) {
        viewModelScope.launch { preferences.setRevertToDefaultOnStop(value) }
    }

    companion object {
        private const val TAG = "MusicWallpaperVM"
    }
}
