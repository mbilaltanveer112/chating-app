package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import com.example.security.PrivacyVisibilityResolver
import com.example.ui.components.UserAvatar
import com.example.ui.viewmodel.UiState

@Composable
fun NewChatDialog(
    currentUserId: String,
    usersState: UiState<List<PublicProfile>>,
    allChats: List<ChatRoom> = emptyList(),
    blockedUserIds: Set<String> = emptySet(),
    blockedEitherWayUserIds: Set<String> = blockedUserIds,
    onStartDirectChat: (PublicProfile) -> Unit,
    onCreateGroupChat: (String, List<PublicProfile>) -> Unit,
    onDismiss: () -> Unit
) {
    var modeIndex by remember { mutableIntStateOf(0) } // 0 = Direct, 1 = Group
    var search by remember { mutableStateOf("") }
    var groupTitle by remember { mutableStateOf("") }
    var selectedUids by remember { mutableStateOf(setOf<String>()) }

    val allUsers = remember(usersState) {
        (usersState as? UiState.Success)?.data.orEmpty()
    }
    val me = remember(allUsers, currentUserId) {
        allUsers.firstOrNull { it.userId == currentUserId }
            ?: PublicProfile(userId = currentUserId, displayName = "Me", handle = "me")
    }
    val otherUsers = remember(allUsers, currentUserId, search, blockedUserIds) {
        val q = search.trim().lowercase()
        allUsers.filter { it.userId != currentUserId && !blockedUserIds.contains(it.userId) }
            .filter {
                q.isEmpty() ||
                    it.displayName.lowercase().contains(q) ||
                    it.handle.lowercase().contains(q)
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Conversation",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = modeIndex == 0,
                        onClick = { modeIndex = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Rounded.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text("Direct")
                    }
                    SegmentedButton(
                        selected = modeIndex == 1,
                        onClick = { modeIndex = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text("Group")
                    }
                }

                if (modeIndex == 1) {
                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { groupTitle = it },
                        label = { Text("Group name") },
                        placeholder = { Text("e.g. Design Crew") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_title_input")
                    )
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search people...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (modeIndex == 0) {
                    // Saved Notes self-chat option
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartDirectChat(me) }
                            .testTag("saved_notes_chat_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bookmark,
                                        contentDescription = "Saved Notes",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Saved Notes (You)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Private space for drafts, voice notes & AI art",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    if (otherUsers.isEmpty()) {
                        item {
                            Text(
                                text = "Other users who sign into PulseChat will appear here automatically. You can also start Saved Notes above right away!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(otherUsers, key = { it.userId }) { user ->
                            val selected = selectedUids.contains(user.userId)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (modeIndex == 0) {
                                            onStartDirectChat(user)
                                        } else {
                                            selectedUids = if (selected) {
                                                selectedUids - user.userId
                                            } else {
                                                selectedUids + user.userId
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val isContact = PrivacyVisibilityResolver.isContactOfViewer(
                                        viewerUid = currentUserId,
                                        targetUid = user.userId,
                                        chats = allChats
                                    )
                                    val visibleOnline = PrivacyVisibilityResolver.isEffectivelyOnline(
                                        target = user,
                                        viewerUid = currentUserId,
                                        isBlockedEitherWay = blockedUserIds.contains(user.userId) ||
                                            blockedEitherWayUserIds.contains(user.userId),
                                        isContact = isContact
                                    )
                                    UserAvatar(
                                        name = user.displayName,
                                        avatarData = user.avatarData,
                                        photoUrl = user.photoUrl,
                                        isOnline = visibleOnline,
                                        size = 42.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = user.bio.ifBlank { "@${user.handle}" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (modeIndex == 1) {
                                        Icon(
                                            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = if (selected) "Selected" else "Not selected",
                                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (modeIndex == 1) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Button(
                        onClick = {
                            val chosen = allUsers.filter { selectedUids.contains(it.userId) }
                            onCreateGroupChat(groupTitle, chosen)
                        },
                        enabled = groupTitle.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_group_button")
                    ) {
                        Text("Create Group (${selectedUids.size + 1} members)")
                    }
                }
            }
        }
    }
}
