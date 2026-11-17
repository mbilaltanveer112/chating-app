package com.example.ui.screens

import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class ChatSearchUtilsTest {

    private val currentUid = "me_uid"
    private val alice = PublicProfile(userId = "alice_uid", displayName = "Alice Khan", handle = "alicek", bio = "Designer")
    private val bob = PublicProfile(userId = "bob_uid", displayName = "Bob Malik", handle = "bobm", bio = "Engineer")
    private val zara = PublicProfile(userId = "zara_uid", displayName = "Zara Tariq", handle = "zarat", bio = "Product Lead")

    private val dmAlice = ChatRoom(
        id = "dm_alice",
        type = "direct",
        title = "Alice Khan",
        memberUids = listOf(currentUid, "alice_uid"),
        memberNames = mapOf(currentUid to "Me", "alice_uid" to "Alice Khan"),
        lastMessageText = "See you at 5pm"
    )

    private val groupDesign = ChatRoom(
        id = "grp_design",
        type = "group",
        title = "Launch Squad",
        memberUids = listOf(currentUid, "alice_uid", "bob_uid"),
        memberNames = mapOf(currentUid to "Me", "alice_uid" to "Alice Khan", "bob_uid" to "Bob Malik"),
        lastMessageText = "Updated the mockups"
    )

    private val archivedGroup = ChatRoom(
        id = "grp_old",
        type = "group",
        title = "Archived Crew",
        memberUids = listOf(currentUid, "bob_uid"),
        memberNames = mapOf(currentUid to "Me", "bob_uid" to "Bob Malik"),
        archivedBy = listOf(currentUid)
    )

    @Test
    fun searchAcrossChatsAndContacts_findsActiveGroupsAndContactsWithoutDuplicates() {
        val allChats = listOf(dmAlice, groupDesign, archivedGroup)
        val contacts = listOf(alice, bob, zara)
        val usersMap = contacts.associateBy { it.userId }

        // Searching "Alice" returns both direct chat with Alice and active group containing Alice (excluding archived)
        val aliceResults = ChatSearchMatcher.searchAcrossChatsAndContacts(
            chats = allChats,
            contacts = contacts,
            currentUserId = currentUid,
            query = "alice",
            filter = ChatListFilter.INBOX,
            usersMap = usersMap
        )
        assertEquals(listOf("grp_design"), aliceResults.groupChats.map { it.id })
        assertEquals(listOf("dm_alice"), aliceResults.directChats.map { it.id })
        assertTrue(aliceResults.contacts.isEmpty()) // Alice already appears in Direct Conversations

        // Searching "Zara" (no existing chat) finds her in Contacts
        val zaraResults = ChatSearchMatcher.searchAcrossChatsAndContacts(
            chats = allChats,
            contacts = contacts,
            currentUserId = currentUid,
            query = "zara",
            filter = ChatListFilter.INBOX,
            usersMap = usersMap
        )
        assertTrue(zaraResults.groupChats.isEmpty())
        assertTrue(zaraResults.directChats.isEmpty())
        assertEquals(listOf("zara_uid"), zaraResults.contacts.map { it.userId })
    }

    @Test
    fun filterChats_groupsChipShowsOnlyActiveGroupConversations() {
        val allChats = listOf(dmAlice, groupDesign, archivedGroup)
        val activeGroups = ChatSearchMatcher.filterChats(
            chats = allChats,
            currentUserId = currentUid,
            query = "",
            filter = ChatListFilter.GROUPS
        )
        assertEquals(listOf("grp_design"), activeGroups.map { it.id })
    }

    @Test
    fun filterAndSearch_excludesLockedChatsAndBlockedContacts() {
        val allChats = listOf(dmAlice, groupDesign, archivedGroup)
        val contacts = listOf(alice, bob, zara)

        val inboxWithoutLocked = ChatSearchMatcher.filterChats(
            chats = allChats,
            currentUserId = currentUid,
            query = "",
            filter = ChatListFilter.INBOX,
            lockedChatIds = setOf("dm_alice")
        )
        assertEquals(listOf("grp_design"), inboxWithoutLocked.map { it.id })

        val searchWithoutLockedOrBlocked = ChatSearchMatcher.searchAcrossChatsAndContacts(
            chats = allChats,
            contacts = contacts,
            currentUserId = currentUid,
            query = "zara",
            filter = ChatListFilter.INBOX,
            lockedChatIds = setOf("dm_alice"),
            blockedUserIds = setOf("zara_uid")
        )
        assertTrue(searchWithoutLockedOrBlocked.contacts.isEmpty())
    }

    @Test
    fun filterChats_archivedChipShowsOnlyUnlockedArchivedConversations() {
        val archivedDm = dmAlice.copy(id = "dm_archived", archivedBy = listOf(currentUid))
        val lockedArchived = archivedGroup.copy(id = "grp_locked_archived")
        val allChats = listOf(dmAlice, groupDesign, archivedGroup, archivedDm, lockedArchived)

        val archivedVisible = ChatSearchMatcher.filterChats(
            chats = allChats,
            currentUserId = currentUid,
            query = "",
            filter = ChatListFilter.ARCHIVED,
            lockedChatIds = setOf("grp_locked_archived")
        )
        assertEquals(listOf("grp_old", "dm_archived"), archivedVisible.map { it.id })
    }

    @Test
    fun chatRoomInactivityHelpers_resolveTimestampsAndCutoffsAccurately() {
        val nowMs = 1_750_000_000_000L
        val tenDaysAgoMs = nowMs - TimeUnit.DAYS.toMillis(10)
        val twoDaysAgoMs = nowMs - TimeUnit.DAYS.toMillis(2)
        val sevenDayCutoff = nowMs - TimeUnit.DAYS.toMillis(7)
        val thirtyDayCutoff = nowMs - TimeUnit.DAYS.toMillis(30)

        val fallbackUpdatedRoom = dmAlice.copy(
            lastMessageAt = null,
            updatedAt = Timestamp(Date(tenDaysAgoMs)),
            createdAt = Timestamp(Date(twoDaysAgoMs))
        )
        assertEquals(tenDaysAgoMs, fallbackUpdatedRoom.lastActivityEpochMs())
        assertTrue(fallbackUpdatedRoom.isInactiveSince(sevenDayCutoff))
        assertFalse(fallbackUpdatedRoom.isInactiveSince(thirtyDayCutoff))

        val fallbackCreatedRoom = dmAlice.copy(
            lastMessageAt = null,
            updatedAt = null,
            createdAt = Timestamp(Date(twoDaysAgoMs))
        )
        assertEquals(twoDaysAgoMs, fallbackCreatedRoom.lastActivityEpochMs())
        assertFalse(fallbackCreatedRoom.isInactiveSince(sevenDayCutoff))

        val activeRecentRoom = dmAlice.copy(
            lastMessageAt = Timestamp(Date(twoDaysAgoMs)),
            updatedAt = Timestamp(Date(tenDaysAgoMs))
        )
        assertEquals(twoDaysAgoMs, activeRecentRoom.lastActivityEpochMs())
        assertFalse(activeRecentRoom.isInactiveSince(sevenDayCutoff))

        val noTimestampRoom = dmAlice.copy(lastMessageAt = null, updatedAt = null, createdAt = null)
        assertEquals(0L, noTimestampRoom.lastActivityEpochMs())
        assertTrue(noTimestampRoom.isInactiveSince(thirtyDayCutoff))
    }
}
