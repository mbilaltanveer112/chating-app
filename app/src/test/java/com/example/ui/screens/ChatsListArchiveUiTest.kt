package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ChatsListArchiveUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val currentUid = "me_uid"

    @Test
    fun inboxBanner_archivedTabQuickUnarchive_andBatchArchiveManager_workCorrectly() {
        val nowMs = System.currentTimeMillis()
        val recentRoom = ChatRoom(
            id = "chat_recent",
            type = "direct",
            title = "Alice Active",
            memberUids = listOf(currentUid, "alice_uid"),
            memberNames = mapOf(currentUid to "Me", "alice_uid" to "Alice Active"),
            lastMessageText = "Just chatted today",
            lastMessageAt = Timestamp(Date(nowMs - TimeUnit.HOURS.toMillis(2)))
        )
        val staleRoom = ChatRoom(
            id = "chat_stale",
            type = "direct",
            title = "Bob Dormant",
            memberUids = listOf(currentUid, "bob_uid"),
            memberNames = mapOf(currentUid to "Me", "bob_uid" to "Bob Dormant"),
            lastMessageText = "Old message from last month",
            lastMessageAt = Timestamp(Date(nowMs - TimeUnit.DAYS.toMillis(21)))
        )
        val archivedRoom = ChatRoom(
            id = "chat_archived_1",
            type = "group",
            title = "2024 Offsite",
            memberUids = listOf(currentUid, "alice_uid", "bob_uid"),
            memberNames = mapOf(currentUid to "Me", "alice_uid" to "Alice Active", "bob_uid" to "Bob Dormant"),
            lastMessageText = "Photos uploaded",
            lastMessageSenderId = "alice_uid",
            archivedBy = listOf(currentUid),
            lastMessageAt = Timestamp(Date(nowMs - TimeUnit.DAYS.toMillis(40)))
        )

        var showArchivedState by mutableStateOf(false)
        val toggledRooms = mutableListOf<String>()
        val batchArchivedCalls = mutableListOf<List<String>>()
        val batchUnarchivedCalls = mutableListOf<List<String>>()

        composeTestRule.setContent {
            MaterialTheme {
                ChatsListScreen(
                    currentUserId = currentUid,
                    chatsState = UiState.Success(listOf(recentRoom, staleRoom, archivedRoom)),
                    usersState = UiState.Success(
                        listOf(
                            PublicProfile(userId = "alice_uid", displayName = "Alice Active"),
                            PublicProfile(userId = "bob_uid", displayName = "Bob Dormant")
                        )
                    ),
                    searchQuery = "",
                    showArchived = showArchivedState,
                    onSearchChange = {},
                    onToggleArchived = { showArchivedState = it },
                    onOpenChat = {},
                    onStartDirectChat = {},
                    onTogglePin = {},
                    onToggleArchive = { toggledRooms += it.id },
                    onBatchArchiveChats = { batchArchivedCalls += it },
                    onBatchUnarchiveChats = { batchUnarchivedCalls += it },
                    onDeleteChat = {},
                    onNewChatClick = {},
                    onOpenAiStudioClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Inbox excludes archived chat and renders the Archived entry banner with unread badge
        composeTestRule.onNodeWithTag("chat_item_chat_recent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_item_chat_stale").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_item_chat_archived_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("archived_chats_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 unread").assertIsDisplayed()

        // Open batch Archive Inactive Chats sheet from header button
        composeTestRule.onNodeWithTag("open_archive_manager_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Archive Old or Inactive Chats").assertIsDisplayed()
        // Stale (21 days old) matches default 7+ days preset and is pre-selected; recent (2h) is excluded
        composeTestRule.onNodeWithTag("archive_candidate_chat_stale").assertIsDisplayed()
        composeTestRule.onNodeWithTag("archive_candidate_chat_recent").assertDoesNotExist()
        composeTestRule.onNodeWithTag("confirm_batch_archive_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(listOf("chat_stale")), batchArchivedCalls)

        // Switch to Archived view via the Archived filter chip
        composeTestRule.onNodeWithTag("filter_chip_archived").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_item_chat_archived_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_item_chat_recent").assertDoesNotExist()

        // Quick-unarchive button restores the individual archived room
        composeTestRule.onNodeWithTag("quick_unarchive_chat_archived_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("chat_archived_1"), toggledRooms)
    }

    @Test
    fun archivedTab_restoreAllButton_unarchivesAllArchivedChats() {
        val nowMs = System.currentTimeMillis()
        val archivedOne = ChatRoom(
            id = "arch_1",
            type = "direct",
            title = "Old Contact 1",
            memberUids = listOf(currentUid, "u1"),
            memberNames = mapOf(currentUid to "Me", "u1" to "Old Contact 1"),
            archivedBy = listOf(currentUid),
            lastMessageAt = Timestamp(Date(nowMs - TimeUnit.DAYS.toMillis(15)))
        )
        val archivedTwo = ChatRoom(
            id = "arch_2",
            type = "direct",
            title = "Old Contact 2",
            memberUids = listOf(currentUid, "u2"),
            memberNames = mapOf(currentUid to "Me", "u2" to "Old Contact 2"),
            archivedBy = listOf(currentUid),
            lastMessageAt = Timestamp(Date(nowMs - TimeUnit.DAYS.toMillis(25)))
        )

        val batchUnarchivedCalls = mutableListOf<List<String>>()

        composeTestRule.setContent {
            MaterialTheme {
                ChatsListScreen(
                    currentUserId = currentUid,
                    chatsState = UiState.Success(listOf(archivedOne, archivedTwo)),
                    usersState = UiState.Success(emptyList()),
                    searchQuery = "",
                    showArchived = true,
                    onSearchChange = {},
                    onToggleArchived = {},
                    onOpenChat = {},
                    onStartDirectChat = {},
                    onTogglePin = {},
                    onToggleArchive = {},
                    onBatchArchiveChats = {},
                    onBatchUnarchiveChats = { batchUnarchivedCalls += it },
                    onDeleteChat = {},
                    onNewChatClick = {},
                    onOpenAiStudioClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("archived_section_info_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unarchive_all_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(listOf("arch_1", "arch_2")), batchUnarchivedCalls)
    }
}
