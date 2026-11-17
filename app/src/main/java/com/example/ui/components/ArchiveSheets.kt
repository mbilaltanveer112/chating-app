package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import java.util.concurrent.TimeUnit

enum class ArchiveInactivityPreset(
    val label: String,
    val daysThreshold: Int?
) {
    DAYS_30("30+ days old", 30),
    DAYS_7("7+ days old", 7),
    ALL_ACTIVE("All inbox", null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveInactiveChatsBottomSheet(
    activeChats: List<ChatRoom>,
    currentUserId: String,
    usersMap: Map<String, PublicProfile>,
    nowMillis: Long = System.currentTimeMillis(),
    onArchiveSelected: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var preset by remember { mutableStateOf(ArchiveInactivityPreset.DAYS_7) }
    val matchingRooms = remember(activeChats, preset, nowMillis) {
        val thresholdDays = preset.daysThreshold
        val sorted = activeChats.sortedBy { it.lastActivityEpochMs() }
        if (thresholdDays == null) {
            sorted
        } else {
            val cutoffMs = nowMillis - TimeUnit.DAYS.toMillis(thresholdDays.toLong())
            sorted.filter { it.isInactiveSince(cutoffMs) }
        }
    }

    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(matchingRooms, preset) {
        selectedIds = if (preset == ArchiveInactivityPreset.ALL_ACTIVE) {
            emptySet()
        } else {
            matchingRooms
                .filterNot { it.isPinnedBy(currentUserId) }
                .map { it.id }
                .toSet()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Archive Old or Inactive Chats",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Hide quiet conversations from Inbox without deleting messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Messages and media stay intact. You can unarchive anytime from the Archived tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ArchiveInactivityPreset.entries.forEach { option ->
                    FilterChip(
                        selected = preset == option,
                        onClick = { preset = option },
                        label = { Text(option.label, maxLines = 1) },
                        modifier = Modifier.testTag("archive_preset_${option.name.lowercase()}")
                    )
                }
            }

            if (matchingRooms.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (preset == ArchiveInactivityPreset.ALL_ACTIVE) {
                                "No active conversations to archive"
                            } else {
                                "No conversations inactive for ${preset.daysThreshold}+ days"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (preset != ArchiveInactivityPreset.ALL_ACTIVE) {
                                "Try \"All inbox\" to manually pick conversations to archive."
                            } else {
                                "Your main chat list is already clean."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${matchingRooms.size} conversation(s) matched",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val allSelected = selectedIds.size == matchingRooms.size && matchingRooms.isNotEmpty()
                    TextButton(
                        onClick = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                matchingRooms.map { it.id }.toSet()
                            }
                        },
                        modifier = Modifier.testTag("archive_select_toggle_all")
                    ) {
                        Text(if (allSelected) "Clear all" else "Select all")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(matchingRooms, key = { it.id }) { room ->
                        val title = room.displayTitleFor(currentUserId)
                        val otherUid = room.otherMemberUid(currentUserId)
                        val otherProfile = otherUid?.let { usersMap[it] }
                        val checked = selectedIds.contains(room.id)
                        val inactivityLabel = formatInactivityLabel(room.lastActivityEpochMs(), nowMillis)

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (checked) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (checked) selectedIds - room.id else selectedIds + room.id
                                }
                                .testTag("archive_candidate_${room.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { nowChecked ->
                                        selectedIds = if (nowChecked) selectedIds + room.id else selectedIds - room.id
                                    }
                                )
                                UserAvatar(
                                    name = title,
                                    avatarData = otherProfile?.avatarData.orEmpty(),
                                    photoUrl = otherProfile?.photoUrl.orEmpty(),
                                    isOnline = false,
                                    isGroup = room.isGroup,
                                    size = 40.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (room.isPinnedBy(currentUserId)) {
                                            Icon(
                                                imageVector = Icons.Rounded.PushPin,
                                                contentDescription = "Pinned",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "$inactivityLabel • ${room.lastMessageText.ifBlank { "No messages yet" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val toArchive = selectedIds.toList()
                        onDismiss()
                        onArchiveSelected(toArchive)
                    },
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("confirm_batch_archive_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Archive (${selectedIds.size})")
                }
            }
        }
    }
}

private fun formatInactivityLabel(lastActivityMs: Long, nowMillis: Long): String {
    if (lastActivityMs <= 0L) return "No activity"
    val diffMs = (nowMillis - lastActivityMs).coerceAtLeast(0L)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        hours < 1L -> "Just now"
        hours < 24L -> "${hours}h ago"
        days == 1L -> "1 day ago"
        else -> "$days days ago"
    }
}
