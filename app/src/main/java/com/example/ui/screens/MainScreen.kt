package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.PlaybackUiState
import com.example.audio.SessionMode
import com.example.data.UserSettings
import com.example.domain.VoiceCatalog
import com.example.ui.MainUiState
import com.example.ui.components.AnimatedEqualizerBars
import com.example.ui.components.DualProgressSeekBar
import com.example.ui.components.GlassCard
import com.example.ui.components.StudioBackground
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo600
import com.example.ui.theme.LocalArLabsColors

@Composable
fun MainScreen(
    uiState: MainUiState,
    settings: UserSettings,
    playbackState: PlaybackUiState,
    onScriptChange: (String) -> Unit,
    onClearScript: () -> Unit,
    onMagicFill: () -> Unit,
    onToggleThemeQuick: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoiceSheet: () -> Unit,
    onToggleNonStop: (Boolean) -> Unit,
    onGenerateOrPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextPart: () -> Unit,
    onPreviousPart: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRepeatToggle: (Boolean) -> Unit,
    onStopPlayback: () -> Unit,
    onOpenDownloadSheet: () -> Unit,
    onShareAudio: () -> Unit,
    onEditWhilePlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalArLabsColors.current
    val selectedVoice = remember(settings.selectedVoiceId) {
        VoiceCatalog.byId(settings.selectedVoiceId)
    }

    StudioBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header: Logo, Title "Text-to-Speech", Dark/Light Toggle, Settings
            MainHeader(
                isDark = ext.isDark,
                hasApiKey = settings.apiKey.isNotBlank(),
                onToggleTheme = { onToggleThemeQuick(ext.isDark) },
                onOpenSettings = onOpenSettings
            )

            // 2. Large Script Box (or Live Sentence-Highlighted Reader while speaking)
            ScriptEditorCard(
                uiState = uiState,
                playbackState = playbackState,
                onScriptChange = onScriptChange,
                onClearScript = onClearScript,
                onMagicFill = onMagicFill,
                onEditWhilePlaying = onEditWhilePlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // 3. Non-Stop Telemetry & Controls OR Standard Custom Player
            AnimatedVisibility(visible = playbackState.hasActiveAudio && playbackState.isNonStopSession) {
                NonStopTelemetryPlayerCard(
                    playbackState = playbackState,
                    onTogglePlayPause = onTogglePlayPause,
                    onPreviousPart = onPreviousPart,
                    onNextPart = onNextPart,
                    onSeekTo = onSeekTo,
                    onSpeedChange = onSpeedChange,
                    onVolumeChange = onVolumeChange,
                    onRepeatToggle = onRepeatToggle,
                    onStop = onStopPlayback,
                    onOpenDownload = onOpenDownloadSheet,
                    onShare = onShareAudio
                )
            }

            AnimatedVisibility(visible = playbackState.hasActiveAudio && !playbackState.isNonStopSession) {
                StandardCustomPlayerCard(
                    playbackState = playbackState,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onSpeedChange = onSpeedChange,
                    onOpenDownload = onOpenDownloadSheet,
                    onShare = onShareAudio,
                    onClose = onStopPlayback
                )
            }

            // 4. Bottom Control Bar: Voice selector (opens upward), Non-Stop switch, big Generate/Play button
            BottomActionBar(
                selectedVoiceName = selectedVoice.displayName,
                selectedVoiceBadge = selectedVoice.badge,
                nonStopEnabled = settings.nonStopEnabled,
                isGeneratingSingle = playbackState.sessionMode == SessionMode.GENERATING_SINGLE,
                isPlayingActive = playbackState.isPlaying,
                statusText = playbackState.statusText,
                onOpenVoiceSheet = onOpenVoiceSheet,
                onToggleNonStop = onToggleNonStop,
                onGenerateOrPlay = onGenerateOrPlay
            )
        }
    }
}

@Composable
private fun MainHeader(
    isDark: Boolean,
    hasApiKey: Boolean,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val ext = LocalArLabsColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_ar_labs_logo),
            contentDescription = "AR Labs Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.2.dp, Indigo400.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = R.string.header_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AR Labs",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Indigo400
                )
                Text(
                    text = " • Gemini Voice AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = ext.glassSurface,
            border = BorderStroke(1.dp, ext.glassBorder)
        ) {
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.testTag("theme_toggle_button")
            ) {
                AnimatedContent(
                    targetState = isDark,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "theme_icon"
                ) { dark ->
                    Icon(
                        imageVector = if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = if (dark) "Light mode lagayein" else "Dark mode lagayein",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box {
            Surface(
                shape = CircleShape,
                color = ext.glassSurface,
                border = BorderStroke(1.dp, ext.glassBorder)
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings kholein",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (!hasApiKey) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CyanAccent)
                )
            }
        }
    }
}

