package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import com.example.security.PrivacyVisibilityResolver
import com.example.ui.theme.OnlineGreen
import com.example.util.MediaUtils

@Composable
fun UserAvatar(
    name: String,
    avatarData: String = "",
    photoUrl: String = "",
    isOnline: Boolean = false,
    isGroup: Boolean = false,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val decodedBitmap = remember(avatarData) {
        if (avatarData.isNotBlank()) MediaUtils.decodeBase64ToBitmap(avatarData) else null
    }
    val initials = remember(name) {
        name.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "P" }
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                decodedBitmap != null -> {
                    Image(
                        bitmap = decodedBitmap.asImageBitmap(),
                        contentDescription = "$name avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                photoUrl.isNotBlank() -> {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "$name avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isGroup -> {
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = "$name group avatar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(size * 0.52f)
                    )
                }
                else -> {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (isOnline && !isGroup) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.28f).coerceAtLeast(11.dp))
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .testTag("avatar_online_dot")
            )
        }
    }
}

@Composable
fun TypingDots(
    color: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "typing_dots_transition")
    val dotOffsets = List(3) { index ->
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600,
                    delayMillis = index * 140,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "typing_dot_$index"
        )
        phase
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((dotSize * 0.6f).coerceAtLeast(3.dp))
    ) {
        dotOffsets.forEach { phase ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .offset(y = (-3f * phase).dp)
                    .scale(0.85f + 0.25f * phase)
                    .alpha(0.45f + 0.55f * phase)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun TypingIndicatorBubble(
    typers: List<Pair<String, PublicProfile?>>,
    label: String,
    modifier: Modifier = Modifier
) {
    val visibleTypers = typers.take(3)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .testTag("typing_indicator_bubble"),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (visibleTypers.isNotEmpty()) {
            Box(
                modifier = Modifier.width((28 + (visibleTypers.size - 1) * 16).dp),
                contentAlignment = Alignment.CenterStart
            ) {
                visibleTypers.forEachIndexed { idx, (name, profile) ->
                    Box(
                        modifier = Modifier
                            .offset(x = (idx * 16).dp)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.background,
                                shape = CircleShape
                            )
                            .clip(CircleShape)
                    ) {
                        UserAvatar(
                            name = name,
                            avatarData = profile?.avatarData.orEmpty(),
                            photoUrl = profile?.photoUrl.orEmpty(),
                            isOnline = false,
                            isGroup = false,
                            size = 28.dp
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 6.dp,
                bottomEnd = 20.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TypingDots(
                    color = MaterialTheme.colorScheme.primary,
                    dotSize = 6.dp
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VoiceNoteContent(
    message: ChatMessage,
    isFromMe: Boolean,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val trackColor = activeColor.copy(alpha = 0.28f)
    val bars = remember(message.waveform) {
        if (message.waveform.isNotEmpty()) message.waveform
        else listOf(22, 45, 78, 35, 64, 92, 50, 74, 30, 82, 48, 60, 42, 70, 38, 55, 65, 28)
    }

    Row(
        modifier = modifier
            .widthIn(min = 210.dp, max = 270.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = activeColor.copy(alpha = 0.16f),
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onTogglePlay)
                .testTag("voice_play_button_${message.id}")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                    tint = activeColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                val barCount = bars.size
                val gap = 3.dp.toPx()
                val totalGap = gap * (barCount - 1)
                val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(2.dp.toPx())
                bars.forEachIndexed { idx, amp ->
                    val frac = (amp.coerceIn(10, 100) / 100f)
                    val barHeight = (size.height * frac).coerceAtLeast(4.dp.toPx())
                    val x = idx * (barWidth + gap)
                    val y = (size.height - barHeight) / 2f
                    val isPlayed = (idx.toFloat() / barCount.toFloat()) <= progress && (isPlaying || progress > 0f)
                    drawRoundRect(
                        color = if (isPlayed) activeColor else trackColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
            Text(
                text = MediaUtils.formatDuration(message.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = activeColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun DocumentAttachmentContent(
    message: ChatMessage,
    isFromMe: Boolean,
    onOpenDocument: () -> Unit
) {
    val contentColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = contentColor.copy(alpha = 0.1f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDocument)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = contentColor.copy(alpha = 0.16f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = "Document attachment",
                        tint = contentColor
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.mediaFileName.ifBlank { "Document" },
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(MediaUtils.formatFileSize(message.mediaSizeBytes))
                        if (message.mediaData.isNotBlank()) append(" • Tap to open")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.78f)
                )
            }
            if (message.mediaData.isNotBlank()) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Open document",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun VideoAttachmentContent(
    message: ChatMessage,
    isFromMe: Boolean
) {
    val thumb = remember(message.mediaData) { MediaUtils.decodeBase64ToBitmap(message.mediaData) }
    val contentColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = "Video thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = "Video clip",
                    tint = Color.White
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = buildString {
                    append(MediaUtils.formatDuration(message.durationMs))
                    if (message.mediaSizeBytes > 0) {
                        append(" • ")
                        append(MediaUtils.formatFileSize(message.mediaSizeBytes))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (thumb == null) {
            Text(
                text = message.mediaFileName.ifBlank { "Video" },
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun FullScreenImageDialog(
    bitmap: Bitmap,
    caption: String,
    isAiImage: Boolean,
    onDismiss: () -> Unit,
    onSaveToGallery: () -> Unit,
    onEditWithAi: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = caption.ifBlank { "Full screen photo" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 72.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAiImage) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "AI Generated",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Gemini AI Image",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close viewer",
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveToGallery,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Save to gallery",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                    Button(
                        onClick = onEditWithAi,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("viewer_edit_with_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Edit with AI",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit with AI")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPickerBottomSheet(
    title: String,
    chats: List<ChatRoom>,
    currentUserId: String,
    onSelectChat: (ChatRoom) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            if (chats.isEmpty()) {
                Text(
                    text = "No conversations yet. Start a chat in the Chats tab first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(chats, key = { it.id }) { room ->
                        val roomTitle = room.displayTitleFor(currentUserId)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectChat(room) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UserAvatar(
                                    name = roomTitle,
                                    isGroup = room.isGroup,
                                    size = 42.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = roomTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (room.isGroup) "${room.memberUids.size} members" else "Direct chat",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send to $roomTitle",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageReadInfoBottomSheet(
    message: ChatMessage,
    chat: ChatRoom?,
    currentUserId: String,
    usersMap: Map<String, PublicProfile>,
    blockedUserIds: Set<String> = emptySet(),
    allChats: List<ChatRoom> = emptyList(),
    nowMillis: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit
) {
    val dateTimeFormat = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }
    val readerUids = remember(message, currentUserId) {
        message.readerUidsExcluding(currentUserId)
    }
    val deliveredOnlyUids = remember(message, currentUserId) {
        message.deliveredOnlyUidsExcluding(currentUserId)
    }
    val pendingUids = remember(chat, readerUids, deliveredOnlyUids, currentUserId, message.senderId) {
        chat?.memberUids
            ?.filter {
                it != currentUserId &&
                    it != message.senderId &&
                    !readerUids.contains(it) &&
                    !deliveredOnlyUids.contains(it)
            }
            .orEmpty()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("message_read_info_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Message Info",
                style = MaterialTheme.typography.titleLarge
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = message.text.ifBlank { message.type.replace("_", " ").replaceFirstChar { it.uppercase() } },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = message.createdAt?.toDate()?.let { "Sent ${dateTimeFormat.format(it)}" } ?: "Sending...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Read by (${readerUids.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (readerUids.isEmpty()) {
                Text(
                    text = "Not opened by recipient yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    readerUids.forEach { uid ->
                        val profile = usersMap[uid]
                        val name = profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: chat?.memberNames?.get(uid)?.takeIf { it.isNotBlank() }
                            ?: "Member"
                        val readTime = message.readTimestamps[uid]?.toDate()?.let { dateTimeFormat.format(it) } ?: "Read"
                        val deliveredTime = message.deliveredTimestamps[uid]?.toDate()?.let { dateTimeFormat.format(it) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isReaderOnline = PrivacyVisibilityResolver.isEffectivelyOnline(
                                target = profile,
                                viewerUid = currentUserId,
                                isBlockedEitherWay = blockedUserIds.contains(uid),
                                isContact = PrivacyVisibilityResolver.isContactOfViewer(currentUserId, uid, allChats),
                                nowMillis = nowMillis
                            )
                            UserAvatar(
                                name = name,
                                avatarData = profile?.avatarData.orEmpty(),
                                photoUrl = profile?.photoUrl.orEmpty(),
                                isOnline = isReaderOnline,
                                size = 36.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (deliveredTime != null) "Read $readTime • Delivered $deliveredTime" else readTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = "Read",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (deliveredOnlyUids.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DoneAll,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Delivered to (${deliveredOnlyUids.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    deliveredOnlyUids.forEach { uid ->
                        val profile = usersMap[uid]
                        val name = profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: chat?.memberNames?.get(uid)?.takeIf { it.isNotBlank() }
                            ?: "Member"
                        val deliveredTime = message.deliveredTimestamps[uid]?.toDate()?.let { dateTimeFormat.format(it) }
                            ?: "Delivered"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isDeliveredOnline = PrivacyVisibilityResolver.isEffectivelyOnline(
                                target = profile,
                                viewerUid = currentUserId,
                                isBlockedEitherWay = blockedUserIds.contains(uid),
                                isContact = PrivacyVisibilityResolver.isContactOfViewer(currentUserId, uid, allChats),
                                nowMillis = nowMillis
                            )
                            UserAvatar(
                                name = name,
                                avatarData = profile?.avatarData.orEmpty(),
                                photoUrl = profile?.photoUrl.orEmpty(),
                                isOnline = isDeliveredOnline,
                                size = 36.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = deliveredTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = "Delivered",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (pendingUids.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Done,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sent / Pending (${pendingUids.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingUids.forEach { uid ->
                        val profile = usersMap[uid]
                        val name = profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: chat?.memberNames?.get(uid)?.takeIf { it.isNotBlank() }
                            ?: "Member"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isPendingOnline = PrivacyVisibilityResolver.isEffectivelyOnline(
                                target = profile,
                                viewerUid = currentUserId,
                                isBlockedEitherWay = blockedUserIds.contains(uid),
                                isContact = PrivacyVisibilityResolver.isContactOfViewer(currentUserId, uid, allChats),
                                nowMillis = nowMillis
                            )
                            UserAvatar(
                                name = name,
                                avatarData = profile?.avatarData.orEmpty(),
                                photoUrl = profile?.photoUrl.orEmpty(),
                                isOnline = isPendingOnline,
                                size = 36.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Sent to server",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Done,
                                contentDescription = "Sent",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
