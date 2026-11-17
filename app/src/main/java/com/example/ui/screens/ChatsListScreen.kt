package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import com.example.data.model.ReadReceiptStatus
import com.example.security.PresenceFormatter
import com.example.security.PrivacyVisibilityResolver
import com.example.ui.components.ArchiveInactiveChatsBottomSheet
import com.example.ui.components.ChatCategoryBadge
import com.example.ui.components.ChatThemeBottomSheet
import com.example.ui.components.TypingDots
import com.example.ui.components.UserAvatar
import com.example.ui.theme.ChatAccentOption
import com.example.ui.theme.ChatCategoryTag
import com.example.ui.theme.ChatThemePreference
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatsListScreen(
    currentUserId: String,
    chatsState: UiState<List<ChatRoom>>,
    usersState: UiState<List<PublicProfile>>,
    searchQuery: String,
    showArchived: Boolean,
    isDarkTheme: Boolean = true,
    chatThemes: Map<String, String> = emptyMap(),
    lockedChatIds: Set<String> = emptySet(),
    blockedUserIds: Set<String> = emptySet(),
    extraUsersMap: Map<String, PublicProfile> = emptyMap(),
    blockedEitherWayUserIds: Set<String> = blockedUserIds,
    onSearchChange: (String) -> Unit,
    onToggleArchived: (Boolean) -> Unit,
    onOpenChat: (ChatRoom) -> Unit,
    onStartDirectChat: (PublicProfile) -> Unit,
    onTogglePin: (ChatRoom) -> Unit,
    onToggleArchive: (ChatRoom) -> Unit,
    onBatchArchiveChats: (List<String>) -> Unit = {},
    onBatchUnarchiveChats: (List<String>) -> Unit = {},
    onToggleLockChat: (ChatRoom) -> Unit = {},
    onOpenLockedChatsVault: () -> Unit = {},
    onDeleteChat: (ChatRoom) -> Unit,
    onUpdateChatTheme: (ChatRoom, ChatThemePreference?) -> Unit = { _, _ -> },
    onNewChatClick: () -> Unit,
    onOpenAiStudioClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var showGroupsOnly by remember { mutableStateOf(false) }
    var showArchiveManager by remember { mutableStateOf(false) }
    var themeSheetRoom by remember { mutableStateOf<ChatRoom?>(null) }
    val activeFilter = when {
        showArchived -> ChatListFilter.ARCHIVED
        showGroupsOnly -> ChatListFilter.GROUPS
        else -> ChatListFilter.INBOX
    }

    val allContacts = remember(usersState) {
        (usersState as? UiState.Success)?.data.orEmpty()
    }
    val usersMap = remember(extraUsersMap, allContacts) {
        extraUsersMap + allContacts.associateBy { it.userId }
    }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    var typingClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val allRooms = (chatsState as? UiState.Success)?.data.orEmpty()
    val activeInboxRooms = remember(allRooms, currentUserId, lockedChatIds) {
        allRooms.filter { !lockedChatIds.contains(it.id) && !it.isArchivedBy(currentUserId) }
    }
    val archivedRooms = remember(allRooms, currentUserId, lockedChatIds) {
        allRooms.filter { !lockedChatIds.contains(it.id) && it.isArchivedBy(currentUserId) }
    }
    val archivedUnreadCount = remember(archivedRooms, currentUserId) {
        archivedRooms.count { it.isUnreadFor(currentUserId) }
    }
    val anyRemoteTypingFlag = allRooms.any { room -> room.typingUids.any { it != currentUserId } }
    LaunchedEffect(anyRemoteTypingFlag, allRooms) {
        typingClockMs = System.currentTimeMillis()
        if (anyRemoteTypingFlag) {
            while (true) {
                delay(1000L)
                typingClockMs = System.currentTimeMillis()
            }
        }
    }

    var presenceClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val trackedProfiles = remember(allContacts, extraUsersMap) {
        (allContacts + extraUsersMap.values).distinctBy { it.userId }
    }
    val anyActivePeer = trackedProfiles.any {
        it.userId != currentUserId && PrivacyVisibilityResolver.isSessionActive(it, presenceClockMs)
    }
    LaunchedEffect(anyActivePeer, trackedProfiles) {
        val now = System.currentTimeMillis()
        presenceClockMs = now
        if (trackedProfiles.any { it.userId != currentUserId && PrivacyVisibilityResolver.isSessionActive(it, now) }) {
            while (true) {
                delay(5000L)
                presenceClockMs = System.currentTimeMillis()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChatClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddComment,
                    contentDescription = "New conversation"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PulseChat",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when (activeFilter) {
                            ChatListFilter.ARCHIVED -> "Archived conversations (messages preserved)"
                            ChatListFilter.GROUPS -> "Active group conversations"
                            ChatListFilter.INBOX -> "Real-time messages"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showArchiveManager = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_archive_manager_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = "Archive old or inactive chats",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenAiStudioClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("header_ai_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Open AI Image Studio",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Always-visible top search bar for contacts & active group conversations
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        text = "Search contacts, groups, or messages...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("search_chats_input")
            )

            // Filter Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                FilterChip(
                    selected = activeFilter == ChatListFilter.INBOX,
                    onClick = {
                        showGroupsOnly = false
                        onToggleArchived(false)
                    },
                    label = { Text("Inbox") },
                    modifier = Modifier.testTag("filter_chip_inbox")
                )
                FilterChip(
                    selected = activeFilter == ChatListFilter.GROUPS,
                    onClick = {
                        showGroupsOnly = true
                        onToggleArchived(false)
                    },
                    label = { Text("Groups") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("filter_chip_groups")
                )
                FilterChip(
                    selected = activeFilter == ChatListFilter.ARCHIVED,
                    onClick = {
                        showGroupsOnly = false
                        onToggleArchived(true)
                    },
                    label = {
                        Text(
                            if (archivedRooms.isNotEmpty()) "Archived (${archivedRooms.size})"
                            else "Archived"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("filter_chip_archived")
                )
            }

            when (chatsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = chatsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val isSearching = searchQuery.trim().isNotEmpty()
                    val lockedCount = remember(chatsState.data, lockedChatIds) {
                        chatsState.data.count { lockedChatIds.contains(it.id) }
                    }
                    val flatFilteredChats = remember(chatsState.data, searchQuery, activeFilter, currentUserId, usersMap, lockedChatIds) {
                        ChatSearchMatcher.filterChats(
                            chats = chatsState.data,
                            currentUserId = currentUserId,
                            query = searchQuery,
                            filter = activeFilter,
                            usersMap = usersMap,
                            lockedChatIds = lockedChatIds
                        )
                    }
                    val combinedBlockedIds = remember(blockedUserIds, blockedEitherWayUserIds) {
                        blockedUserIds + blockedEitherWayUserIds
                    }
                    val searchResults = remember(chatsState.data, allContacts, searchQuery, activeFilter, currentUserId, usersMap, lockedChatIds, combinedBlockedIds) {
                        ChatSearchMatcher.searchAcrossChatsAndContacts(
                            chats = chatsState.data,
                            contacts = allContacts,
                            currentUserId = currentUserId,
                            query = searchQuery,
                            filter = activeFilter,
                            usersMap = usersMap,
                            lockedChatIds = lockedChatIds,
                            blockedUserIds = combinedBlockedIds
                        )
                    }

                    val showLockedBanner = !isSearching && activeFilter == ChatListFilter.INBOX && lockedCount > 0
                    if (showLockedBanner) {
                        LockedChatsEntryBanner(
                            lockedCount = lockedCount,
                            onClick = onOpenLockedChatsVault
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                    }

                    val showArchivedEntryBanner = !isSearching && activeFilter == ChatListFilter.INBOX && archivedRooms.isNotEmpty()
                    if (showArchivedEntryBanner) {
                        ArchivedChatsEntryBanner(
                            archivedCount = archivedRooms.size,
                            unreadCount = archivedUnreadCount,
                            onClick = {
                                showGroupsOnly = false
                                onToggleArchived(true)
                            }
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                    }

                    if (!isSearching && activeFilter == ChatListFilter.ARCHIVED && archivedRooms.isNotEmpty()) {
                        ArchivedSectionHeaderCard(
                            archivedCount = archivedRooms.size,
                            hasActiveInboxChats = activeInboxRooms.isNotEmpty(),
                            onOpenArchiveManager = { showArchiveManager = true },
                            onUnarchiveAll = {
                                onBatchUnarchiveChats(archivedRooms.map { it.id })
                            }
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                    }

                    val showEmptyState = if (isSearching) searchResults.isEmpty else flatFilteredChats.isEmpty()

                    if (showEmptyState) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when {
                                                isSearching -> Icons.Rounded.SearchOff
                                                activeFilter == ChatListFilter.ARCHIVED -> Icons.Rounded.Archive
                                                else -> Icons.Rounded.Forum
                                            },
                                            contentDescription = "No conversations",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = when {
                                        isSearching -> "No matches for \"${searchQuery.trim()}\""
                                        activeFilter == ChatListFilter.ARCHIVED -> "No archived chats"
                                        activeFilter == ChatListFilter.GROUPS -> "No active group conversations"
                                        else -> "Start your first conversation"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = when {
                                        isSearching -> "Try another contact name, group title, or message keyword."
                                        activeFilter == ChatListFilter.ARCHIVED -> "Swipe left on any conversation or tap Archive Inactive Chats to tuck old threads away without deleting messages."
                                        activeFilter == ChatListFilter.GROUPS -> "Tap + to create a group conversation with your contacts."
                                        else -> "Tap + to message someone, create a group, or open Saved Notes."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSearching) {
                                    TextButton(onClick = { onSearchChange("") }) {
                                        Text("Clear Search")
                                    }
                                } else if (activeFilter == ChatListFilter.ARCHIVED) {
                                    if (activeInboxRooms.isNotEmpty()) {
                                        TextButton(
                                            onClick = { showArchiveManager = true },
                                            modifier = Modifier.testTag("empty_archived_manage_button")
                                        ) {
                                            Text("Archive Inactive Chats")
                                        }
                                    } else {
                                        TextButton(onClick = { onToggleArchived(false) }) {
                                            Text("Back to Inbox")
                                        }
                                    }
                                } else {
                                    TextButton(onClick = onNewChatClick) {
                                        Text("Start a Chat")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isSearching) {
                                if (searchResults.groupChats.isNotEmpty()) {
                                    item(key = "section_groups") {
                                        SearchSectionHeader(title = "Group Conversations (${searchResults.groupChats.size})")
                                    }
                                    items(searchResults.groupChats, key = { "grp_${it.id}" }) { room ->
                                        ChatRoomListItem(
                                            room = room,
                                            allRooms = allRooms,
                                            currentUserId = currentUserId,
                                            usersMap = usersMap,
                                            blockedUserIds = blockedUserIds,
                                            blockedEitherWayUserIds = blockedEitherWayUserIds,
                                            isLocked = lockedChatIds.contains(room.id),
                                            isDarkTheme = isDarkTheme,
                                            themeSpec = chatThemes[room.id],
                                            typingClockMs = typingClockMs,
                                            presenceClockMs = presenceClockMs,
                                            timeFormatter = timeFormatter,
                                            onOpenChat = onOpenChat,
                                            onTogglePin = onTogglePin,
                                            onToggleArchive = onToggleArchive,
                                            onToggleLock = onToggleLockChat,
                                            onCustomizeTheme = { themeSheetRoom = it },
                                            onDeleteChat = onDeleteChat
                                        )
                                    }
                                }

                                if (searchResults.directChats.isNotEmpty()) {
                                    item(key = "section_direct") {
                                        SearchSectionHeader(title = "Direct Conversations (${searchResults.directChats.size})")
                                    }
                                    items(searchResults.directChats, key = { "dm_${it.id}" }) { room ->
                                        ChatRoomListItem(
                                            room = room,
                                            allRooms = allRooms,
                                            currentUserId = currentUserId,
                                            usersMap = usersMap,
                                            blockedUserIds = blockedUserIds,
                                            blockedEitherWayUserIds = blockedEitherWayUserIds,
                                            isLocked = lockedChatIds.contains(room.id),
                                            isDarkTheme = isDarkTheme,
                                            themeSpec = chatThemes[room.id],
                                            typingClockMs = typingClockMs,
                                            presenceClockMs = presenceClockMs,
                                            timeFormatter = timeFormatter,
                                            onOpenChat = onOpenChat,
                                            onTogglePin = onTogglePin,
                                            onToggleArchive = onToggleArchive,
                                            onToggleLock = onToggleLockChat,
                                            onCustomizeTheme = { themeSheetRoom = it },
                                            onDeleteChat = onDeleteChat
                                        )
                                    }
                                }

                                if (searchResults.contacts.isNotEmpty()) {
                                    item(key = "section_contacts") {
                                        SearchSectionHeader(title = "Contacts (${searchResults.contacts.size})")
                                    }
                                    items(searchResults.contacts, key = { "contact_${it.userId}" }) { contact ->
                                        val isContact = PrivacyVisibilityResolver.isContactOfViewer(
                                            viewerUid = currentUserId,
                                            targetUid = contact.userId,
                                            chats = allRooms
                                        )
                                        val visibleOnline = PrivacyVisibilityResolver.isEffectivelyOnline(
                                            target = contact,
                                            viewerUid = currentUserId,
                                            isBlockedEitherWay = blockedEitherWayUserIds.contains(contact.userId),
                                            isContact = isContact,
                                            nowMillis = presenceClockMs
                                        )
                                        ContactSearchResultItem(
                                            contact = contact,
                                            isOnline = visibleOnline,
                                            onClick = {
                                                focusManager.clearFocus()
                                                onStartDirectChat(contact)
                                            }
                                        )
                                    }
                                }
                            } else {
                                items(flatFilteredChats, key = { it.id }) { room ->
                                    ChatRoomListItem(
                                        room = room,
                                        allRooms = allRooms,
                                        currentUserId = currentUserId,
                                        usersMap = usersMap,
                                        blockedUserIds = blockedUserIds,
                                        blockedEitherWayUserIds = blockedEitherWayUserIds,
                                        isLocked = lockedChatIds.contains(room.id),
                                        isDarkTheme = isDarkTheme,
                                        themeSpec = chatThemes[room.id],
                                        typingClockMs = typingClockMs,
                                        presenceClockMs = presenceClockMs,
                                        timeFormatter = timeFormatter,
                                        onOpenChat = onOpenChat,
                                        onTogglePin = onTogglePin,
                                        onToggleArchive = onToggleArchive,
                                        onToggleLock = onToggleLockChat,
                                        onCustomizeTheme = { themeSheetRoom = it },
                                        onDeleteChat = onDeleteChat
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showArchiveManager) {
        ArchiveInactiveChatsBottomSheet(
            activeChats = activeInboxRooms,
            currentUserId = currentUserId,
            usersMap = usersMap,
            onArchiveSelected = { ids -> onBatchArchiveChats(ids) },
            onDismiss = { showArchiveManager = false }
        )
    }

    val targetThemeRoom = themeSheetRoom
    if (targetThemeRoom != null) {
        val currentPref = remember(targetThemeRoom.id, chatThemes) {
            ChatThemePreference.fromWireString(chatThemes[targetThemeRoom.id])
        }
        ChatThemeBottomSheet(
            chatTitle = targetThemeRoom.displayTitleFor(currentUserId),
            initialTheme = currentPref,
            isDarkTheme = isDarkTheme,
            onApplyTheme = { pref -> onUpdateChatTheme(targetThemeRoom, pref) },
            onDismiss = { themeSheetRoom = null }
        )
    }
}

@Composable
private fun ArchivedChatsEntryBanner(
    archivedCount: Int,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("archived_chats_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Archive,
                    contentDescription = "Archived Chats",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Archived",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$archivedCount saved conversation(s) • Out of main inbox",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (unreadCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "$unreadCount unread",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "View",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ArchivedSectionHeaderCard(
    archivedCount: Int,
    hasActiveInboxChats: Boolean,
    onOpenArchiveManager: () -> Unit,
    onUnarchiveAll: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("archived_section_info_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$archivedCount archived • Swipe left or tap icon to restore",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Messages and history stay safely preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasActiveInboxChats) {
                    TextButton(
                        onClick = onOpenArchiveManager,
                        modifier = Modifier.testTag("archived_header_add_button")
                    ) {
                        Text("Archive more")
                    }
                }
                if (archivedCount > 1) {
                    TextButton(
                        onClick = onUnarchiveAll,
                        modifier = Modifier.testTag("unarchive_all_button")
                    ) {
                        Text("Restore all")
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedChatsEntryBanner(
    lockedCount: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("locked_chats_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Locked Chats",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Locked Chats",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$lockedCount hidden conversation(s) • Biometric or PIN required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
    )
}

@Composable
private fun ContactSearchResultItem(
    contact: PublicProfile,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("contact_search_item_${contact.userId}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            UserAvatar(
                name = contact.displayName,
                avatarData = contact.avatarData,
                photoUrl = contact.photoUrl,
                isOnline = isOnline,
                size = 48.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.bio.ifBlank { "@${contact.handle}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Message",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomListItem(
    room: ChatRoom,
    allRooms: List<ChatRoom>,
    currentUserId: String,
    usersMap: Map<String, PublicProfile>,
    blockedUserIds: Set<String>,
    blockedEitherWayUserIds: Set<String>,
    isLocked: Boolean,
    isDarkTheme: Boolean,
    themeSpec: String?,
    typingClockMs: Long,
    presenceClockMs: Long,
    timeFormatter: SimpleDateFormat,
    onOpenChat: (ChatRoom) -> Unit,
    onTogglePin: (ChatRoom) -> Unit,
    onToggleArchive: (ChatRoom) -> Unit,
    onToggleLock: (ChatRoom) -> Unit,
    onCustomizeTheme: (ChatRoom) -> Unit,
    onDeleteChat: (ChatRoom) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val otherUid = room.otherMemberUid(currentUserId)
    val otherProfile = otherUid?.let { usersMap[it] }
    val isBlockedByMe = otherUid != null && blockedUserIds.contains(otherUid)
    val isBlockedEitherWay = otherUid != null && (blockedUserIds.contains(otherUid) || blockedEitherWayUserIds.contains(otherUid))
    val isContact = remember(otherUid, allRooms, currentUserId) {
        if (otherUid == null) false
        else PrivacyVisibilityResolver.isContactOfViewer(currentUserId, otherUid, allRooms)
    }
    val isPeerOnline = remember(otherProfile, otherUid, isBlockedEitherWay, isContact, currentUserId, presenceClockMs) {
        if (otherProfile == null) false
        else {
            PrivacyVisibilityResolver.isEffectivelyOnline(
                target = otherProfile,
                viewerUid = currentUserId,
                isBlockedEitherWay = isBlockedEitherWay,
                isContact = isContact,
                nowMillis = presenceClockMs
            )
        }
    }
    val presenceLabel = remember(otherProfile, otherUid, isBlockedEitherWay, isContact, currentUserId, presenceClockMs) {
        if (otherProfile == null || isBlockedEitherWay) {
            ""
        } else {
            val lastSeenMs = PrivacyVisibilityResolver.effectiveLastSeenMillis(otherProfile)
            val formatted = lastSeenMs?.let {
                PresenceFormatter.formatRelativeLastSeen(it, nowMillis = presenceClockMs)
            }.orEmpty()
            PrivacyVisibilityResolver.resolvePresenceSubtitle(
                target = otherProfile,
                viewerUid = currentUserId,
                isBlockedEitherWay = isBlockedEitherWay,
                isContact = isContact,
                formattedLastSeen = formatted,
                nowMillis = presenceClockMs
            )
        }
    }
    val roomTitle = room.displayTitleFor(currentUserId)
    val isPinned = room.isPinnedBy(currentUserId)
    val isArchived = room.isArchivedBy(currentUserId)
    val isUnread = room.isUnreadFor(currentUserId)
    val roomTheme = remember(themeSpec) { ChatThemePreference.fromWireString(themeSpec) }
    val roomAccent = if (roomTheme.accent == ChatAccentOption.DEFAULT) {
        MaterialTheme.colorScheme.primary
    } else {
        roomTheme.accent.accentColor(isDarkTheme)
    }
    val hasCustomTheme = !roomTheme.isDefault
    val activeTypers = room.activeTypingUids(currentUserId, typingClockMs)
    val othersTyping = activeTypers.isNotEmpty() && !isBlockedEitherWay
    val typingText = if (room.isGroup) {
        "${room.typingSummaryLabel(currentUserId, usersMap, typingClockMs)}..."
    } else {
        "is typing..."
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onToggleArchive(room)
            }
            false
        }
    )

    Box {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isArchived) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isArchived) "Unarchive" else "Archive",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isArchived) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Icon(
                            imageVector = if (isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                            contentDescription = if (isArchived) "Unarchive chat" else "Archive chat",
                            tint = if (isArchived) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when {
                    isUnread -> roomAccent.copy(alpha = 0.14f)
                    hasCustomTheme -> roomAccent.copy(alpha = if (isDarkTheme) 0.08f else 0.05f)
                    else -> MaterialTheme.colorScheme.surface
                },
                border = if (hasCustomTheme) {
                    BorderStroke(1.5.dp, roomAccent.copy(alpha = 0.55f))
                } else {
                    null
                },
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenChat(room) },
                        onLongClick = { menuExpanded = true }
                    )
                    .testTag("chat_item_${room.id}")
            ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                UserAvatar(
                    name = roomTitle,
                    avatarData = otherProfile?.avatarData.orEmpty(),
                    photoUrl = otherProfile?.photoUrl.orEmpty(),
                    isOnline = isPeerOnline,
                    isGroup = room.isGroup,
                    size = 52.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isPinned) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pinned",
                                    tint = roomAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = roomTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (roomTheme.tag != ChatCategoryTag.NONE) {
                                ChatCategoryBadge(
                                    tag = roomTheme.tag,
                                    accentColor = roomAccent
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (room.disappearingDurationSeconds > 0L) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = "Disappearing messages active",
                                    tint = roomAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            if (isBlockedByMe) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Blocked user",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            val timeStr = room.lastMessageAt?.toDate()?.let { timeFormatter.format(it) }.orEmpty()
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUnread) roomAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!room.isGroup && presenceLabel.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.testTag("chat_list_presence_${room.id}")
                        ) {
                            if (isPeerOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                        .testTag("chat_list_online_dot_${room.id}")
                                )
                            }
                            Text(
                                text = presenceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isPeerOnline) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isPeerOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (othersTyping) {
                                TypingDots(
                                    color = roomAccent,
                                    dotSize = 4.dp
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                            } else if (room.lastMessageSenderId == currentUserId) {
                                val receiptStatus = room.lastMessageStatusFor(currentUserId) ?: ReadReceiptStatus.SENT
                                val (icon, desc, statusPrefix) = when (receiptStatus) {
                                    ReadReceiptStatus.SENDING -> Triple(Icons.Rounded.Schedule, "Sending", "Sending •")
                                    ReadReceiptStatus.SENT -> Triple(Icons.Rounded.Done, "Sent", "Sent •")
                                    ReadReceiptStatus.DELIVERED -> Triple(Icons.Rounded.DoneAll, "Delivered", "Delivered •")
                                    ReadReceiptStatus.READ -> Triple(Icons.Rounded.DoneAll, "Read", "Read •")
                                }
                                val isRead = receiptStatus == ReadReceiptStatus.READ
                                val tintColor = if (isRead) roomAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.testTag("chat_list_receipt_${room.id}")
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = desc,
                                        tint = tintColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = statusPrefix,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isRead) FontWeight.SemiBold else FontWeight.Normal,
                                        color = tintColor
                                    )
                                }
                            }
                            val normalizedPreview = if (room.lastMessageText == "[Encrypted message]") {
                                "Encrypted message"
                            } else {
                                room.lastMessageText
                            }
                            val isEncryptedPreview = !othersTyping && (
                                room.lastMessageText == "[Encrypted message]" ||
                                    room.lastMessageText == "Encrypted message" ||
                                    room.lastMessageText.startsWith("Encrypted ")
                                )
                            if (isEncryptedPreview) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "End-to-end encrypted",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = when {
                                    othersTyping -> typingText
                                    normalizedPreview.isNotBlank() -> normalizedPreview
                                    else -> "Say hello!"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (othersTyping) FontWeight.Medium else FontWeight.Normal,
                                color = if (othersTyping) roomAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (othersTyping) {
                                    Modifier.testTag("chat_list_typing_${room.id}")
                                } else {
                                    Modifier
                                }
                            )
                        }

                        if (isArchived) {
                            IconButton(
                                onClick = { onToggleArchive(room) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("quick_unarchive_${room.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Unarchive,
                                    contentDescription = "Unarchive chat",
                                    tint = roomAccent
                                )
                            }
                        } else if (isUnread) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = roomAccent,
                                modifier = Modifier.size(10.dp)
                            ) {}
                        }
                    }
                }
            }
        }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Customize theme") },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = roomAccent
                    )
                },
                onClick = {
                    menuExpanded = false
                    onCustomizeTheme(room)
                }
            )
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin chat" else "Pin chat") },
                leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onTogglePin(room)
                }
            )
            DropdownMenuItem(
                text = { Text(if (isLocked) "Unlock chat" else "Lock chat") },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onToggleLock(room)
                }
            )
            DropdownMenuItem(
                text = { Text(if (isArchived) "Unarchive" else "Archive") },
                leadingIcon = {
                    Icon(
                        if (isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                        contentDescription = null
                    )
                },
                onClick = {
                    menuExpanded = false
                    onToggleArchive(room)
                }
            )
            DropdownMenuItem(
                text = { Text("Delete chat") },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDeleteChat(room)
                }
            )
        }
    }
}
