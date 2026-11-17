package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CallStatus
import com.example.data.model.CallType
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

class CallRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun twoUsers_voiceAndVideoCallSignaling_exchangesSdpAndIceAndLogsHistory() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceChatRepo = ChatRepository(firestore, auth)
        val aliceCallRepo = CallRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.ensureUserProfile("Alice", ALICE_EMAIL).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobChatRepo = ChatRepository(firestore, auth)
        val bobCallRepo = CallRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.ensureUserProfile("Bob", BOB_EMAIL).getOrThrow()
        }

        val aliceProfile = PublicProfile(userId = aliceUid, displayName = "Alice", handle = "alice")
        val bobProfile = PublicProfile(userId = bobUid, displayName = "Bob", handle = "bob")

        // Alice creates direct chat and initiates video call
        signInTestUser(ALICE_EMAIL)
        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.createOrGetDirectChat(aliceProfile, bobProfile).getOrThrow()
        }
        val callId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.createCallSession(
                chatId = chatId,
                caller = aliceProfile,
                callee = bobProfile,
                callType = CallType.VIDEO,
                offerSdp = "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\ns=Offer\r\n"
            ).getOrThrow()
        }
        assertTrue(callId.startsWith("call_"))

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.sendIceCandidate(
                callId = callId,
                participantUids = listOf(aliceUid, bobUid),
                sdpMid = "0",
                sdpMLineIndex = 0,
                sdp = "candidate:1 1 UDP 2122252543 192.0.2.1 54321 typ host"
            ).getOrThrow()
        }

        // Bob discovers incoming call, rings, answers, and sends ICE candidate
        signInTestUser(BOB_EMAIL)
        val bobHistory = withTimeout(FLOW_TIMEOUT_MS) {
            bobCallRepo.observeCallHistory(bobUid).first { list -> list.any { it.id == callId } }
        }
        val incoming = bobHistory.first { it.id == callId }
        assertEquals(CallStatus.CALLING, incoming.callStatus)
        assertTrue(incoming.isVideo)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobCallRepo.updateCallStatus(callId, CallStatus.RINGING).getOrThrow()
            bobCallRepo.updateCallAnswer(
                callId = callId,
                answerSdp = "v=0\r\no=- 67890 2 IN IP4 127.0.0.1\r\ns=Answer\r\n"
            ).getOrThrow()
            bobCallRepo.sendIceCandidate(
                callId = callId,
                participantUids = listOf(aliceUid, bobUid),
                sdpMid = "0",
                sdpMLineIndex = 0,
                sdp = "candidate:2 1 UDP 2122252543 198.51.100.2 54322 typ host"
            ).getOrThrow()
        }

        // Alice receives Bob's answer SDP and ICE candidate, performs ICE restart, then ends call
        signInTestUser(ALICE_EMAIL)
        val answeredSession = withTimeout(FLOW_TIMEOUT_MS) {
            aliceCallRepo.observeCallSession(callId).first { it?.answerSdp?.isNotBlank() == true }
        }
        assertNotNull(answeredSession)
        assertEquals(CallStatus.CONNECTING, answeredSession?.callStatus)

        val candidatesSeen = withTimeout(FLOW_TIMEOUT_MS) {
            aliceCallRepo.observeIceCandidates(callId).first { list -> list.size >= 2 }
        }
        assertTrue(candidatesSeen.any { it.senderId == bobUid })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.updateCallOfferSdp(
                callId = callId,
                offerSdp = "v=0\r\no=- 12345 3 IN IP4 127.0.0.1\r\ns=IceRestart\r\n",
                status = CallStatus.RECONNECTING
            ).getOrThrow()
            aliceCallRepo.updateCallStatus(
                callId = callId,
                status = CallStatus.ENDED,
                endedBy = aliceUid,
                endReason = "hangup",
                markEndedNow = true,
                durationSeconds = 95L
            ).getOrThrow()
            aliceChatRepo.sendMessage(
                chatId = chatId,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "Alice",
                type = MessageType.CALL,
                text = "Video call • 01:35",
                durationMs = 95_000L
            ).getOrThrow()
        }

        // Bob reads final call history record and deletes his entry cleanly
        signInTestUser(BOB_EMAIL)
        val finalSession = aliceCallRepo.getCallSession(callId)
        assertNotNull(finalSession)
        assertEquals(CallStatus.ENDED, finalSession?.callStatus)
        assertEquals(95L, finalSession?.durationSeconds)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobCallRepo.deleteCallHistoryEntry(callId).getOrThrow()
        }
    }

    @Test
    fun outsiderUser_cannotReadTamperOrSendIceCandidatesOnCallSession() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceChatRepo = ChatRepository(firestore, auth)
        val aliceCallRepo = CallRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.ensureUserProfile("Alice", ALICE_EMAIL).getOrThrow()
        }
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobChatRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.ensureUserProfile("Bob", BOB_EMAIL).getOrThrow()
        }

        val aliceProfile = PublicProfile(userId = aliceUid, displayName = "Alice", handle = "alice")
        val bobProfile = PublicProfile(userId = bobUid, displayName = "Bob", handle = "bob")

        signInTestUser(ALICE_EMAIL)
        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.createOrGetDirectChat(aliceProfile, bobProfile).getOrThrow()
        }
        val callId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.createCallSession(
                chatId = chatId,
                caller = aliceProfile,
                callee = bobProfile,
                callType = CallType.VOICE,
                offerSdp = "v=0\r\ns=VoiceOffer\r\n"
            ).getOrThrow()
        }

        // Outsider Charlie attempts unauthorized operations
        signInTestUser("charlie_${UUID.randomUUID().toString().take(6)}@test.com")
        val charlieCallRepo = CallRepository(firestore, auth)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("calls").document(callId).get().await()
            }
            fail("Expected PERMISSION_DENIED when outsider reads call session")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsErr)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
        }

        val answerResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieCallRepo.updateCallAnswer(callId, "v=0\r\ns=Hijack\r\n")
        }
        assertTrue(answerResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            answerResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        val candidateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieCallRepo.sendIceCandidate(
                callId = callId,
                participantUids = listOf(aliceUid, bobUid),
                sdpMid = "0",
                sdpMLineIndex = 0,
                sdp = "candidate:spoof"
            )
        }
        assertTrue(candidateResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            candidateResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    private companion object {
        const val ALICE_EMAIL = "alice_calls@test.com"
        const val BOB_EMAIL = "bob_calls@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 8000L
    }
}
