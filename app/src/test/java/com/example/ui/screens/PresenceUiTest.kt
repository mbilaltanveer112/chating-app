package com.example.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.ChatLockConfig
import com.example.data.model.ChatRoom
import com.example.data.model.OnlineVisibility
import com.example.data.model.PrivacySettings
import com.example.data.model.PrivacyVisibility
import com.example.data.model.PublicProfile
import com.example.data.model.SpamGuardState
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PresenceUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatsListScreen_displaysOnlineAndLastSeenStatusRealTime() {
        val now = Date()
        var aliceProfile by mutableStateOf(
            PublicProfile(
                userId = "alice",
                displayName = "Alice",
                isOnline = true,
                lastSeen = Timestamp(now),
                lastHeartbeatAt = Timestamp(now),
                activeSessions = mapOf("dev1" to Timestamp(now))
            )
        )
        val room = ChatRoom(
            id = "dm_me_alice",
            type = "direct",
            title = "Alice",
            memberUids = listOf("me", "alice"),
            memberNames = mapOf("me" to "Me", "alice" to "Alice"),
            lastMessageText = "See you soon!",
            lastMessageSenderId = "alice",
            lastMessageAt = Timestamp(now)
        )

        composeTestRule.setContent {
            MaterialTheme {
                ChatsListScreen(
                    currentUserId = "me",
                    chatsState = UiState.Success(listOf(room)),
                    usersState = UiState.Success(listOf(aliceProfile)),
                    searchQuery = "",
                    showArchived = false,
                    onSearchChange = {},
                    onToggleArchived = {},
                    onOpenChat = {},
                    onStartDirectChat = {},
                    onTogglePin = {},
                    onToggleArchive = {},
                    onToggleLockChat = {},
                    onDeleteChat = {},
                    onUpdateChatTheme = { _, _ -> },
                    onNewChatClick = {},
                    onOpenAiStudioClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_list_presence_dm_me_alice", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_list_online_dot_dm_me_alice", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Online").assertIsDisplayed()

        // Alice goes offline -> updates in real time to Last seen today at ...
        aliceProfile = aliceProfile.copy(
            isOnline = false,
            activeSessions = emptyMap(),
            lastHeartbeatAt = null,
            lastSeen = Timestamp(Date(System.currentTimeMillis() - 180_000L))
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_list_online_dot_dm_me_alice", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("chat_list_presence_dm_me_alice", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Last seen today at", substring = true).assertIsDisplayed()
    }

    @Test
    fun chatDetailHeader_showsOnlineDotAndTransitionsToLastSeen() {
        val now = Date()
        var aliceProfile by mutableStateOf(
            PublicProfile(
                userId = "alice",
                displayName = "Alice",
                isOnline = true,
                lastSeen = Timestamp(now),
                lastHeartbeatAt = Timestamp(now),
                activeSessions = mapOf("dev1" to Timestamp(now))
            )
        )
        val room = ChatRoom(
            id = "dm_me_alice",
            type = "direct",
            title = "Alice",
            memberUids = listOf("me", "alice"),
            memberNames = mapOf("me" to "Me", "alice" to "Alice")
        )

        composeTestRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    chatId = room.id,
                    currentUserId = "me",
                    chat = room,
                    allChats = listOf(room),
                    messagesState = UiState.Success(emptyList()),
                    usersMap = mapOf("alice" to aliceProfile),
                    replyingTo = null,
                    forwardingMessage = null,
                    onBack = {},
                    onSendText = { _, _ -> },
                    onSendMedia = { _, _, _, _, _, _, _, _, _ -> },
                    onSendBitmap = { _, _, _, _ -> },
                    onComposerTextChanged = { _, _ -> },
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

        composeTestRule.onNodeWithTag("chat_header_presence_status").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_header_online_dot").assertIsDisplayed()
        composeTestRule.onNodeWithText("Online").assertIsDisplayed()

        // Alice goes offline -> header shows Last seen today at ...
        aliceProfile = aliceProfile.copy(
            isOnline = false,
            activeSessions = emptyMap(),
            lastHeartbeatAt = null,
            lastSeen = Timestamp(Date(System.currentTimeMillis() - 120_000L))
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_header_online_dot").assertDoesNotExist()
        composeTestRule.onNodeWithText("Last seen today at", substring = true).assertIsDisplayed()
    }

    @Test
    fun privacySecurityScreen_updatesOnlineVisibilityToSameAsLastSeen() {
        var settings by mutableStateOf(
            PrivacySettings(
                lastSeenVisibility = PrivacyVisibility.CONTACTS.wireValue,
                onlineVisibility = OnlineVisibility.EVERYONE.wireValue
            )
        )

        composeTestRule.setContent {
            MaterialTheme {
                PrivacySecurityScreen(
                    currentUserId = "me",
                    privacySettings = settings,
                    twoStepConfig = null,
                    chatLockConfig = ChatLockConfig(),
                    spamGuardState = SpamGuardState(),
                    blockedUsers = emptyList(),
                    blockHistory = emptyList(),
                    localE2eeMaterial = null,
                    allChats = emptyList(),
                    myReports = emptyList(),
                    isAdmin = false,
                    adminReports = emptyList(),
                    lockedVaultUnlocked = false,
                    hasAnySecurityPin = false,
                    onSensitiveScreenChanged = {},
                    onUpdatePrivacySettings = { transform -> settings = transform(settings) },
                    onEnableTwoStep = { _, _, _ -> },
                    onDisableTwoStep = { _, _ -> },
                    onSetChatLockPin = {},
                    onSetChatLockBiometric = {},
                    onVerifyChatLockPin = { false },
                    onSetLockedVaultUnlocked = {},
                    onToggleChatLocked = {},
                    onOpenLockedChat = {},
                    onUnblockUser = { _, _ -> },
                    onRotateE2eeKeys = {},
                    onSubmitProblemReport = { _, _, _ -> },
                    onAdminReviewReport = { _, _, _ -> },
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("row_online_visibility").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Who can see when I'm Online").assertIsDisplayed()
        composeTestRule.onNodeWithTag("online_visibility_option_same_as_last_seen").performClick()
        composeTestRule.waitForIdle()

        assertEquals(OnlineVisibility.SAME_AS_LAST_SEEN.wireValue, settings.onlineVisibility)
    }

    @Test
    fun bidirectionalBlock_whenBlockedByPeer_hidesOnlineAndLastSeenWithoutBlockedLabel() {
        val now = Date()
        val aliceProfile = PublicProfile(
            userId = "alice",
            displayName = "Alice",
            isOnline = true,
            lastSeen = Timestamp(now),
            lastHeartbeatAt = Timestamp(now),
            activeSessions = mapOf("dev1" to Timestamp(now))
        )
        val room = ChatRoom(
            id = "dm_me_alice",
            type = "direct",
            title = "Alice",
            memberUids = listOf("me", "alice"),
            memberNames = mapOf("me" to "Me", "alice" to "Alice"),
            lastMessageText = "Earlier message",
            lastMessageSenderId = "alice",
            lastMessageAt = Timestamp(now)
        )

        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.weight(1f)
                    ) {
                        ChatsListScreen(
                            currentUserId = "me",
                            chatsState = UiState.Success(listOf(room)),
                            usersState = UiState.Success(listOf(aliceProfile)),
                            searchQuery = "",
                            showArchived = false,
                            blockedUserIds = emptySet(),
                            blockedEitherWayUserIds = setOf("alice"),
                            onSearchChange = {},
                            onToggleArchived = {},
                            onOpenChat = {},
                            onStartDirectChat = {},
                            onTogglePin = {},
                            onToggleArchive = {},
                            onToggleLockChat = {},
                            onDeleteChat = {},
                            onUpdateChatTheme = { _, _ -> },
                            onNewChatClick = {},
                            onOpenAiStudioClick = {}
                        )
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.weight(1f)
                    ) {
                        ChatDetailScreen(
                            chatId = room.id,
                            currentUserId = "me",
                            chat = room,
                            allChats = listOf(room),
                            messagesState = UiState.Success(emptyList()),
                            usersMap = mapOf("alice" to aliceProfile),
                            replyingTo = null,
                            forwardingMessage = null,
                            blockedUserIds = emptySet(),
                            blockedEitherWayUserIds = setOf("alice"),
                            onBack = {},
                            onSendText = { _, _ -> },
                            onSendMedia = { _, _, _, _, _, _, _, _, _ -> },
                            onSendBitmap = { _, _, _, _ -> },
                            onComposerTextChanged = { _, _ -> },
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
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_list_presence_dm_me_alice", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("chat_list_online_dot_dm_me_alice", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("chat_header_online_dot").assertDoesNotExist()
        composeTestRule.onNodeWithText("Online").assertDoesNotExist()
        composeTestRule.onNodeWithText("Blocked user").assertDoesNotExist()
        composeTestRule.onNodeWithText("Direct message").assertIsDisplayed()
    }
}
