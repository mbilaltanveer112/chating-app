package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.example.data.model.CallDirection
import com.example.data.model.CallSession
import com.example.data.model.CallType
import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import com.example.security.PrivacyVisibilityResolver
import com.example.ui.components.UserAvatar
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import java.text.SimpleDateFormat
import java.util.Locale

private enum class CallHistoryFilter(val label: String) {
    ALL("All"),
    MISSED("Missed"),
    INCOMING("Incoming"),
    OUTGOING("Outgoing")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsHistoryScreen(
    currentUserId: String,
    callHistoryState: UiState<List<CallSession>>,
    usersState: UiState<List<PublicProfile>>,
    allChats: List<ChatRoom> = emptyList(),
    blockedUserIds: Set<String> = emptySet(),
    extraUsersMap: Map<String, PublicProfile> = emptyMap(),
    blockedEitherWayUserIds: Set<String> = blockedUserIds,
    onStartCall: (PublicProfile, CallType) -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(CallHistoryFilter.ALL) }
    var showNewCallSheet by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val usersMap = remember(extraUsersMap, users) { extraUsersMap + users.associateBy { it.userId } }
    val trackedProfiles = remember(users, extraUsersMap) {
        (users + extraUsersMap.values).distinctBy { it.userId }
    }
    var presenceClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trackedProfiles) {
        val now = System.currentTimeMillis()
        presenceClockMs = now
        if (trackedProfiles.any { it.userId != currentUserId && PrivacyVisibilityResolver.isSessionActive(it, now) }) {
            while (true) {
                delay(5000L)
                presenceClockMs = System.currentTimeMillis()
            }
        }
    }
    val callableContacts = remember(users, currentUserId, blockedUserIds) {
        users.filter { it.userId != currentUserId && !blockedUserIds.contains(it.userId) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Calls",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showNewCallSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("new_call_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddIcCall,
                            contentDescription = "Start new call"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewCallSheet = true },
                modifier = Modifier.testTag("new_call_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddIcCall,
                    contentDescription = "New call"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CallHistoryFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("call_filter_${filter.name.lowercase()}")
                    )
                }
            }

            when (callHistoryState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = callHistoryState.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val filtered = remember(callHistoryState.data, selectedFilter, currentUserId) {
                        callHistoryState.data.filter { session ->
                            val dir = session.directionFor(currentUserId)
                            when (selectedFilter) {
                                CallHistoryFilter.ALL -> true
                                CallHistoryFilter.MISSED -> dir == CallDirection.MISSED || dir == CallDirection.REJECTED
                                CallHistoryFilter.INCOMING -> dir == CallDirection.INCOMING
                                CallHistoryFilter.OUTGOING -> dir == CallDirection.OUTGOING
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.widthIn(max = 360.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = if (selectedFilter == CallHistoryFilter.ALL) "No calls yet" else "No ${selectedFilter.label.lowercase()} calls",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Place crystal-clear WebRTC voice or video calls directly with any contact.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { showNewCallSheet = true },
                                    modifier = Modifier.testTag("empty_start_call_button")
                                ) {
                                    Icon(Icons.Rounded.AddIcCall, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start a Call")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { session ->
                                val peerUid = session.peerUidFor(currentUserId)
                                val peerProfile = usersMap[peerUid] ?: PublicProfile(
                                    userId = peerUid,
                                    displayName = session.peerNameFor(currentUserId),
                                    avatarData = session.peerAvatarDataFor(currentUserId),
                                    photoUrl = session.peerPhotoUrlFor(currentUserId)
                                )
                                val direction = session.directionFor(currentUserId)
                                val isMissed = direction == CallDirection.MISSED || direction == CallDirection.REJECTED
                                val dateStr = (session.createdAt ?: session.startedAt)
                                    ?.toDate()
                                    ?.let { dateFormat.format(it) }
                                    ?: "Just now"

                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("call_history_item_${session.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val isContact = PrivacyVisibilityResolver.isContactOfViewer(
                                            viewerUid = currentUserId,
                                            targetUid = peerUid,
                                            chats = allChats
                                        )
                                        val isBlocked = blockedUserIds.contains(peerUid) ||
                                            blockedEitherWayUserIds.contains(peerUid)
                                        val peerOnline = PrivacyVisibilityResolver.isEffectivelyOnline(
                                            target = usersMap[peerUid],
                                            viewerUid = currentUserId,
                                            isBlockedEitherWay = isBlocked,
                                            isContact = isContact,
                                            nowMillis = presenceClockMs
                                        )
                                        UserAvatar(
                                            name = peerProfile.displayName,
                                            avatarData = peerProfile.avatarData,
                                            photoUrl = peerProfile.photoUrl,
                                            isOnline = peerOnline,
                                            size = 48.dp
                                        )

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = peerProfile.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = when (direction) {
                                                        CallDirection.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived
                                                        CallDirection.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade
                                                        CallDirection.MISSED, CallDirection.REJECTED -> Icons.AutoMirrored.Rounded.CallMissed
                                                    },
                                                    contentDescription = direction.label,
                                                    tint = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = session.historySubtitle(currentUserId),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onStartCall(peerProfile, CallType.VOICE) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("redial_voice_${session.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Call,
                                                    contentDescription = "Voice call ${peerProfile.displayName}",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { onStartCall(peerProfile, CallType.VIDEO) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("redial_video_${session.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Videocam,
                                                    contentDescription = "Video call ${peerProfile.displayName}",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteEntry(session.id) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("delete_call_${session.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Delete call record",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (showNewCallSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewCallSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Start a Call",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (callableContacts.isEmpty()) {
                    Text(
                        text = "No other contacts found yet. Invite another account to start calling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(340.dp)
                    ) {
                        items(callableContacts, key = { it.userId }) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val isContact = PrivacyVisibilityResolver.isContactOfViewer(
                                    viewerUid = currentUserId,
                                    targetUid = contact.userId,
                                    chats = allChats
                                )
                                val isBlocked = blockedUserIds.contains(contact.userId) ||
                                    blockedEitherWayUserIds.contains(contact.userId)
                                val visibleOnline = PrivacyVisibilityResolver.isEffectivelyOnline(
                                    target = contact,
                                    viewerUid = currentUserId,
                                    isBlockedEitherWay = isBlocked,
                                    isContact = isContact,
                                    nowMillis = presenceClockMs
                                )
                                UserAvatar(
                                    name = contact.displayName,
                                    avatarData = contact.avatarData,
                                    photoUrl = contact.photoUrl,
                                    isOnline = visibleOnline,
                                    size = 44.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (visibleOnline) "Online" else "@${contact.handle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showNewCallSheet = false
                                        onStartCall(contact, CallType.VOICE)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("sheet_voice_call_${contact.userId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = "Voice call ${contact.displayName}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showNewCallSheet = false
                                        onStartCall(contact, CallType.VIDEO)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("sheet_video_call_${contact.userId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = "Video call ${contact.displayName}",
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
}
