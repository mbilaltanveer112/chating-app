package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.OnlineVisibility
import com.example.data.model.PrivacySettings
import com.example.data.model.PrivacyVisibility
import com.example.security.PrivacyVisibilityResolver
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PresenceRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
    }

    @Test
    fun multiDevicePresence_tracksSessionsAndUpdatesLastSeenWhenLastDeviceDisconnects() = runBlocking {
        val runId = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("alice_pres_$runId@test.com")
        val aliceRepo = ChatRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile(
                displayName = "Alice",
                email = "alice_pres_$runId@test.com",
                deviceId = "dev_1"
            ).getOrThrow()

            // Device 2 also comes online
            aliceRepo.updatePresence(isOnline = true, deviceId = "dev_2").getOrThrow()
        }

        val twoDevicesSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("public_profiles").document(aliceUid).get().await()
        }
        assertTrue(twoDevicesSnap.getBoolean("isOnline") == true)
        @Suppress("UNCHECKED_CAST")
        val sessionsBoth = twoDevicesSnap.get("activeSessions") as? Map<String, Any?>
        assertNotNull(sessionsBoth)
        assertTrue(sessionsBoth!!.containsKey("dev_1"))
        assertTrue(sessionsBoth.containsKey("dev_2"))

        // Device 1 goes offline; Device 2 remains active -> stays Online
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.updatePresence(isOnline = false, deviceId = "dev_1").getOrThrow()
        }
        val oneDeviceSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("public_profiles").document(aliceUid).get().await()
        }
        assertTrue(oneDeviceSnap.getBoolean("isOnline") == true)
        @Suppress("UNCHECKED_CAST")
        val sessionsRemaining = oneDeviceSnap.get("activeSessions") as? Map<String, Any?>
        assertFalse(sessionsRemaining?.containsKey("dev_1") == true)
        assertTrue(sessionsRemaining?.containsKey("dev_2") == true)

        // Device 2 goes offline -> flips offline and updates lastSeen
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.updatePresence(isOnline = false, deviceId = "dev_2").getOrThrow()
        }

        // Bob observes Alice via real-time stream
        val bobUid = signInTestUser("bob_pres_$runId@test.com")
        val bobRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfile(
                displayName = "Bob",
                email = "bob_pres_$runId@test.com",
                deviceId = "bob_dev"
            ).getOrThrow()
        }
        val directory = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.observeDiscoverableUsers().first { list -> list.any { it.userId == aliceUid } }
        }
        val aliceObserved = directory.first { it.userId == aliceUid }
        assertFalse(aliceObserved.isOnline)
        assertTrue(aliceObserved.activeSessions.isEmpty())
        assertNotNull(aliceObserved.lastSeen)
        assertFalse(
            PrivacyVisibilityResolver.isEffectivelyOnline(
                target = aliceObserved,
                viewerUid = bobUid,
                isBlockedEitherWay = false,
                isContact = true
            )
        )
    }

    @Test
    fun privacySettings_sameAsLastSeenAndNobody_enforcePublicDocMaskingAndDenyPeerSpoofing() = runBlocking {
        val runId = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("alice_priv_$runId@test.com")
        val aliceChatRepo = ChatRepository(firestore, auth)
        val aliceSecRepo = SecurityRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.ensureUserProfile(
                displayName = "Alice",
                email = "alice_priv_$runId@test.com",
                deviceId = "dev_a"
            ).getOrThrow()
            aliceSecRepo.savePrivacySettings(
                PrivacySettings(
                    lastSeenVisibility = PrivacyVisibility.NOBODY.wireValue,
                    onlineVisibility = OnlineVisibility.SAME_AS_LAST_SEEN.wireValue
                )
            ).getOrThrow()
        }

        val maskedDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("public_profiles").document(aliceUid).get().await()
        }
        assertFalse(maskedDoc.getBoolean("isOnline") == true)
        @Suppress("UNCHECKED_CAST")
        val maskedSessions = maskedDoc.get("activeSessions") as? Map<String, Any?>
        assertTrue(maskedSessions.isNullOrEmpty())

        // Sign in as Bob and attempt to spoof Alice's presence -> PERMISSION_DENIED
        signInTestUser("bob_priv_$runId@test.com")
        val spoofResult = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("public_profiles").document(aliceUid)
                    .update("isOnline", true)
                    .await()
            }
        }
        assertTrue(spoofResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            spoofResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun observePublicProfile_streamsRealTimeUpdatesForNonDiscoverablePeer() = runBlocking {
        val runId = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("alice_nondisc_$runId@test.com")
        val aliceRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile(
                displayName = "Alice Private",
                email = "alice_nondisc_$runId@test.com",
                deviceId = "dev_1"
            ).getOrThrow()
            firestore.collection("public_profiles").document(aliceUid)
                .update("discoverable", false)
                .await()
        }

        signInTestUser("bob_nondisc_$runId@test.com")
        val bobRepo = ChatRepository(firestore, auth)
        val observedOnline = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.observePublicProfile(aliceUid).first { it != null && it.isOnline }
        }
        assertNotNull(observedOnline)
        assertFalse(observedOnline!!.discoverable)
        assertTrue(observedOnline.isOnline)

        // Alice goes offline -> Bob's observePublicProfile reflects offline immediately
        signInTestUser("alice_nondisc_$runId@test.com")
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.updatePresence(isOnline = false, deviceId = "dev_1").getOrThrow()
        }
        signInTestUser("bob_nondisc_$runId@test.com")
        val observedOffline = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.observePublicProfile(aliceUid).first { it != null && !it.isOnline }
        }
        assertFalse(observedOffline!!.isOnline)
    }
}
