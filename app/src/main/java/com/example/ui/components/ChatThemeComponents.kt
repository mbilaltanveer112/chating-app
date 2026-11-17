package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ChatAccentOption
import com.example.ui.theme.ChatBackgroundOption
import com.example.ui.theme.ChatCategoryTag
import com.example.ui.theme.ChatThemePreference
import kotlin.math.sin

@Composable
fun ChatThemedBackground(
    theme: ChatThemePreference,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val baseBg = MaterialTheme.colorScheme.background
    val accent = theme.accent.accentColor(isDarkTheme)

    Box(modifier = modifier.background(baseBg)) {
        when (theme.background) {
            ChatBackgroundOption.DEFAULT -> Unit
            ChatBackgroundOption.SOFT_TINT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accent.copy(alpha = if (isDarkTheme) 0.14f else 0.08f),
                                    baseBg,
                                    accent.copy(alpha = if (isDarkTheme) 0.09f else 0.06f)
                                )
                            )
                        )
                )
            }
            ChatBackgroundOption.AURORA_GLOW -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (isDarkTheme) 0.22f else 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.16f),
                            radius = size.maxDimension * 0.55f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (isDarkTheme) 0.16f else 0.10f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, size.height * 0.82f),
                            radius = size.maxDimension * 0.50f
                        )
                    )
                }
            }
            ChatBackgroundOption.WORK_GRID -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = if (isDarkTheme) 0.08f else 0.04f),
                                Color.Transparent
                            )
                        )
                    )
                    val step = 34.dp.toPx()
                    val lineColor = accent.copy(alpha = if (isDarkTheme) 0.09f else 0.08f)
                    var x = 0f
                    while (x <= size.width) {
                        drawLine(
                            color = lineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                        x += step
                    }
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        y += step
                    }
                }
            }
            ChatBackgroundOption.PERSONAL_WAVES -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = if (isDarkTheme) 0.12f else 0.06f),
                                Color.Transparent,
                                accent.copy(alpha = if (isDarkTheme) 0.10f else 0.05f)
                            )
                        )
                    )
                    val strokeColor = accent.copy(alpha = if (isDarkTheme) 0.12f else 0.11f)
                    val waveSpacing = 72.dp.toPx()
                    val amplitude = 16.dp.toPx()
                    var baseY = waveSpacing * 0.6f
                    var waveIndex = 0
                    while (baseY < size.height + waveSpacing) {
                        val path = Path()
                        var x = 0f
                        val phase = waveIndex * 0.9f
                        path.moveTo(0f, baseY + sin(phase) * amplitude)
                        while (x <= size.width) {
                            val y = baseY + sin((x / size.width) * 6.28f + phase) * amplitude
                            path.lineTo(x, y)
                            x += 18f
                        }
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        baseY += waveSpacing
                        waveIndex += 1
                    }
                }
            }
        }
    }
}

@Composable
fun ChatCategoryBadge(
    tag: ChatCategoryTag,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (tag == ChatCategoryTag.NONE) return
    val icon = when (tag) {
        ChatCategoryTag.PERSONAL -> Icons.Rounded.Home
        ChatCategoryTag.WORK -> Icons.Rounded.Work
        ChatCategoryTag.PRIORITY -> Icons.Rounded.Star
        ChatCategoryTag.NONE -> null
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.16f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = tag.label,
                    tint = accentColor,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = tag.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatThemeBottomSheet(
    chatTitle: String,
    initialTheme: ChatThemePreference,
    isDarkTheme: Boolean,
    onApplyTheme: (ChatThemePreference?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(initialTheme) { mutableStateOf(initialTheme) }

    val previewAccent = draft.accent.accentColor(isDarkTheme)
    val previewOnAccent = draft.accent.onAccentColor(isDarkTheme)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = previewAccent.copy(alpha = 0.18f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "Chat theme",
                            tint = previewAccent
                        )
                    }
                }
                Column {
                    Text(
                        text = "Customize Chat Appearance",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = chatTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Mini Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.dp,
                        color = previewAccent.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                ChatThemedBackground(
                    theme = draft,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ChatCategoryBadge(tag = draft.tag, accentColor = previewAccent)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Ready for today's update?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = previewAccent
                            ) {
                                Text(
                                    text = "On it — sending details now!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = previewOnAccent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatThemePreference.Presets.forEach { (label, preset) ->
                        val selected = draft == preset
                        FilterChip(
                            selected = selected,
                            onClick = { draft = preset },
                            label = { Text(label) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(preset.accent.accentColor(isDarkTheme))
                                )
                            },
                            modifier = Modifier.testTag("theme_preset_${label.lowercase()}")
                        )
                    }
                }
            }

            // Contact Category Tag
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Conversation Label",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatCategoryTag.entries.forEach { tag ->
                        FilterChip(
                            selected = draft.tag == tag,
                            onClick = { draft = draft.copy(tag = tag) },
                            label = { Text(tag.label) },
                            modifier = Modifier.testTag("theme_tag_${tag.wireKey}")
                        )
                    }
                }
            }

            // Accent Color Swatches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Accent Color • ${draft.accent.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChatAccentOption.entries.forEach { option ->
                        val swatchColor = option.accentColor(isDarkTheme)
                        val onSwatch = option.onAccentColor(isDarkTheme)
                        val isSelected = draft.accent == option
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { draft = draft.copy(accent = option) }
                                .testTag("theme_accent_${option.wireKey}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 40.dp else 34.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "${option.label} selected",
                                        tint = onSwatch,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Background Pattern / Style
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Background Style",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatBackgroundOption.entries.forEach { bgOption ->
                        FilterChip(
                            selected = draft.background == bgOption,
                            onClick = { draft = draft.copy(background = bgOption) },
                            label = { Text(bgOption.label) },
                            modifier = Modifier.testTag("theme_bg_${bgOption.wireKey}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onApplyTheme(null)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("reset_chat_theme_button")
                ) {
                    Text("Reset Default")
                }
                Button(
                    onClick = {
                        onApplyTheme(if (draft.isDefault) null else draft)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = previewAccent,
                        contentColor = previewOnAccent
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("apply_chat_theme_button")
                ) {
                    Text("Apply Theme", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
