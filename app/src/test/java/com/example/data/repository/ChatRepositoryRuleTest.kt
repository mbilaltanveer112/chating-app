package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MessageType
import com.example.data.model.PublicProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_andCrossUserPrivateRead_enforcesIsolation() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)

        val ensureResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", ALICE_EMAIL)
        }
        assertTrue(ensureResult.isSuccess)

        val alicePrivate = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePrivateUser(aliceUid).first { it != null }
        }
        assertEquals("Alice", alicePrivate?.displayName)

        // Bob cannot read Alice's private user document
        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice private user doc")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsErr)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
        }
    }

    @Test
    fun directChat_andRealtimeMessaging_succeedsForMembersAndDeniesNonMembers() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", ALICE_EMAIL).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfile("Bob", BOB_EMAIL).getOrThrow()
        }

        val aliceProfile = PublicProfile(userId = aliceUid, displayName = "Alice", handle = "alice")
        val bobProfile = PublicProfile(userId = bobUid, displayName = "Bob", handle = "bob")

        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.createOrGetDirectChat(bobProfile, aliceProfile).getOrThrow()
        }

        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.sendMessage(
                chatId = chatId,
                memberUids = listOf(bobUid, aliceUid),
                senderName = "Bob",
                type = MessageType.VOICE,
                text = "Voice note",
                durationMs = 4200L,
                waveform = listOf(12, 48, 80, 35)
            ).getOrThrow()
        }
        assertTrue(msgId.isNotEmpty())

        // Alice observes both chat and message
        signInTestUser(ALICE_EMAIL)
        val aliceChats = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChats(aliceUid).first { list -> list.any { it.id == chatId } }
        }
        assertTrue(aliceChats.any { it.id == chatId })

        val aliceMessages = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(chatId, aliceUid).first { list -> list.any { it.id == msgId } }
        }
        assertEquals("Voice note", aliceMessages.first { it.id == msgId }.text)

        // Charlie (non-member) cannot observe Alice & Bob's messages
        signInTestUser("charlie_${UUID.randomUUID().toString().take(6)}@test.com")
        val charlieRepo = ChatRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                charlieRepo.observeMessages(chatId, aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Charlie observes Alice & Bob messages")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsErr)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
        }
    }

    @Test
    fun observeChats_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ChatRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeChats("unauth_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated observeChats")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsErr)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
        }
    }

    @Test
    fun setTyping_directAndGroupChats_updatesTypingStateAndRejectsNonMembers() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", ALICE_EMAIL).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfile("Bob", BOB_EMAIL).getOrThrow()
        }

        val charlieEmail = "charlie_typing_${UUID.randomUUID().toString().take(6)}@test.com"
        val charlieUid = signInTestUser(charlieEmail)
        val charlieRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieRepo.ensureUserProfile("Charlie", charlieEmail).getOrThrow()
        }

        val aliceProfile = PublicProfile(userId = aliceUid, displayName = "Alice", handle = "alice")
        val bobProfile = PublicProfile(userId = bobUid, displayName = "Bob", handle = "bob")
        val charlieProfile = PublicProfile(userId = charlieUid, displayName = "Charlie", handle = "charlie")

        // 1. Private chat typing indicator
        signInTestUser(BOB_EMAIL)
        val directChatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.createOrGetDirectChat(bobProfile, aliceProfile).getOrThrow()
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.setTyping(directChatId, true).getOrThrow()
        }

        signInTestUser(ALICE_EMAIL)
        val directTypingRoom = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(directChatId).first { it?.activeTypingUids(aliceUid)?.contains(bobUid) == true }
        }
        assertNotNull(directTypingRoom)
        assertTrue(directTypingRoom!!.typingTimestamps.containsKey(bobUid))
        assertEquals("Bob is typing", directTypingRoom.typingSummaryLabel(aliceUid))

        // Bob stops typing
        signInTestUser(BOB_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.setTyping(directChatId, false).getOrThrow()
        }
        signInTestUser(ALICE_EMAIL)
        val clearedDirectRoom = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(directChatId).first { it != null && it.typingUids.isEmpty() }
        }
        assertTrue(clearedDirectRoom!!.typingTimestamps.isEmpty())

        // Bob starts typing again and sends a message -> sendMessage clears Bob's typing state automatically
        signInTestUser(BOB_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.setTyping(directChatId, true).getOrThrow()
        }
        signInTestUser(ALICE_EMAIL)
        withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(directChatId).first { it?.activeTypingUids(aliceUid)?.contains(bobUid) == true }
        }
        signInTestUser(BOB_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.sendMessage(
                chatId = directChatId,
                memberUids = listOf(bobUid, aliceUid),
                senderName = "Bob",
                type = MessageType.TEXT,
                text = "Finished typing this!"
            ).getOrThrow()
        }
        signInTestUser(ALICE_EMAIL)
        val autoClearedAfterSend = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(directChatId).first {
                it != null && it.lastMessageText == "Finished typing this!" && !it.typingUids.contains(bobUid)
            }
        }!!
        assertTrue(!autoClearedAfterSend.typingTimestamps.containsKey(bobUid))

        // 2. Group chat multi-user typing indicator
        val groupId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createGroupChat("Launch Team", aliceProfile, listOf(bobProfile, charlieProfile)).getOrThrow()
        }
        signInTestUser(BOB_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.setTyping(groupId, true).getOrThrow()
        }
        signInTestUser(charlieEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieRepo.setTyping(groupId, true).getOrThrow()
        }

        signInTestUser(ALICE_EMAIL)
        val groupTypingRoom = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(groupId).first { room ->
                val active = room?.activeTypingUids(aliceUid).orEmpty()
                active.contains(bobUid) && active.contains(charlieUid)
            }
        }
        assertEquals(
            "Bob and Charlie are typing",
            groupTypingRoom!!.typingSummaryLabel(aliceUid)
        )

        // 3. Non-member cannot set typing on direct chat
        val outsiderEmail = "outsider_${UUID.randomUUID().toString().take(6)}@test.com"
        signInTestUser(outsiderEmail)
        val outsiderRepo = ChatRepository(firestore, auth)
        val outsiderResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            outsiderRepo.setTyping(directChatId, true)
        }
        assertTrue(outsiderResult.isFailure)
        val fsErr = outsiderResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    @Test
    fun markMessagesRead_directAndGroupChats_updatesReadStatusAndTimestamps() = runBlocking {
        val runSuffix = UUID.randomUUID().toString().take(6)
        val aliceEmail = "alice_read_$runSuffix@test.com"
        val bobEmail = "bob_read_$runSuffix@test.com"
        val charlieEmail = "charlie_read_$runSuffix@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", aliceEmail).getOrThrow()
        }

        val bobUid = signInTestUser(bobEmail)
        val bobRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfile("Bob", bobEmail).getOrThrow()
        }

        val charlieUid = signInTestUser(charlieEmail)
        val charlieRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieRepo.ensureUserProfile("Charlie", charlieEmail).getOrThrow()
        }

        val aliceProfile = PublicProfile(userId = aliceUid, displayName = "Alice", handle = "alice")
        val bobProfile = PublicProfile(userId = bobUid, displayName = "Bob", handle = "bob")
        val charlieProfile = PublicProfile(userId = charlieUid, displayName = "Charlie", handle = "charlie")

        // 1. Direct chat: Alice sends 2 messages; before Bob syncs they are SENT
        signInTestUser(aliceEmail)
        val directChatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createOrGetDirectChat(aliceProfile, bobProfile).getOrThrow()
        }
        val m1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendMessage(
                chatId = directChatId,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "Alice",
                type = MessageType.TEXT,
                text = "First message"
            ).getOrThrow()
        }
        val m2 = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendMessage(
                chatId = directChatId,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "Alice",
                type = MessageType.TEXT,
                text = "Second message"
            ).getOrThrow()
        }

        val beforeSync = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(directChatId, aliceUid).first { list -> list.size >= 2 }
        }
        assertEquals(com.example.data.model.ReadReceiptStatus.SENT, beforeSync.first { it.id == m2 }.readStatusForSender(aliceUid))

        // Bob's device syncs the inbox -> marks chat and undelivered messages as DELIVERED without marking them READ
        signInTestUser(bobEmail)
        val deliveredCount = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.markUndeliveredMessagesInChat(directChatId).getOrThrow()
        }
        assertEquals(2, deliveredCount)

        signInTestUser(aliceEmail)
        val afterBobDeliveredMsgs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(directChatId, aliceUid).first { list ->
                val firstDelivered = list.firstOrNull { it.id == m1 }?.deliveredByUids?.contains(bobUid) == true
                val secondDelivered = list.firstOrNull { it.id == m2 }?.deliveredByUids?.contains(bobUid) == true
                firstDelivered && secondDelivered
            }
        }
        val deliveredSecondMsg = afterBobDeliveredMsgs.first { it.id == m2 }
        assertEquals(com.example.data.model.ReadReceiptStatus.DELIVERED, deliveredSecondMsg.readStatusForSender(aliceUid))
        val originalBobDeliveredTs = deliveredSecondMsg.deliveredTimestamps[bobUid]
        assertNotNull(originalBobDeliveredTs)
        assertTrue(!deliveredSecondMsg.readByUids.contains(bobUid))

        val afterBobDeliveredChat = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(directChatId).first { it?.deliveredByUids?.contains(bobUid) == true }
        }
        assertNotNull(afterBobDeliveredChat)
        assertEquals(com.example.data.model.ReadReceiptStatus.DELIVERED, afterBobDeliveredChat!!.lastMessageStatusFor(aliceUid))

        // Bob opens the chat and batch-marks messages READ
        signInTestUser(bobEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.markChatAsRead(directChatId).getOrThrow()
            bobRepo.markMessagesRead(directChatId, listOf(m1, m2)).getOrThrow()
        }

        // Alice observes real-time READ status and readTimestamps while original deliveredTimestamp is preserved
        signInTestUser(aliceEmail)
        val afterBobReadMsgs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(directChatId, aliceUid).first { list ->
                val firstRead = list.firstOrNull { it.id == m1 }?.readByUids?.contains(bobUid) == true
                val secondRead = list.firstOrNull { it.id == m2 }?.readByUids?.contains(bobUid) == true
                firstRead && secondRead
            }
        }
        val secondMsg = afterBobReadMsgs.first { it.id == m2 }
        assertEquals(com.example.data.model.ReadReceiptStatus.READ, secondMsg.readStatusForSender(aliceUid))
        assertNotNull(secondMsg.readTimestamps[bobUid])
        assertEquals(originalBobDeliveredTs, secondMsg.deliveredTimestamps[bobUid])

        val afterBobReadChat = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(directChatId).first { it?.readByUids?.contains(bobUid) == true }
        }
        assertNotNull(afterBobReadChat)
        assertEquals(com.example.data.model.ReadReceiptStatus.READ, afterBobReadChat!!.lastMessageStatusFor(aliceUid))

        // 2. Group chat: Alice sends message -> Bob syncs ("Delivered to Bob") -> Charlie syncs ("Delivered to all") -> Bob reads ("Read by Bob") -> Charlie reads ("Read by all")
        val groupId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createGroupChat("Project Room", aliceProfile, listOf(bobProfile, charlieProfile)).getOrThrow()
        }
        val gm1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendMessage(
                chatId = groupId,
                memberUids = listOf(aliceUid, bobUid, charlieUid),
                senderName = "Alice",
                type = MessageType.TEXT,
                text = "Group update"
            ).getOrThrow()
        }

        signInTestUser(bobEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.markUndeliveredMessagesInChat(groupId).getOrThrow()
        }
        signInTestUser(aliceEmail)
        val groupRoom = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(groupId).first { it != null }
        }!!
        val afterBobDeliveredGroupMsg = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(groupId, aliceUid).first { list ->
                list.firstOrNull { it.id == gm1 }?.deliveredByUids?.contains(bobUid) == true
            }
        }.first { it.id == gm1 }
        assertEquals("Delivered to Bob", afterBobDeliveredGroupMsg.readStatusLabel(aliceUid, groupRoom))

        signInTestUser(charlieEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieRepo.markMessagesDelivered(groupId, listOf(gm1)).getOrThrow()
        }
        signInTestUser(aliceEmail)
        val afterAllDeliveredGroupMsg = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(groupId, aliceUid).first { list ->
                list.firstOrNull { it.id == gm1 }?.deliveredByUids?.contains(charlieUid) == true
            }
        }.first { it.id == gm1 }
        assertEquals("Delivered to all", afterAllDeliveredGroupMsg.readStatusLabel(aliceUid, groupRoom))

        signInTestUser(bobEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.markMessagesRead(groupId, listOf(gm1)).getOrThrow()
        }
        signInTestUser(aliceEmail)
        val afterBobGroupMsg = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(groupId, aliceUid).first { list ->
                list.firstOrNull { it.id == gm1 }?.readByUids?.contains(bobUid) == true
            }
        }.first { it.id == gm1 }
        assertEquals("Read by Bob", afterBobGroupMsg.readStatusLabel(aliceUid, groupRoom))
        assertEquals(listOf(charlieUid), afterBobGroupMsg.deliveredOnlyUidsExcluding(aliceUid))

        signInTestUser(charlieEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieRepo.markMessagesRead(groupId, listOf(gm1)).getOrThrow()
        }
        signInTestUser(aliceEmail)
        val afterAllGroupMsg = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(groupId, aliceUid).first { list ->
                list.firstOrNull { it.id == gm1 }?.readByUids?.contains(charlieUid) == true
            }
        }.first { it.id == gm1 }
        assertEquals("Read by all", afterAllGroupMsg.readStatusLabel(aliceUid, groupRoom))

        // 3. Non-member cannot mark messages delivered or read
        val outsiderEmail = "outsider_read_$runSuffix@test.com"
        signInTestUser(outsiderEmail)
        val outsiderRepo = ChatRepository(firestore, auth)
        val outsiderDeliveredResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            outsiderRepo.markMessagesDelivered(directChatId, listOf(m1))
        }
        assertTrue(outsiderDeliveredResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            outsiderDeliveredResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        val outsiderReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            outsiderRepo.markMessagesRead(directChatId, listOf(m1))
        }
        assertTrue(outsiderReadResult.isFailure)
        val fsErr = outsiderReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    @Test
    fun phoneUserRegistration_andReturningLogin_preservesProfileAndSettings() = runBlocking {
        val phoneEmail = "phone_user_${UUID.randomUUID().toString().take(6)}@test.com"
        val uid = signInTestUser(phoneEmail)
        val repo = ChatRepository(firestore, auth)

        // New user: profile does not exist yet
        val existsBefore = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.doesUserProfileExist(uid)
        }
        assertTrue(!existsBefore)

        // Complete new phone user registration
        val phoneNumber = "+923001234567"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.registerPhoneUserProfile(
                displayName = "Bilal Ahmed",
                phoneNumber = phoneNumber,
                bio = "Product builder",
                avatarData = null
            ).getOrThrow()
        }

        val existsAfter = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.doesUserProfileExist(uid)
        }
        assertTrue(existsAfter)

        // User customizes dark mode preference
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updatePreferences(darkModeEnabled = false).getOrThrow()
        }

        // Returning login sync must preserve displayName, bio, and darkModeEnabled
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.syncExistingPhoneUser(phoneNumber).getOrThrow()
        }

        val restored = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePrivateUser(uid).first { it != null }
        }!!
        assertEquals("Bilal Ahmed", restored.displayName)
        assertEquals("Product builder", restored.bio)
        assertEquals(phoneNumber, restored.phoneNumber)
        assertEquals(false, restored.darkModeEnabled)
    }

    @Test
    fun updateChatTheme_persistsAndResetsPerConversationTheme() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", ALICE_EMAIL).getOrThrow()
        }

        val workChatId = "work_chat_${UUID.randomUUID().toString().take(6)}"
        val personalChatId = "personal_chat_${UUID.randomUUID().toString().take(6)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.updateChatTheme(workChatId, "accent=cobalt;bg=work_grid;tag=work").getOrThrow()
            aliceRepo.updateChatTheme(personalChatId, "accent=rose;bg=personal_waves;tag=personal").getOrThrow()
        }

        val withThemes = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePrivateUser(aliceUid).first { user ->
                user?.chatThemes?.containsKey(workChatId) == true &&
                    user.chatThemes.containsKey(personalChatId)
            }
        }!!
        assertEquals("accent=cobalt;bg=work_grid;tag=work", withThemes.chatThemes[workChatId])
        assertEquals("accent=rose;bg=personal_waves;tag=personal", withThemes.chatThemes[personalChatId])

        // Resetting one conversation removes only that key without touching other themes
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.updateChatTheme(workChatId, null).getOrThrow()
        }
        val afterReset = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePrivateUser(aliceUid).first { user ->
                user != null && !user.chatThemes.containsKey(workChatId)
            }
        }!!
        assertEquals("accent=rose;bg=personal_waves;tag=personal", afterReset.chatThemes[personalChatId])
    }

    @Test
    fun archiveChats_singleAndBatch_movesChatsWithoutDeletingMessagesAndEnforcesMembership() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", ALICE_EMAIL).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfile("Bob", BOB_EMAIL).getOrThrow()
        }

        val aliceProfile = PublicProfile(userId = aliceUid, displayName = "Alice", handle = "alice")
        val bobProfile = PublicProfile(userId = bobUid, displayName = "Bob", handle = "bob")

        // Create direct chat and group chat and send messages in both
        signInTestUser(ALICE_EMAIL)
        val directChatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createOrGetDirectChat(aliceProfile, bobProfile).getOrThrow()
        }
        val groupChatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createGroupChat("Quiet Project", aliceProfile, listOf(bobProfile)).getOrThrow()
        }
        val dmMsgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendMessage(
                chatId = directChatId,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "Alice",
                type = MessageType.TEXT,
                text = "Old DM history"
            ).getOrThrow()
        }
        val grpMsgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendMessage(
                chatId = groupChatId,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "Alice",
                type = MessageType.TEXT,
                text = "Old group history"
            ).getOrThrow()
        }

        // Batch-archive both chats for Alice only
        val archivedCount = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.archiveChats(listOf(directChatId, groupChatId), archive = true).getOrThrow()
        }
        assertEquals(2, archivedCount)

        val aliceChatsAfterArchive = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChats(aliceUid).first { list ->
                val dm = list.firstOrNull { it.id == directChatId }
                val grp = list.firstOrNull { it.id == groupChatId }
                dm?.isArchivedBy(aliceUid) == true && grp?.isArchivedBy(aliceUid) == true
            }
        }
        assertTrue(aliceChatsAfterArchive.first { it.id == directChatId }.isArchivedBy(aliceUid))
        assertTrue(aliceChatsAfterArchive.first { it.id == groupChatId }.isArchivedBy(aliceUid))

        // Messages remain intact in both archived chats
        val dmMessagesAfterArchive = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(directChatId, aliceUid).first { list -> list.any { it.id == dmMsgId } }
        }
        assertEquals("Old DM history", dmMessagesAfterArchive.first { it.id == dmMsgId }.text)
        val grpMessagesAfterArchive = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(groupChatId, aliceUid).first { list -> list.any { it.id == grpMsgId } }
        }
        assertEquals("Old group history", grpMessagesAfterArchive.first { it.id == grpMsgId }.text)

        // Bob's inbox view is unaffected (chats are NOT archived for Bob)
        signInTestUser(BOB_EMAIL)
        val bobChats = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeChats(bobUid).first { list ->
                list.any { it.id == directChatId } && list.any { it.id == groupChatId }
            }
        }
        assertTrue(!bobChats.first { it.id == directChatId }.isArchivedBy(bobUid))
        assertTrue(!bobChats.first { it.id == groupChatId }.isArchivedBy(bobUid))

        // Alice unarchives one via toggleArchiveChat and restores the second via batch unarchive
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.toggleArchiveChat(directChatId, currentlyArchived = true).getOrThrow()
            aliceRepo.archiveChats(listOf(groupChatId), archive = false).getOrThrow()
        }
        val aliceChatsRestored = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChats(aliceUid).first { list ->
                val dm = list.firstOrNull { it.id == directChatId }
                val grp = list.firstOrNull { it.id == groupChatId }
                dm != null && !dm.isArchivedBy(aliceUid) && grp != null && !grp.isArchivedBy(aliceUid)
            }
        }
        assertTrue(!aliceChatsRestored.first { it.id == directChatId }.isArchivedBy(aliceUid))
        assertTrue(!aliceChatsRestored.first { it.id == groupChatId }.isArchivedBy(aliceUid))

        // Outsider cannot archive/unarchive someone else's chat
        val outsiderEmail = "outsider_archive_${UUID.randomUUID().toString().take(6)}@test.com"
        signInTestUser(outsiderEmail)
        val outsiderRepo = ChatRepository(firestore, auth)
        val outsiderResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            outsiderRepo.archiveChats(listOf(directChatId), archive = true)
        }
        assertTrue(outsiderResult.isFailure)
        val fsErr = outsiderResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    @Test
    fun ensureUserProfile_preservesCustomUsernameAndHandle() = runBlocking {
        val aliceUid = signInTestUser("custom_username_${UUID.randomUUID().toString().take(6)}@test.com")
        val repo = ChatRepository(firestore, auth)

        firestore.collection("users").document(aliceUid).set(
            mapOf(
                "userId" to aliceUid,
                "username" to "custom_alice_99",
                "displayName" to "Custom Alice",
                "darkModeEnabled" to true,
                "notificationsEnabled" to true,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        ).await()

        firestore.collection("public_profiles").document(aliceUid).set(
            mapOf(
                "userId" to aliceUid,
                "displayName" to "Custom Alice",
                "handle" to "custom_alice_99",
                "isOnline" to false,
                "discoverable" to true,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        ).await()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Custom Alice", "").getOrThrow()
        }

        val privateUser = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePrivateUser(aliceUid).first { it != null }
        }
        assertEquals("custom_alice_99", privateUser?.username)

        val publicSnap = firestore.collection("public_profiles").document(aliceUid).get().await()
        assertEquals("custom_alice_99", publicSnap.getString("handle"))
    }

    private companion object {
        const val ALICE_EMAIL = "alice_pulse@test.com"
        const val BOB_EMAIL = "bob_pulse@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