@Composable
private fun ScriptEditorCard(
    uiState: MainUiState,
    playbackState: PlaybackUiState,
    onScriptChange: (String) -> Unit,
    onClearScript: () -> Unit,
    onMagicFill: () -> Unit,
    onEditWhilePlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalArLabsColors.current
    val showLiveHighlights = playbackState.hasActiveAudio && !uiState.isEditingWhilePlaying
    val highlightScrollState = rememberScrollState()

    // Smooth auto-scroll when active sentence advances in long scripts
    LaunchedEffect(playbackState.activeSentenceStart) {
        if (showLiveHighlights && playbackState.activeSentenceStart > 0 && uiState.scriptText.isNotEmpty()) {
            val ratio = playbackState.activeSentenceStart.toFloat() / uiState.scriptText.length.toFloat()
            val targetScroll = (highlightScrollState.maxValue * ratio).toInt()
            highlightScrollState.animateScrollTo(targetScroll)
        }
    }

    GlassCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        elevated = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top bar inside script box: counters + Magic Fill + Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "${uiState.scriptAnalysis.wordCount} shabd • ${uiState.scriptAnalysis.charCount} akshar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Indigo500.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Indigo400.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onMagicFill() }
                            .testTag("magic_fill_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = "Sample script bharein",
                                tint = Indigo400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Magic Fill",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    val canClear = uiState.scriptText.isNotEmpty()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (canClear) 0.55f else 0.28f),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canClear) { onClearScript() }
                            .testTag("clear_script_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Script saaf karein",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canClear) 1f else 0.45f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canClear) 1f else 0.45f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (showLiveHighlights) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Reading Highlight",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CyanAccent
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEditWhilePlaying() }
                            .testTag("edit_script_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit Karein",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                val text = playbackState.activeScriptText.ifEmpty { uiState.scriptText }
                val start = playbackState.activeSentenceStart.coerceIn(0, text.length)
                val end = playbackState.activeSentenceEnd.coerceIn(start, text.length)
                val annotated = buildAnnotatedString {
                    append(text)
                    if (end > start) {
                        addStyle(
                            style = SpanStyle(
                                background = ext.highlightBg,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            start = start,
                            end = end
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(highlightScrollState)
                ) {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                val editScroll = rememberScrollState()
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val minEditorHeight = maxHeight
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(editScroll)
                    ) {
                        if (uiState.scriptText.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.script_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                        BasicTextField(
                            value = uiState.scriptText,
                            onValueChange = onScriptChange,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 25.sp
                            ),
                            cursorBrush = SolidColor(Indigo400),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minEditorHeight)
                                .testTag("script_input_field")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NonStopTelemetryPlayerCard(
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onPreviousPart: () -> Unit,
    onNextPart: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRepeatToggle: (Boolean) -> Unit,
    onStop: () -> Unit,
    onOpenDownload: () -> Unit,
    onShare: () -> Unit
) {
    val ext = LocalArLabsColors.current
    var showVolumeSlider by remember { mutableStateOf(false) }
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("non_stop_player_card"),
        cornerRadius = 24.dp,
        elevated = true
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Telemetry Row: "Part 5 of 23", Buffering indicator, Buffer ready badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedEqualizerBars(isActive = playbackState.isPlaying && !playbackState.isBuffering)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Part ${playbackState.currentPartIndex + 1} of ${playbackState.totalParts.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (playbackState.isBuffering) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyanAccent.copy(alpha = 0.18f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.8.dp,
                                    color = CyanAccent
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Buffering...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Indigo500.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "Ready: ${playbackState.readyPartsCount}/${playbackState.totalParts}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Indigo400,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(34.dp).testTag("non_stop_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Rokein",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Dual Progress Seek Bar (playback + ready-ahead buffer)
            Column {
                DualProgressSeekBar(
                    playbackFraction = playbackState.playbackFraction,
                    bufferedFraction = playbackState.bufferedFraction,
                    onSeekFraction = { frac ->
                        onSeekTo((playbackState.estimatedTotalMs * frac).toLong())
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(playbackState.elapsedMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Buffer ahead: ${(playbackState.bufferedFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Indigo400
                    )
                    Text(
                        text = formatDurationMs(playbackState.estimatedTotalMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Transport Controls: Prev, Play/Pause, Next, Repeat, Volume, Download, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPreviousPart,
                    modifier = Modifier.size(48.dp).testTag("prev_part_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Pichhla part",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Indigo600,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable { onTogglePlayPause() }
                        .testTag("non_stop_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause karein" else "Play karein",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onNextPart,
                    modifier = Modifier.size(48.dp).testTag("next_part_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Agla part",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { onRepeatToggle(!playbackState.isRepeat) },
                    modifier = Modifier.size(48.dp).testTag("repeat_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Repeat mode",
                        tint = if (playbackState.isRepeat) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showVolumeSlider = !showVolumeSlider },
                    modifier = Modifier.size(48.dp).testTag("volume_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Volume control",
                        tint = if (showVolumeSlider) Indigo400 else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onOpenDownload,
                    modifier = Modifier.size(48.dp).testTag("non_stop_download_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Audio save karein",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(48.dp).testTag("non_stop_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Audio share karein",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Speed pills row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                speeds.forEach { spd ->
                    val selected = kotlin.math.abs(playbackState.speed - spd) < 0.05f
                    val label = if (spd == 1.0f) "1x" else "${spd}x"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Indigo600 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSpeedChange(spd) }
                            .testTag("speed_$label")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showVolumeSlider) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Volume",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = playbackState.volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Indigo400,
                            activeTrackColor = Indigo500
                        ),
                        modifier = Modifier.weight(1f).testTag("volume_slider")
                    )
                    Text(
                        text = "${(playbackState.volume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardCustomPlayerCard(
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onOpenDownload: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit
) {
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)
    val nextSpeed = speeds[(speeds.indexOfFirst { kotlin.math.abs(it - playbackState.speed) < 0.05f } + 1) % speeds.size]

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("standard_player_card"),
        cornerRadius = 24.dp,
        elevated = true
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedEqualizerBars(isActive = playbackState.isPlaying)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playbackState.activeVoiceName.ifBlank { "Audio Player" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).testTag("player_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Player band karein",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DualProgressSeekBar(
                playbackFraction = playbackState.playbackFraction,
                bufferedFraction = 1f,
                onSeekFraction = { frac ->
                    onSeekTo((playbackState.estimatedTotalMs * frac).toLong())
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationMs(playbackState.elapsedMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDurationMs(playbackState.estimatedTotalMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = Indigo600,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onTogglePlayPause() }
                        .testTag("standard_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSpeedChange(nextSpeed) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (playbackState.speed == 1.0f) "1x" else "${playbackState.speed}x",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Indigo500.copy(alpha = 0.16f),
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenDownload() }
                        .testTag("standard_download_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            tint = Indigo400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(48.dp).testTag("standard_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share audio",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    selectedVoiceName: String,
    selectedVoiceBadge: String,
    nonStopEnabled: Boolean,
    isGeneratingSingle: Boolean,
    isPlayingActive: Boolean,
    statusText: String,
    onOpenVoiceSheet: () -> Unit,
    onToggleNonStop: (Boolean) -> Unit,
    onGenerateOrPlay: () -> Unit
) {
    val ext = LocalArLabsColors.current

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevated = true
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Upward-opening Voice Selector
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, ext.glassBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenVoiceSheet() }
                        .testTag("voice_selector_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = Indigo400,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedVoiceName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$selectedVoiceBadge • Badlein",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Awaaz list kholein",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Non-Stop Switch Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (nonStopEnabled) Indigo500.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (nonStopEnabled) Indigo400.copy(alpha = 0.5f) else ext.glassBorder
                    ),
                    modifier = Modifier.height(52.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Non-Stop",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (nonStopEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = nonStopEnabled,
                            onCheckedChange = onToggleNonStop,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Indigo600
                            ),
                            modifier = Modifier.testTag("non_stop_switch")
                        )
                    }
                }
            }

            // Big Generate / Play CTA Button
            val buttonGradient = Brush.horizontalGradient(listOf(Indigo600, Indigo500, CyanAccent))
            Button(
                onClick = onGenerateOrPlay,
                enabled = !isGeneratingSingle,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isGeneratingSingle) {
                            SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                        } else {
                            buttonGradient
                        }
                    )
                    .testTag("generate_play_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isGeneratingSingle) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.4.dp,
                            color = Indigo400
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusText.ifBlank { "Audio ban raha hai..." },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isPlayingActive -> "Nayi Script Generate & Play"
                                nonStopEnabled -> "Non-Stop Generate & Play"
                                else -> "Audio Generate Karein"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
