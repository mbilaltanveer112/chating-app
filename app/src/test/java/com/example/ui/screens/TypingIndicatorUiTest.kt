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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class TypingIndicatorUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatDetailScreen_showsTopBarAndBubbleTypingIndicator_andHidesWhenClearedOrBlocked() {
        val aliceProfile = PublicProfile(userId = "alice", displayName = "Alice", isOnline = true)
        val initialRoom = ChatRoom(
            id = "dm_me_alice",
            type = "direct",
            title = "Alice",
            memberUids = listOf("me", "alice"),
            memberNames = mapOf("me" to "Me", "alice" to "Alice"),
            typingUids = listOf("alice"),
            typingTimestamps = mapOf("alice" to Timestamp(Date()))
        )
        var chatState by mutableStateOf(initialRoom)
        var blockedSet by mutableStateOf(emptySet<String>())
        val composerEvents = mutableListOf<Pair<String, String>>()

        composeTestRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    chatId = chatState.id,
                    currentUserId = "me",
                    chat = chatState,
                    allChats = listOf(chatState),
                    messagesState = UiState.Success(
                        listOf(
                            ChatMessage(
                                id = "m1",
                                chatId = chatState.id,
                                senderId = "alice",
                                senderName = "Alice",
                                memberUids = listOf("me", "alice"),
                                type = "text",
                                text = "Hey there",
                                createdAt = Timestamp.now()
                            )
                        )
                    ),
                    usersMap = mapOf("alice" to aliceProfile),
                    replyingTo = null,
                    forwardingMessage = null,
                    blockedUserIds = blockedSet,
                    e2eeEnabled = false,
                    onBack = {},
                    onSendText = { _, _ -> },
                    onSendMedia = { _, _, _, _, _, _, _, _, _ -> },
                    onSendBitmap = { _, _, _, _ -> },
                    onComposerTextChanged = { id, txt -> composerEvents += id to txt },
                    onStopTyping = {},
                    onMarkMessagesRead = { _, _, _ -> },
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

        // Active remote typing renders in both the header and the message stream bubble
        composeTestRule.onNodeWithTag("top_bar_typing_status").assertIsDisplayed()
        composeTestRule.onNodeWithText("is typing...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("typing_indicator_bubble").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice is typing").assertIsDisplayed()

        // Entering text and inserting a quick emoji notifies onComposerTextChanged
        composeTestRule.onNodeWithTag("message_input").performTextInput("Hello")
        composeTestRule.onNodeWithContentDescription("Toggle emojis").performClick()
        composeTestRule.onNodeWithText("👍").performClick()
        composeTestRule.waitForIdle()
        assertTrue(composerEvents.any { it.first == "dm_me_alice" && it.second.contains("Hello") })
        assertTrue(composerEvents.any { it.first == "dm_me_alice" && it.second.endsWith("👍") })

        // Blocking peer suppresses typing indicator immediately
        blockedSet = setOf("alice")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_bar_typing_status").assertDoesNotExist()
        composeTestRule.onNodeWithTag("typing_indicator_bubble").assertDoesNotExist()

        // Unblocking while typing is cleared keeps typing hidden
        blockedSet = emptySet()
        chatState = initialRoom.copy(typingUids = emptyList(), typingTimestamps = emptyMap())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_bar_typing_status").assertDoesNotExist()
        composeTestRule.onNodeWithTag("typing_indicator_bubble").assertDoesNotExist()
    }

    @Test
    fun chatsListScreen_displaysRealtimeTypingLabelInInboxAndRevertsOnStop() {
        val now = Timestamp(Date())
        var roomsState by mutableStateOf(
            listOf(
                ChatRoom(
                    id = "dm_1",
                    type = "direct",
                    title = "Alice",
                    memberUids = listOf("me", "alice"),
                    memberNames = mapOf("me" to "Me", "alice" to "Alice"),
                    lastMessageText = "Last saved message",
                    typingUids = listOf("alice"),
                    typingTimestamps = mapOf("alice" to now)
                ),
                ChatRoom(
                    id = "grp_1",
                    type = "group",
                    title = "Design Crew",
                    memberUids = listOf("me", "alice", "bob"),
                    memberNames = mapOf("me" to "Me", "alice" to "Alice", "bob" to "Bob"),
                    lastMessageText = "Group preview",
                    typingUids = listOf("alice", "bob"),
                    typingTimestamps = mapOf("alice" to now, "bob" to now)
                )
            )
        )

        composeTestRule.setContent {
            MaterialTheme {
                ChatsListScreen(
                    currentUserId = "me",
                    chatsState = UiState.Success(roomsState),
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

        composeTestRule.onNodeWithTag("chat_list_typing_dm_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("is typing...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_list_typing_grp_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice and Bob are typing...").assertIsDisplayed()

        // When typing stops, previews revert to the last message text
        roomsState = roomsState.map { it.copy(typingUids = emptyList(), typingTimestamps = emptyMap()) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_list_typing_dm_1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Last saved message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Group preview").assertIsDisplayed()
    }
}
