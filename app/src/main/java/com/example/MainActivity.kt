package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.components.AnimatedVoiceSelectorOverlay
import com.example.ui.components.CustomToastBanner
import com.example.ui.components.ExportDownloadSheet
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.ArLabsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as ArLabsApp).container

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(appContainer)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

            val notifPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { /* Playback works regardless; notification shows if granted */ }
            )

            LaunchedEffect(Unit) {
                viewModel.shareEvents.collect { shareEvent ->
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = shareEvent.mimeType
                        putExtra(Intent.EXTRA_STREAM, shareEvent.uri)
                        putExtra(Intent.EXTRA_SUBJECT, shareEvent.fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(sendIntent, "AR Labs Audio Share Karein")
                    runCatching { startActivity(chooser) }
                }
            }

            ArLabsTheme(themeMode = settings.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = when {
                            uiState.showSplash -> "splash"
                            uiState.isSettingsOpen -> "settings"
                            else -> "main"
                        },
                        animationSpec = tween(320),
                        label = "root_screen"
                    ) { screen ->
                        when (screen) {
                            "splash" -> SplashScreen()
                            "settings" -> SettingsScreen(
                                currentApiKey = settings.apiKey,
                                themeMode = settings.themeMode,
                                bannerMessage = uiState.settingsBannerMessage,
                                keyTestStatus = uiState.keyTestStatus,
                                keyTestFeedback = uiState.keyTestFeedback,
                                onSaveApiKey = viewModel::saveApiKey,
                                onTestApiKey = viewModel::testApiKey,
                                onSelectThemeMode = viewModel::setThemeMode,
                                onClearCache = viewModel::clearCache,
                                onBack = viewModel::closeSettings
                            )
                            else -> MainScreen(
                                uiState = uiState,
                                settings = settings,
                                playbackState = playbackState,
                                onScriptChange = viewModel::updateScriptText,
                                onClearScript = viewModel::clearScript,
                                onMagicFill = viewModel::magicFill,
                                onToggleThemeQuick = viewModel::toggleThemeQuick,
                                onOpenSettings = { viewModel.openSettings() },
                                onOpenVoiceSheet = { viewModel.setVoiceSheetOpen(true) },
                                onToggleNonStop = viewModel::setNonStopEnabled,
                                onGenerateOrPlay = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!granted) {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    viewModel.onGenerateOrPlayClicked()
                                },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onNextPart = viewModel::nextPart,
                                onPreviousPart = viewModel::previousPart,
                                onSeekTo = viewModel::seekTo,
                                onSpeedChange = viewModel::setSpeed,
                                onVolumeChange = viewModel::setVolume,
                                onRepeatToggle = viewModel::setRepeat,
                                onStopPlayback = viewModel::stopPlayback,
                                onOpenDownloadSheet = { viewModel.setDownloadSheetOpen(true) },
                                onShareAudio = { viewModel.shareAudio() },
                                onEditWhilePlaying = { viewModel.setEditingWhilePlaying(true) }
                            )
                        }
                    }

                    AnimatedVoiceSelectorOverlay(
                        visible = uiState.isVoiceSheetOpen && !uiState.showSplash && !uiState.isSettingsOpen,
                        selectedVoiceId = settings.selectedVoiceId,
                        previewingVoiceId = playbackState.previewingVoiceId,
                        previewLoadingVoiceId = playbackState.previewLoadingVoiceId,
                        onSelectVoice = viewModel::selectVoice,
                        onPreviewVoice = viewModel::previewVoice,
                        onDismiss = { viewModel.setVoiceSheetOpen(false) }
                    )

                    ExportDownloadSheet(
                        visible = uiState.isDownloadSheetOpen && !uiState.showSplash && !uiState.isSettingsOpen,
                        totalParts = playbackState.totalParts,
                        readyPartsCount = playbackState.readyPartsCount,
                        isExporting = playbackState.isExporting,
                        onExport = viewModel::exportAudio,
                        onShare = viewModel::shareAudio,
                        onDismiss = { viewModel.setDownloadSheetOpen(false) }
                    )

                    CustomToastBanner(
                        toast = uiState.activeToast,
                        onDismiss = viewModel::dismissToast
                    )
                }
            }
        }
    }
}
