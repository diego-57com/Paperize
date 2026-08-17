package com.anthonyla.paperize.presentation.screens.music_wallpaper

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthonyla.paperize.core.constants.Constants
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
    val isLoadingApps: Boolean = true
)

@HiltViewModel
class MusicWallpaperViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: MusicSourcePreferences
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isLoadingApps = MutableStateFlow(true)

    val uiState: StateFlow<MusicWallpaperUiState> = combine(
        preferences.isEnabled,
        preferences.allowedPackages,
        installedApps,
        isLoadingApps
    ) { enabled, allowed, apps, loading ->
        MusicWallpaperUiState(
            enabled = enabled,
            allowedPackages = allowed,
            installedApps = apps,
            isLoadingApps = loading
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

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setEnabled(enabled) }
    }

    fun setPackageAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { preferences.setPackageAllowed(packageName, allowed) }
    }
}
