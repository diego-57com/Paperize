package com.anthonyla.paperize.presentation.screens.music_wallpaper

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.presentation.common.components.SettingSwitchItem
import com.anthonyla.paperize.presentation.theme.AppSpacing

/**
 * Music Wallpaper: functions as a third wallpaper mode alongside Static and Live. When
 * turned on, it pauses Paperize's normal scheduled rotation and drives the wallpaper from
 * whatever's currently playing in the allowed apps below, saving every cover to a gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicWallpaperScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicWallpaperViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Music Wallpaper") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = AppSpacing.large),
            contentPadding = PaddingValues(vertical = AppSpacing.large)
        ) {
            item {
                SettingSwitchItem(
                    title = "Music mode",
                    description = "Takes over the wallpaper from Static/Live rotation and " +
                        "uses the current track's album art instead.",
                    checked = uiState.enabled,
                    onCheckedChange = { viewModel.setEnabled(it) }
                )
                Spacer(modifier = Modifier.height(AppSpacing.medium))
            }

            if (uiState.enabled && uiState.wallpaperMode != null && uiState.wallpaperMode != WallpaperMode.STATIC) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(AppSpacing.large),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.small))
                            Column {
                                Text(
                                    text = "Wallpaper mode is set to Live",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.extraSmall))
                                Text(
                                    text = "Music mode needs the Static wallpaper mode to be visible - " +
                                        "a Live wallpaper renders its own engine on top. Switch to " +
                                        "Static in Wallpaper Mode settings to see album art changes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AppSpacing.medium))
                }
            }

            item {
                Card(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.large)) {
                        Text(
                            text = "Grant notification access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.extraSmall))
                        Text(
                            text = "Required so Paperize can detect the currently playing track. " +
                                "Tap to open system settings and enable it for Paperize.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacing.medium))
            }

            item {
                Card(
                    onClick = onNavigateToGallery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.large),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(AppSpacing.medium))
                        Column {
                            Text(
                                text = "Gallery & default wallpaper",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.extraSmall))
                            Text(
                                text = "Browse every captured cover and choose a default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacing.extraLarge))
            }

            item {
                Text(
                    text = "When music stops",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(AppSpacing.small))

                OnStopOption(
                    title = "Keep the last album art",
                    selected = !uiState.revertToDefaultOnStop,
                    onClick = { viewModel.setRevertToDefaultOnStop(false) }
                )
                OnStopOption(
                    title = "Revert to default wallpaper",
                    selected = uiState.revertToDefaultOnStop,
                    onClick = { viewModel.setRevertToDefaultOnStop(true) }
                )
                Spacer(modifier = Modifier.height(AppSpacing.extraLarge))
            }

            item {
                Text(
                    text = "Allowed apps",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AppSpacing.extraSmall))
                Text(
                    text = "Only music from checked apps will change your wallpaper. Everything else is ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AppSpacing.medium))
            }

            if (uiState.isLoadingApps) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.extraLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(uiState.installedApps, key = { it.packageName }) { app ->
                    SettingSwitchItem(
                        title = app.label,
                        description = app.packageName,
                        checked = app.packageName in uiState.allowedPackages,
                        onCheckedChange = { checked ->
                            viewModel.setPackageAllowed(app.packageName, checked)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(AppSpacing.large)) }
        }
    }
}

@Composable
private fun OnStopOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = AppSpacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
