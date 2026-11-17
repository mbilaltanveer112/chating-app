package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import com.example.ui.components.MessageReadInfoBottomSheet
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReadReceiptUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatDetailScreen_rendersSendingSentDeliveredAndReadStatusesForDirectAndGroupChats() {
        val alice = PublicProfile(userId = "alice", displayName = "Alice")
        val bob = PublicProfile(userId = "bob", displayName = "Bob")
        val usersMap = mapOf("alice" to alice, "bob" to bob)

        var activeRoom by mutableStateOf(
            ChatRoom(
                id = "dm_receipt",
                type = "direct",
                title = "Alice",
                memberUids = listOf("me", "alice"),
                memberNames = mapOf("me" to "Me", "alice" to "Alice")
            )
        )
        var messageState by mutableStateOf(
            ChatMessage(
                id = "m_out",
                chatId = "dm_receipt",
                senderId = "me",
                senderName = "Me",
                memberUids = listOf("me", "alice"),
                type = "text",
                text = "Status test",
                deliveredByUids = listOf("me"),
                readByUids = listOf("me"),
                createdAt = null
            )
        )
        var deliveredCallbackFired = false
        var readCallbackFired = false

        composeTestRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    chatId = activeRoom.id,
                    currentUserId = "me",
                    chat = activeRoom,
                    allChats = listOf(activeRoom),
                    messagesState = UiState.Success(
                        listOf(
                            ChatMessage(
                                id = "m_in",
                                chatId = activeRoom.id,
                                senderId = "alice",
                                senderName = "Alice",
                                memberUids = activeRoom.memberUids,
                                type = "text",
                                text = "Incoming ping",
                                deliveredByUids = listOf("alice"),
                                readByUids = listOf("alice"),
                                createdAt = Timestamp(Date(1_000_000L))
                            ),
                            messageState
                        )
                    ),
                    usersMap = usersMap,
                    replyingTo = null,
                    forwardingMessage = null,
                    e2eeEnabled = false,
                    onBack = {},
                    onSendText = { _, _ -> },
                    onSendMedia = { _, _, _, _, _, _, _, _, _ -> },
                    onSendBitmap = { _, _, _, _ -> },
                    onComposerTextChanged = { _, _ -> },
                    onStopTyping = {},
                    onMarkMessagesDelivered = { _, msgs, _ ->
                        if (msgs.any { it.id == "m_in" }) deliveredCallbackFired = true
                    },
                    onMarkMessagesRead = { _, msgs, _ ->
                        if (msgs.any { it.id == "m_in" }) readCallbackFired = true
                    },
                    onSetReply = {},
                    onSetForward = {},
                    onConfirmForward = {},
                    onToggleReaction = { _, _, _ -> },
                    onDeleteMessage = { _, _ -> },
                    onTogglePin = {},
                    onToggleArchive = {},
                    onDeleteChat = {},
                    onOpenAiStudioWithBitmap = { _, _ -> },
                    onNotify = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(deliveredCallbackFired)
        assertTrue(readCallbackFired)

        // 1. SENDING
        composeTestRule.onNodeWithTag("latest_read_receipt_status").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sending...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("message_receipt_icon_m_out", useUnmergedTree = true).assertIsDisplayed()

        // 2. SENT (server persisted, not yet synced by recipient)
        messageState = messageState.copy(createdAt = Timestamp(Date(1_001_000L)))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sent").assertIsDisplayed()
        composeTestRule.onNode(
            androidx.compose.ui.test.hasTestTag("message_receipt_icon_m_out") and
                androidx.compose.ui.test.hasContentDescription("Sent"),
            useUnmergedTree = true
        ).assertIsDisplayed()

        // 3. DELIVERED (synced by recipient, not yet opened)
        messageState = messageState.copy(
            deliveredByUids = listOf("me", "alice"),
            deliveredTimestamps = mapOf("alice" to Timestamp(Date(1_002_000L)))
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            androidx.compose.ui.test.hasTestTag("message_receipt_icon_m_out") and
                androidx.compose.ui.test.hasContentDescription("Delivered"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Delivered ", substring = true).assertIsDisplayed()

        // 4. READ
        messageState = messageState.copy(
            readByUids = listOf("me", "alice"),
            readTimestamps = mapOf("alice" to Timestamp(Date(1_004_000L)))
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            androidx.compose.ui.test.hasTestTag("message_receipt_icon_m_out") and
                androidx.compose.ui.test.hasContentDescription("Read"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Read ", substring = true).assertIsDisplayed()

        // 5. Group chat transitions: Delivered to Alice -> Delivered to all -> Read by Alice -> Read by all
        val groupRoom = ChatRoom(
            id = "grp_receipt",
            type = "group",
            title = "Team Sync",
            memberUids = listOf("me", "alice", "bob"),
            memberNames = mapOf("me" to "Me", "alice" to "Alice", "bob" to "Bob")
        )
        activeRoom = groupRoom
        messageState = ChatMessage(
            id = "m_grp",
            chatId = groupRoom.id,
            senderId = "me",
            senderName = "Me",
            memberUids = groupRoom.memberUids,
            type = "text",
            text = "Team update",
            deliveredByUids = listOf("me", "alice"),
            readByUids = listOf("me"),
            createdAt = Timestamp(Date(1_005_000L))
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Delivered to Alice").assertIsDisplayed()

        messageState = messageState.copy(deliveredByUids = listOf("me", "alice", "bob"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Delivered to all").assertIsDisplayed()

        messageState = messageState.copy(readByUids = listOf("me", "alice"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Read by Alice").assertIsDisplayed()

        messageState = messageState.copy(readByUids = listOf("me", "alice", "bob"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Read by all").assertIsDisplayed()
    }

    @Test
    fun chatsListScreen_rendersSentDeliveredAndReadSummaryBadges() {
        val now = Timestamp(Date())
        val rooms = listOf(
            ChatRoom(
                id = "dm_sent",
                type = "direct",
                title = "Alice",
                memberUids = listOf("me", "alice"),
                memberNames = mapOf("me" to "Me", "alice" to "Alice"),
                lastMessageText = "Sent preview",
                lastMessageSenderId = "me",
                lastMessageAt = now,
                deliveredByUids = listOf("me"),
                readByUids = listOf("me")
            ),
            ChatRoom(
                id = "dm_delivered",
                type = "direct",
                title = "Bob",
                memberUids = listOf("me", "bob"),
                memberNames = mapOf("me" to "Me", "bob" to "Bob"),
                lastMessageText = "Delivered preview",
                lastMessageSenderId = "me",
                lastMessageAt = now,
                deliveredByUids = listOf("me", "bob"),
                readByUids = listOf("me")
            ),
            ChatRoom(
                id = "grp_read",
                type = "group",
                title = "Ops",
                memberUids = listOf("me", "alice", "bob"),
                memberNames = mapOf("me" to "Me", "alice" to "Alice", "bob" to "Bob"),
                lastMessageText = "Read preview",
                lastMessageSenderId = "me",
                lastMessageAt = now,
                deliveredByUids = listOf("me", "alice", "bob"),
                readByUids = listOf("me", "alice")
            )
        )

        composeTestRule.setContent {
            MaterialTheme {
                ChatsListScreen(
                    currentUserId = "me",
                    chatsState = UiState.Success(rooms),
                    usersState = UiState.Success(
                        listOf(
                            PublicProfile(userId = "alice", displayName = "Alice"),
                            PublicProfile(userId = "bob", displayName = "Bob")
                        )
                    ),
                    searchQuery = "",
                    showArchived = false,
                    onSearchChange = {},
                    onToggleArchived = {},
                    onOpenChat = {},
                    onStartDirectChat = {},
                    onTogglePin = {},
                    onToggleArchive = {},
                    onDeleteChat = {},
                    onNewChatClick = {},
                    onOpenAiStudioClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_list_receipt_dm_sent", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Sent •", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_list_receipt_dm_delivered", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Delivered •", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_list_receipt_grp_read", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Read •", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun messageReadInfoBottomSheet_separatesReadersDeliveredAndPendingMembers() {
        val groupRoom = ChatRoom(
            id = "grp_info",
            type = "group",
            title = "Engineering",
            memberUids = listOf("me", "alice", "bob", "charlie"),
            memberNames = mapOf("me" to "Me", "alice" to "Alice", "bob" to "Bob", "charlie" to "Charlie")
        )
        val usersMap = mapOf(
            "alice" to PublicProfile(userId = "alice", displayName = "Alice"),
            "bob" to PublicProfile(userId = "bob", displayName = "Bob"),
            "charlie" to PublicProfile(userId = "charlie", displayName = "Charlie")
        )
        val msg = ChatMessage(
            id = "m_info",
            chatId = groupRoom.id,
            senderId = "me",
            senderName = "Me",
            memberUids = groupRoom.memberUids,
            type = "text",
            text = "Release checklist",
            deliveredByUids = listOf("me", "alice", "bob"),
            deliveredTimestamps = mapOf(
                "alice" to Timestamp(Date(1_001_000L)),
                "bob" to Timestamp(Date(1_002_000L))
            ),
            readByUids = listOf("me", "alice"),
            readTimestamps = mapOf("alice" to Timestamp(Date(1_003_000L))),
            createdAt = Timestamp(Date(1_000_000L))
        )

        composeTestRule.setContent {
            MaterialTheme {
                MessageReadInfoBottomSheet(
                    message = msg,
                    chat = groupRoom,
                    currentUserId = "me",
                    usersMap = usersMap,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("message_read_info_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read by (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delivered to (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bob").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sent / Pending (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Charlie").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sent to server").assertIsDisplayed()
    }
}
