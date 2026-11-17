package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.PublicProfile
import com.example.security.E2eeCryptoManager
import com.example.security.MessageSecurityBridge
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatDetailE2eeDisplayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatDetailScreen_rendersDecryptedPlaintextAndGenuineFailureSeparately() {
        val alice = E2eeCryptoManager.generateLocalKeyMaterial("alice", keyVersion = 1)
        val bob = E2eeCryptoManager.generateLocalKeyMaterial("bob", keyVersion = 1)
        val otherBobDevice = E2eeCryptoManager.generateLocalKeyMaterial("bob", keyVersion = 9)

        val chat = ChatRoom(
            id = "dm_alice_bob",
            type = "direct",
            title = "Alice",
            memberUids = listOf("alice", "bob"),
            memberNames = mapOf("alice" to "Alice", "bob" to "Bob")
        )

        val okEnv = E2eeCryptoManager.encryptForRecipients(
            chatId = chat.id,
            messageId = "msg_ok",
            senderMaterial = alice,
            plaintextText = "Hello Bob, real decrypted message!",
            mediaData = "",
            recipientBundles = listOf(alice.toPublicKeyBundle(), bob.toPublicKeyBundle())
        )
        val failEnv = E2eeCryptoManager.encryptForRecipients(
            chatId = chat.id,
            messageId = "msg_fail",
            senderMaterial = alice,
            plaintextText = "Unreachable ciphertext",
            mediaData = "",
            recipientBundles = listOf(alice.toPublicKeyBundle(), otherBobDevice.toPublicKeyBundle())
        )

        val rawMessages = listOf(
            ChatMessage(
                id = "msg_ok",
                chatId = chat.id,
                senderId = "alice",
                senderName = "Alice",
                memberUids = listOf("alice", "bob"),
                type = "text",
                text = "[Encrypted message]",
                isEncrypted = true,
                encryptionVersion = okEnv.encryptionVersion,
                encryptedPayload = okEnv.encryptedPayload,
                wrappedKeys = okEnv.wrappedKeys,
                createdAt = Timestamp.now()
            ),
            ChatMessage(
                id = "msg_fail",
                chatId = chat.id,
                senderId = "alice",
                senderName = "Alice",
                memberUids = listOf("alice", "bob"),
                type = "text",
                text = "",
                isEncrypted = true,
                encryptionVersion = failEnv.encryptionVersion,
                encryptedPayload = failEnv.encryptedPayload,
                wrappedKeys = failEnv.wrappedKeys,
                createdAt = Timestamp.now()
            )
        )

        val displayMessages = MessageSecurityBridge().decryptMessagesForDisplay(
            messages = rawMessages,
            currentUid = "bob",
            localMaterial = bob,
            keyBundles = mapOf("alice" to alice.toPublicKeyBundle())
        )

        composeTestRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    chatId = chat.id,
                    currentUserId = "bob",
                    chat = chat,
                    allChats = listOf(chat),
                    messagesState = UiState.Success(displayMessages),
                    usersMap = mapOf(
                        "alice" to PublicProfile(userId = "alice", displayName = "Alice", isOnline = true)
                    ),
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

        composeTestRule.onNodeWithText("Hello Bob, real decrypted message!").assertIsDisplayed()
        composeTestRule.onNodeWithText("[Encrypted message]").assertDoesNotExist()
        composeTestRule.onNodeWithText("Unable to decrypt message with your current device keys.")
            .assertIsDisplayed()
    }
}
