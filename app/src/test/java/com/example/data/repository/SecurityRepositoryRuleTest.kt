package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CallType
import com.example.data.model.ChatLockConfig
import com.example.data.model.MessageType
import com.example.data.model.PrivacySettings
import com.example.data.model.PrivacyVisibility
import com.example.data.model.PublicProfile
import com.example.data.model.ReportCategory
import com.example.data.model.ReportStatus
import com.example.data.model.TwoStepSecurityConfig
import com.example.security.E2eeCryptoManager
import com.example.security.PinCryptoUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SecurityRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun keyBundles_andPrivateSecurityConfigs_enforceOwnershipAndDecryptE2ee() = runBlocking {
        val runId = UUID.randomUUID().toString().take(6)
        val aliceEmail = "alice_k_$runId@test.com"
        val bobEmail = "bob_k_$runId@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceSecRepo = SecurityRepository(firestore, auth)
        val aliceKeys = E2eeCryptoManager.generateLocalKeyMaterial(aliceUid, 1)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceSecRepo.publishKeyBundle(aliceKeys.toPublicKeyBundle()).getOrThrow()
            aliceSecRepo.savePrivacySettings(
                PrivacySettings(
                    lastSeenVisibility = PrivacyVisibility.CONTACTS.wireValue,
                    onlineVisibility = PrivacyVisibility.NOBODY.wireValue,
                    defaultDisappearingSeconds = 86_400L,
                    e2eeEnabled = true
                )
            ).getOrThrow()
            val hash = PinCryptoUtils.hashSecret("135790")
            aliceSecRepo.saveTwoStepConfig(
                TwoStepSecurityConfig(
                    enabled = true,
                    pinHash = hash.hash,
                    pinSalt = hash.salt,
                    recoveryEmail = "alice@example.com",
                    registeredDeviceIds = listOf("device_1")
                )
            ).getOrThrow()
            aliceSecRepo.saveChatLockConfig(
                ChatLockConfig(
                    lockedChatIds = listOf("chat_secret"),
                    biometricEnabled = true
                )
            ).getOrThrow()
        }

        val bobUid = signInTestUser(bobEmail)
        val bobSecRepo = SecurityRepository(firestore, auth)
        val bobKeys = E2eeCryptoManager.generateLocalKeyMaterial(bobUid, 1)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobSecRepo.publishKeyBundle(bobKeys.toPublicKeyBundle()).getOrThrow()
        }

        // Bob can fetch Alice's public pre-key bundle and verify signature
        val fetchedAliceBundle = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobSecRepo.getKeyBundle(aliceUid)
        }
        assertNotNull(fetchedAliceBundle)
        assertTrue(E2eeCryptoManager.verifySignedPreKey(fetchedAliceBundle!!))

        // Bob CANNOT read Alice's private 2FA or Chat Lock docs
        val readPrivate = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("security").document("two_step").get().await()
            }
        }
        assertTrue(readPrivate.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            readPrivate.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun blockUser_blocksDirectMessagesAndCallsAndLogsAuditHistory() = runBlocking {
        val runId = UUID.randomUUID().toString().take(6)
        val aliceEmail = "alice_blk_$runId@test.com"
        val bobEmail = "bob_blk_$runId@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceChatRepo = ChatRepository(firestore, auth)
        val aliceSecRepo = SecurityRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.ensureUserProfile("Alice", aliceEmail).getOrThrow()
        }

        val bobUid = signInTestUser(bobEmail)
        val bobChatRepo = ChatRepository(firestore, auth)
        val bobCallRepo = CallRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.ensureUserProfile("Bob", bobEmail).getOrThrow()
        }

        val aliceProfile = PublicProfile(userId = aliceUid, displayName = "Alice", handle = "alice")
        val bobProfile = PublicProfile(userId = bobUid, displayName = "Bob", handle = "bob")

        signInTestUser(aliceEmail)
        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.createOrGetDirectChat(aliceProfile, bobProfile).getOrThrow()
        }

        // Alice blocks Bob
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceSecRepo.blockUser(bobProfile, "Unwanted messages").getOrThrow()
        }
        val historyAfterBlock = withTimeout(FLOW_TIMEOUT_MS) {
            aliceSecRepo.observeBlockHistory(aliceUid).first { it.isNotEmpty() }
        }
        assertEquals("blocked", historyAfterBlock.first().action)

        // Bob is blocked server-side from sending messages OR placing calls to Alice, and receives bidirectional blocked_by signal
        signInTestUser(bobEmail)
        val bobSecRepo = SecurityRepository(firestore, auth)
        val blockedBySet = withTimeout(FLOW_TIMEOUT_MS) {
            bobSecRepo.observeBlockedByUsers(bobUid).first { it.contains(aliceUid) }
        }
        assertTrue(blockedBySet.contains(aliceUid))

        val blockedMsgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.sendMessage(
                chatId = chatId,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "Bob",
                type = MessageType.TEXT,
                text = "Blocked attempt"
            )
        }
        assertTrue(blockedMsgResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            blockedMsgResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        val blockedCallResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobCallRepo.createCallSession(
                chatId = chatId,
                caller = bobProfile,
                callee = aliceProfile,
                callType = CallType.VOICE,
                offerSdp = "v=0\r\ns=BlockedOffer\r\n"
            )
        }
        assertTrue(blockedCallResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            blockedCallResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Alice unblocks Bob -> blocked_by marker is removed and Bob can message again
        signInTestUser(aliceEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceSecRepo.unblockUser(bobUid, "Bob").getOrThrow()
        }
        signInTestUser(bobEmail)
        val clearedBlockedBy = withTimeout(FLOW_TIMEOUT_MS) {
            bobSecRepo.observeBlockedByUsers(bobUid).first { !it.contains(aliceUid) }
        }
        assertTrue(!clearedBlockedBy.contains(aliceUid))

        val allowedMsg = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.sendMessage(
                chatId = chatId,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "Bob",
                type = MessageType.TEXT,
                text = "Now unblocked"
            )
        }
        assertTrue(allowedMsg.isSuccess)
    }

    @Test
    fun disappearingMessages_screenshotNotice_andReportSubmission_respectSecurityRules() = runBlocking {
        val runId = UUID.randomUUID().toString().take(6)
        val aliceEmail = "alice_exp_$runId@test.com"
        val bobEmail = "bob_exp_$runId@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceChatRepo = ChatRepository(firestore, auth)
        val aliceSecRepo = SecurityRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.ensureUserProfile("Alice", aliceEmail).getOrThrow()
        }

        val bobUid = signInTestUser(bobEmail)
        val bobChatRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.ensureUserProfile("Bob", bobEmail).getOrThrow()
        }

        val aliceProfile = PublicProfile(userId = aliceUid, displayName = "Alice", handle = "alice")
        val bobProfile = PublicProfile(userId = bobUid, displayName = "Bob", handle = "bob")

        signInTestUser(aliceEmail)
        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.createOrGetDirectChat(aliceProfile, bobProfile).getOrThrow()
        }

        // Update disappearing timer & emit screenshot system notice
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.updateDisappearingTimer(chatId, listOf(aliceUid, bobUid), "Alice", 86_400L).getOrThrow()
            aliceChatRepo.sendScreenshotNotice(chatId, listOf(aliceUid, bobUid), "Alice", "Nov 14, 10:00 PM").getOrThrow()
        }

        // Bob inserts a message that has already expired -> Bob (or Alice) can purge it via rule check
        signInTestUser(bobEmail)
        val expiredMsgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.sendMessage(
                chatId = chatId,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "Bob",
                type = MessageType.TEXT,
                text = "Already expired ephemeral note",
                expiresAtEpochMs = System.currentTimeMillis() - 5_000L
            ).getOrThrow()
        }

        signInTestUser(aliceEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.purgeExpiredMessages(chatId, listOf(expiredMsgId)).getOrThrow()
        }
        val remainingIds = firestore.collection("chats").document(chatId)
            .collection("messages")
            .whereArrayContains("memberUids", aliceUid)
            .get()
            .await()
            .documents
            .map { it.id }
        assertTrue(!remainingIds.contains(expiredMsgId))

        // Report submission succeeds and non-admin review fails with PERMISSION_DENIED
        val reportId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceSecRepo.submitReport(
                reporterName = "Alice",
                reportedUserId = bobUid,
                reportedUserName = "Bob",
                category = ReportCategory.SPAM,
                description = "Repeated unsolicited links",
                chatId = chatId
            ).getOrThrow()
        }
        assertTrue(reportId.startsWith("rep_"))

        val nonAdminReview = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceSecRepo.reviewReport(reportId, ReportStatus.RESOLVED, "Self-approve")
        }
        assertTrue(nonAdminReview.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            nonAdminReview.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun e2eeMessages_roundTripThroughFirestore_oneToOneGroupOfflineAndKeyRotation() = runBlocking {
        val runId = UUID.randomUUID().toString().take(6)
        val aliceEmail = "alice_e2e_$runId@test.com"
        val bobEmail = "bob_e2e_$runId@test.com"
        val charlieEmail = "charlie_e2e_$runId@test.com"
        val eveEmail = "eve_e2e_$runId@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceChatRepo = ChatRepository(firestore, auth)
        val aliceSecRepo = SecurityRepository(firestore, auth)
        val aliceV1 = E2eeCryptoManager.generateLocalKeyMaterial(aliceUid, keyVersion = 1)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.ensureUserProfile("Alice", aliceEmail).getOrThrow()
            aliceSecRepo.publishKeyBundle(aliceV1.toPublicKeyBundle()).getOrThrow()
        }

        val bobUid = signInTestUser(bobEmail)
        val bobChatRepo = ChatRepository(firestore, auth)
        val bobSecRepo = SecurityRepository(firestore, auth)
        val bobV1 = E2eeCryptoManager.generateLocalKeyMaterial(bobUid, keyVersion = 1)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.ensureUserProfile("Bob", bobEmail).getOrThrow()
            bobSecRepo.publishKeyBundle(bobV1.toPublicKeyBundle()).getOrThrow()
        }

        val charlieUid = signInTestUser(charlieEmail)
        val charlieChatRepo = ChatRepository(firestore, auth)
        val charlieSecRepo = SecurityRepository(firestore, auth)
        val charlieKeys = E2eeCryptoManager.generateLocalKeyMaterial(charlieUid, keyVersion = 1)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieChatRepo.ensureUserProfile("Charlie", charlieEmail).getOrThrow()
            charlieSecRepo.publishKeyBundle(charlieKeys.toPublicKeyBundle()).getOrThrow()
        }

        val eveUid = signInTestUser(eveEmail)
        val eveKeys = E2eeCryptoManager.generateLocalKeyMaterial(eveUid, keyVersion = 1)

        // Verify PublicProfile.isOnline deserializes accurately as true from Firestore
        signInTestUser(aliceEmail)
        val directoryUsers = withTimeout(FLOW_TIMEOUT_MS) {
            aliceChatRepo.observeDiscoverableUsers().first { users ->
                users.any { it.userId == bobUid }
            }
        }
        val bobDirectory = directoryUsers.first { it.userId == bobUid }
        assertTrue("PublicProfile.isOnline must deserialize as true", bobDirectory.isOnline)

        val aliceProfile = PublicProfile(userId = aliceUid, displayName = "Alice", handle = "alice")
        val bobProfile = PublicProfile(userId = bobUid, displayName = "Bob", handle = "bob")
        val charlieProfile = PublicProfile(userId = charlieUid, displayName = "Charlie", handle = "charlie")

        // 1. One-to-One Encrypted Messaging through Firestore
        val dmId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.createOrGetDirectChat(aliceProfile, bobProfile).getOrThrow()
        }
        val dmMsgId = "msg_dm_$runId"
        val secretDmText = "Confidential contract approval #42"
        val secretMedia = "AQIDBAUGBwgJCg=="
        val secretReply = "Can you approve section 4?"
        val dmEnvelope = E2eeCryptoManager.encryptForRecipients(
            chatId = dmId,
            messageId = dmMsgId,
            senderMaterial = aliceV1,
            plaintextText = secretDmText,
            mediaData = secretMedia,
            recipientBundles = listOf(aliceV1.toPublicKeyBundle(), bobV1.toPublicKeyBundle()),
            replyToPreview = secretReply
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.sendMessage(
                chatId = dmId,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "Alice",
                type = MessageType.IMAGE,
                text = "",
                mediaData = null,
                replyTo = com.example.data.model.ChatMessage(
                    id = "prev_msg",
                    senderName = "Bob",
                    type = "text",
                    text = secretReply
                ),
                messageIdOverride = dmMsgId,
                isEncrypted = true,
                encryptionVersion = dmEnvelope.encryptionVersion,
                encryptedPayload = dmEnvelope.encryptedPayload,
                encryptedMediaData = dmEnvelope.encryptedMediaData,
                wrappedKeys = dmEnvelope.wrappedKeys,
                chatPreviewOverride = "Encrypted photo"
            ).getOrThrow()
        }

        // Inspect raw Firestore document to confirm zero plaintext stored on server
        val rawDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("chats").document(dmId)
                .collection("messages").document(dmMsgId).get().await()
        }
        assertEquals(true, rawDoc.getBoolean("isEncrypted"))
        assertEquals("", rawDoc.getString("text"))
        assertEquals("", rawDoc.getString("replyToPreview"))
        assertNull(rawDoc.getString("mediaData"))
        assertTrue(!rawDoc.getString("encryptedPayload").orEmpty().contains(secretDmText))

        // Alice decrypts her own sent message via MessageSecurityBridge (even while keyBundles is empty)
        val aliceBridge = com.example.security.MessageSecurityBridge()
        val aliceRawObserved = withTimeout(FLOW_TIMEOUT_MS) {
            aliceChatRepo.observeMessages(dmId, aliceUid).first { list -> list.any { it.id == dmMsgId } }
        }
        val aliceRawMsg = aliceRawObserved.first { it.id == dmMsgId }
        assertTrue("ChatMessage.isEncrypted must deserialize as true", aliceRawMsg.isEncrypted)

        val aliceDisplayed = aliceBridge.decryptMessagesForDisplay(
            messages = aliceRawObserved,
            currentUid = aliceUid,
            localMaterial = aliceV1,
            keyBundles = emptyMap()
        ).first { it.id == dmMsgId }
        assertEquals(secretDmText, aliceDisplayed.text)
        assertEquals(secretMedia, aliceDisplayed.mediaData)
        assertEquals(secretReply, aliceDisplayed.replyToPreview)
        assertEquals("", aliceDisplayed.decryptionError)

        // Bob signs in on a separate account/device and receives + decrypts the DM message
        signInTestUser(bobEmail)
        val bobBridge = com.example.security.MessageSecurityBridge()
        val bobObserved = withTimeout(FLOW_TIMEOUT_MS) {
            bobChatRepo.observeMessages(dmId, bobUid).first { list -> list.any { it.id == dmMsgId } }
        }
        val bobBundles = withTimeout(FLOW_TIMEOUT_MS) {
            bobSecRepo.observeKeyBundles().first { it.containsKey(aliceUid) }
        }
        val bobDisplayed = bobBridge.decryptMessagesForDisplay(
            messages = bobObserved,
            currentUid = bobUid,
            localMaterial = bobV1,
            keyBundles = bobBundles
        ).first { it.id == dmMsgId }
        assertEquals(secretDmText, bobDisplayed.text)
        assertEquals(secretMedia, bobDisplayed.mediaData)
        assertEquals(secretReply, bobDisplayed.replyToPreview)
        assertEquals("", bobDisplayed.decryptionError)
        assertTrue(!bobDisplayed.text.contains("[Encrypted message]"))

        // 2. Group Chat E2EE + Key Rotation + Offline Decryption
        signInTestUser(aliceEmail)
        val aliceV2 = E2eeCryptoManager.generateLocalKeyMaterial(aliceUid, keyVersion = 2)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceSecRepo.publishKeyBundle(aliceV2.toPublicKeyBundle()).getOrThrow()
        }
        val groupId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.createGroupChat("Core Eng", aliceProfile, listOf(bobProfile, charlieProfile)).getOrThrow()
        }
        val grpMsgId = "msg_grp_$runId"
        val secretGroupText = "Deploy key rotation window: 02:00 UTC"
        val grpEnvelope = E2eeCryptoManager.encryptForRecipients(
            chatId = groupId,
            messageId = grpMsgId,
            senderMaterial = aliceV2,
            plaintextText = secretGroupText,
            mediaData = "",
            recipientBundles = listOf(
                aliceV2.toPublicKeyBundle(),
                bobV1.toPublicKeyBundle(),
                charlieKeys.toPublicKeyBundle()
            )
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.sendMessage(
                chatId = groupId,
                memberUids = listOf(aliceUid, bobUid, charlieUid),
                senderName = "Alice",
                type = MessageType.TEXT,
                text = "",
                messageIdOverride = grpMsgId,
                isEncrypted = true,
                encryptionVersion = grpEnvelope.encryptionVersion,
                encryptedPayload = grpEnvelope.encryptedPayload,
                wrappedKeys = grpEnvelope.wrappedKeys,
                chatPreviewOverride = "Encrypted message"
            ).getOrThrow()
        }

        // Bob rotates his own keys to v2 (keeping v1 in history) and decrypts offline (empty keyBundles)
        val bobV2 = E2eeCryptoManager.generateLocalKeyMaterial(bobUid, keyVersion = 2)
        val bobMaterialsMap = mapOf(
            bobV1.keyFingerprint to bobV1,
            bobV2.keyFingerprint to bobV2
        )
        signInTestUser(bobEmail)
        val bobGroupObserved = withTimeout(FLOW_TIMEOUT_MS) {
            bobChatRepo.observeMessages(groupId, bobUid).first { list -> list.any { it.id == grpMsgId } }
        }
        val bobGroupDisplayed = bobBridge.decryptMessagesForDisplay(
            messages = bobGroupObserved,
            currentUid = bobUid,
            localMaterial = bobV2,
            keyBundles = emptyMap(),
            materialsByFingerprint = bobMaterialsMap
        ).first { it.id == grpMsgId }
        assertEquals(secretGroupText, bobGroupDisplayed.text)
        assertEquals("", bobGroupDisplayed.decryptionError)

        // Charlie also decrypts the group message cleanly
        signInTestUser(charlieEmail)
        val charlieBridge = com.example.security.MessageSecurityBridge()
        val charlieGroupObserved = withTimeout(FLOW_TIMEOUT_MS) {
            charlieChatRepo.observeMessages(groupId, charlieUid).first { list -> list.any { it.id == grpMsgId } }
        }
        val charlieGroupDisplayed = charlieBridge.decryptMessagesForDisplay(
            messages = charlieGroupObserved,
            currentUid = charlieUid,
            localMaterial = charlieKeys,
            keyBundles = emptyMap()
        ).first { it.id == grpMsgId }
        assertEquals(secretGroupText, charlieGroupDisplayed.text)
        assertEquals("", charlieGroupDisplayed.decryptionError)

        // Unauthorized Eve fails decryption and receives a clean error state without fake text
        val eveDisplayed = com.example.security.MessageSecurityBridge().decryptMessagesForDisplay(
            messages = charlieGroupObserved,
            currentUid = eveUid,
            localMaterial = eveKeys,
            keyBundles = emptyMap()
        ).first { it.id == grpMsgId }
        assertEquals("", eveDisplayed.text)
        assertTrue(eveDisplayed.decryptionError.isNotBlank())
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 6000L
    }
}
