package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.AudioExportFormat
import com.example.domain.VoiceCatalog
import com.example.domain.VoiceCategory
import com.example.domain.VoiceProfile
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo600
import com.example.ui.theme.LocalArLabsColors
import com.example.ui.theme.VioletAccent

@Composable
fun AnimatedVoiceSelectorOverlay(
    visible: Boolean,
    selectedVoiceId: String,
    previewingVoiceId: String?,
    previewLoadingVoiceId: String?,
    onSelectVoice: (VoiceProfile) -> Unit,
    onPreviewVoice: (VoiceProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (visible) {
        BackHandler { onDismiss() }
    }

    val ext = LocalArLabsColors.current

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280)) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .testTag("voice_selector_sheet"),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = ext.glassSurfaceElevated,
                border = BorderStroke(1.dp, ext.glassBorder),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 12.dp, bottom = 12.dp)
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Awaaz Chunein (7 Studio Voices)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Preview dabakar har awaaz check karein",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Band karein",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = ext.glassBorder)

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(VoiceCatalog.voices, key = { it.id }) { voice ->
                            val isSelected = voice.id == selectedVoiceId
                            val isPreviewing = voice.id == previewingVoiceId
                            val isLoadingPreview = voice.id == previewLoadingVoiceId

                            val accent = when (voice.category) {
                                VoiceCategory.MALE -> Indigo400
                                VoiceCategory.FEMALE -> VioletAccent
                                VoiceCategory.NARRATOR -> CyanAccent
                            }

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isSelected) {
                                    Indigo600.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Indigo500 else ext.glassBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onSelectVoice(voice) }
                                    .testTag("voice_item_${voice.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(accent.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPreviewing) {
                                            AnimatedEqualizerBars(isActive = true, color = accent)
                                        } else {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.RecordVoiceOver,
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = voice.displayName,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = accent.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = voice.badge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accent,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = voice.hinglishDescription,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Preview button on every voice
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isPreviewing) accent else accent.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = !isLoadingPreview) {
                                                onPreviewVoice(voice)
                                            }
                                            .testTag("preview_voice_${voice.id}")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            when {
                                                isLoadingPreview -> CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = accent
                                                )
                                                isPreviewing -> Icon(
                                                    imageVector = Icons.Rounded.Stop,
                                                    contentDescription = "Preview rokein",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                else -> Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = "${voice.displayName} preview sunein",
                                                    tint = accent,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportDownloadSheet(
    visible: Boolean,
    totalParts: Int,
    readyPartsCount: Int,
    isExporting: Boolean,
    onExport: (AudioExportFormat, Boolean) -> Unit,
    onShare: (AudioExportFormat) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (visible) {
        BackHandler { onDismiss() }
    }

    val ext = LocalArLabsColors.current

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(260)) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_download_sheet"),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = ext.glassSurfaceElevated,
                border = BorderStroke(1.dp, ext.glassBorder),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Audio Download & Share",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (readyPartsCount < totalParts) {
                                    "Abhi $readyPartsCount / $totalParts parts ready hain"
                                } else {
                                    "Poori script ek continuous file mein save hogi"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Band karein"
                            )
                        }
                    }

                    ExportOptionRow(
                        title = "Ek Continuous WAV Save Karein",
                        subtitle = "Studio quality uncompressed audio • Downloads/AR Labs",
                        icon = Icons.Rounded.AudioFile,
                        accent = Indigo400,
                        enabled = !isExporting,
                        onClick = { onExport(AudioExportFormat.WAV, false) },
                        testTag = "export_wav_single"
                    )

                    ExportOptionRow(
                        title = "Ek Continuous M4A Save Karein",
                        subtitle = "Chhota file size, WhatsApp & reels ke liye best",
                        icon = Icons.Rounded.GraphicEq,
                        accent = CyanAccent,
                        enabled = !isExporting,
                        onClick = { onExport(AudioExportFormat.M4A, false) },
                        testTag = "export_m4a_single"
                    )

                    if (readyPartsCount > 1) {
                        ExportOptionRow(
                            title = "Parts mein Save Karein ($readyPartsCount Files • M4A)",
                            subtitle = "Lambi script ke har hisse ki alag file banayein",
                            icon = Icons.Rounded.FolderZip,
                            accent = VioletAccent,
                            enabled = !isExporting,
                            onClick = { onExport(AudioExportFormat.M4A, true) },
                            testTag = "export_m4a_parts"
                        )
                    }

                    ExportOptionRow(
                        title = "Audio Share Karein",
                        subtitle = "WhatsApp, Telegram ya Drive par seedha bhejein",
                        icon = Icons.Rounded.Share,
                        accent = ext.success,
                        enabled = !isExporting,
                        onClick = {
                            onDismiss()
                            onShare(AudioExportFormat.M4A)
                        },
                        testTag = "export_share_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val ext = LocalArLabsColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, ext.glassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
