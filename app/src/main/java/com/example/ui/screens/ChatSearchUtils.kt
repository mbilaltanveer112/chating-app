package com.example.ui.screens

import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile

enum class ChatListFilter {
    INBOX,
    GROUPS,
    ARCHIVED
}

data class ChatSearchResults(
    val directChats: List<ChatRoom>,
    val groupChats: List<ChatRoom>,
    val contacts: List<PublicProfile>
) {
    val isEmpty: Boolean
        get() = directChats.isEmpty() && groupChats.isEmpty() && contacts.isEmpty()
}

object ChatSearchMatcher {

    fun filterChats(
        chats: List<ChatRoom>,
        currentUserId: String,
        query: String,
        filter: ChatListFilter,
        usersMap: Map<String, PublicProfile> = emptyMap(),
        lockedChatIds: Set<String> = emptySet()
    ): List<ChatRoom> {
        val q = query.trim().lowercase()
        return chats.filter { room ->
            if (lockedChatIds.contains(room.id)) return@filter false
            val filterMatch = when (filter) {
                ChatListFilter.INBOX -> !room.isArchivedBy(currentUserId)
                ChatListFilter.GROUPS -> !room.isArchivedBy(currentUserId) && room.isGroup
                ChatListFilter.ARCHIVED -> room.isArchivedBy(currentUserId)
            }
            if (!filterMatch) return@filter false
            if (q.isEmpty()) return@filter true

            matchesRoom(room, currentUserId, q, usersMap)
        }
    }

    fun searchAcrossChatsAndContacts(
        chats: List<ChatRoom>,
        contacts: List<PublicProfile>,
        currentUserId: String,
        query: String,
        filter: ChatListFilter,
        usersMap: Map<String, PublicProfile> = emptyMap(),
        lockedChatIds: Set<String> = emptySet(),
        blockedUserIds: Set<String> = emptySet()
    ): ChatSearchResults {
        val matchedChats = filterChats(
            chats = chats,
            currentUserId = currentUserId,
            query = query,
            filter = filter,
            usersMap = usersMap,
            lockedChatIds = lockedChatIds
        )
        val groupChats = matchedChats.filter { it.isGroup }
        val directChats = matchedChats.filter { !it.isGroup }

        val q = query.trim().lowercase()
        if (q.isEmpty() || filter == ChatListFilter.GROUPS || filter == ChatListFilter.ARCHIVED) {
            return ChatSearchResults(
                directChats = directChats,
                groupChats = groupChats,
                contacts = emptyList()
            )
        }

        val existingDirectPartnerUids = directChats
            .mapNotNull { it.otherMemberUid(currentUserId) }
            .toSet()

        val matchedContacts = contacts.filter { profile ->
            profile.userId != currentUserId &&
                !blockedUserIds.contains(profile.userId) &&
                !existingDirectPartnerUids.contains(profile.userId) &&
                (profile.displayName.lowercase().contains(q) ||
                    profile.handle.lowercase().contains(q) ||
                    profile.bio.lowercase().contains(q))
        }

        return ChatSearchResults(
            directChats = directChats,
            groupChats = groupChats,
            contacts = matchedContacts
        )
    }

    private fun matchesRoom(
        room: ChatRoom,
        currentUserId: String,
        normalizedQuery: String,
        usersMap: Map<String, PublicProfile>
    ): Boolean {
        if (room.displayTitleFor(currentUserId).lowercase().contains(normalizedQuery)) return true
        if (room.title.lowercase().contains(normalizedQuery)) return true
        if (room.lastMessageText.lowercase().contains(normalizedQuery)) return true

        val memberNameMatch = room.memberNames.entries.any { (uid, name) ->
            uid != currentUserId && name.lowercase().contains(normalizedQuery)
        }
        if (memberNameMatch) return true

        return room.memberUids.any { uid ->
            if (uid == currentUserId) return@any false
            val profile = usersMap[uid] ?: return@any false
            profile.displayName.lowercase().contains(normalizedQuery) ||
                profile.handle.lowercase().contains(normalizedQuery)
        }
    }
}
