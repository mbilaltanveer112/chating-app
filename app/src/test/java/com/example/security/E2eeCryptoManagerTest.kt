package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ChatMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class E2eeCryptoManagerTest {

    @Test
    fun encryptAndDecrypt_privateTextAndMedia_succeedsForParticipantsOnly() {
        val alice = E2eeCryptoManager.generateLocalKeyMaterial("alice_uid", keyVersion = 1)
        val bob = E2eeCryptoManager.generateLocalKeyMaterial("bob_uid", keyVersion = 1)
        val eve = E2eeCryptoManager.generateLocalKeyMaterial("eve_uid", keyVersion = 1)

        val aliceBundle = alice.toPublicKeyBundle()
        val bobBundle = bob.toPublicKeyBundle()

        assertTrue(E2eeCryptoManager.verifySignedPreKey(aliceBundle))
        assertTrue(E2eeCryptoManager.verifySignedPreKey(bobBundle))

        val envelope = E2eeCryptoManager.encryptForRecipients(
            chatId = "dm_alice_bob",
            messageId = "msg_001",
            senderMaterial = alice,
            plaintextText = "Meet at 9:30 PM — bring the specs.",
            mediaData = "AQIDBAUGBwgJCgsMDQ4P",
            recipientBundles = listOf(aliceBundle, bobBundle),
            replyToPreview = "Original secret prompt"
        )

        assertTrue(envelope.encryptedPayload.isNotBlank())
        assertTrue(envelope.encryptedMediaData.isNotBlank())
        assertFalse(envelope.encryptedPayload.contains("Meet at 9:30 PM"))
        assertFalse(envelope.encryptedPayload.contains("Original secret prompt"))

        val bobDecrypted = E2eeCryptoManager.decryptForCurrentUser(
            chatId = "dm_alice_bob",
            messageId = "msg_001",
            senderId = "alice_uid",
            currentMaterial = bob,
            senderBundle = aliceBundle,
            encryptedPayload = envelope.encryptedPayload,
            encryptedMediaData = envelope.encryptedMediaData,
            wrappedKeys = envelope.wrappedKeys
        )
        assertNotNull(bobDecrypted)
        assertEquals("Meet at 9:30 PM — bring the specs.", bobDecrypted?.text)
        assertEquals("AQIDBAUGBwgJCgsMDQ4P", bobDecrypted?.mediaData)
        assertEquals("Original secret prompt", bobDecrypted?.replyToPreview)

        // Sender can also decrypt their own sent message from history
        val aliceSelfDecrypted = E2eeCryptoManager.decryptForCurrentUser(
            chatId = "dm_alice_bob",
            messageId = "msg_001",
            senderId = "alice_uid",
            currentMaterial = alice,
            senderBundle = aliceBundle,
            encryptedPayload = envelope.encryptedPayload,
            encryptedMediaData = envelope.encryptedMediaData,
            wrappedKeys = envelope.wrappedKeys
        )
        assertNotNull(aliceSelfDecrypted)
        assertEquals("Meet at 9:30 PM — bring the specs.", aliceSelfDecrypted?.text)

        // Unauthorized third party cannot decrypt
        val eveAttempt = E2eeCryptoManager.decryptForCurrentUser(
            chatId = "dm_alice_bob",
            messageId = "msg_001",
            senderId = "alice_uid",
            currentMaterial = eve,
            senderBundle = aliceBundle,
            encryptedPayload = envelope.encryptedPayload,
            encryptedMediaData = envelope.encryptedMediaData,
            wrappedKeys = envelope.wrappedKeys
        )
        assertNull(eveAttempt)

        // Tampering with AAD (wrong messageId) causes GCM authentication failure
        val tamperedAadAttempt = E2eeCryptoManager.decryptForCurrentUser(
            chatId = "dm_alice_bob",
            messageId = "msg_tampered",
            senderId = "alice_uid",
            currentMaterial = bob,
            senderBundle = aliceBundle,
            encryptedPayload = envelope.encryptedPayload,
            encryptedMediaData = envelope.encryptedMediaData,
            wrappedKeys = envelope.wrappedKeys
        )
        assertNull(tamperedAadAttempt)
    }

    @Test
    fun wrapAndUnwrapMaterial_safetyNumber_andKeyRotation() {
        val aliceV1 = E2eeCryptoManager.generateLocalKeyMaterial("alice_uid", keyVersion = 1)
        val wrapped = E2eeCryptoManager.wrapPrivateMaterialForStorage(aliceV1)
        val unwrapped = E2eeCryptoManager.unwrapPrivateMaterialFromStorage(wrapped)
        assertNotNull(unwrapped)
        assertEquals(aliceV1, unwrapped)

        val bob = E2eeCryptoManager.generateLocalKeyMaterial("bob_uid", keyVersion = 1)
        val snAliceBob = E2eeCryptoManager.computeSafetyNumber(aliceV1.identityPublicKey, bob.identityPublicKey)
        val snBobAlice = E2eeCryptoManager.computeSafetyNumber(bob.identityPublicKey, aliceV1.identityPublicKey)
        assertEquals(snAliceBob, snBobAlice)
        assertTrue(snAliceBob.filter { it.isDigit() }.length == 60)

        val aliceV2 = E2eeCryptoManager.generateLocalKeyMaterial("alice_uid", keyVersion = 2)
        assertNotEquals(aliceV1.keyFingerprint, aliceV2.keyFingerprint)

        // Tampered SPK signature fails verification
        val tamperedBundle = bob.toPublicKeyBundle().copy(signedPreKeySignature = "AAAA")
        assertFalse(E2eeCryptoManager.verifySignedPreKey(tamperedBundle))
    }

    @Test
    fun bridge_decryptsOfflineAfterSenderAndRecipientKeyRotation_andSurfacesGenuineErrorOnFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DeviceSecurityStore(context)

        val aliceV1 = E2eeCryptoManager.generateLocalKeyMaterial("alice_uid", keyVersion = 1)
        val aliceV2 = E2eeCryptoManager.generateLocalKeyMaterial("alice_uid", keyVersion = 2)
        val bobV1 = E2eeCryptoManager.generateLocalKeyMaterial("bob_uid", keyVersion = 1)
        val bobV2 = E2eeCryptoManager.generateLocalKeyMaterial("bob_uid", keyVersion = 2)
        val charlie = E2eeCryptoManager.generateLocalKeyMaterial("charlie_uid", keyVersion = 1)

        // Bob saves v1 then rotates to v2 locally; history retains both key pairs
        store.saveE2eeMaterial(bobV1)
        store.saveE2eeMaterial(bobV2)
        val bobHistory = store.loadAllE2eeMaterials("bob_uid")
        assertEquals(2, bobHistory.size)
        assertEquals(bobV2.keyFingerprint, store.loadE2eeMaterial("bob_uid")?.keyFingerprint)

        // Alice encrypts message m1 using v1 keys to group (alice, bobV1, charlie)
        // Simulate legacy doc that had text = "[Encrypted message]" and missed isEncrypted boolean
        val env1 = E2eeCryptoManager.encryptForRecipients(
            chatId = "grp_sec",
            messageId = "m1",
            senderMaterial = aliceV1,
            plaintextText = "Group secret v1",
            mediaData = "",
            recipientBundles = listOf(aliceV1.toPublicKeyBundle(), bobV1.toPublicKeyBundle(), charlie.toPublicKeyBundle())
        )
        val rawMsg1 = ChatMessage(
            id = "m1",
            chatId = "grp_sec",
            senderId = "alice_uid",
            senderName = "Alice",
            memberUids = listOf("alice_uid", "bob_uid", "charlie_uid"),
            type = "text",
            text = "[Encrypted message]",
            isEncrypted = false, // Even if flag were missing, envelope detection triggers decryption
            encryptionVersion = env1.encryptionVersion,
            encryptedPayload = env1.encryptedPayload,
            wrappedKeys = env1.wrappedKeys
        )

        // Message encrypted with a different random key Bob doesn't possess -> genuine failure
        val otherBobDevice = E2eeCryptoManager.generateLocalKeyMaterial("bob_uid", keyVersion = 99)
        val envLost = E2eeCryptoManager.encryptForRecipients(
            chatId = "grp_sec",
            messageId = "m_lost",
            senderMaterial = aliceV2,
            plaintextText = "Lost key secret",
            mediaData = "",
            recipientBundles = listOf(aliceV2.toPublicKeyBundle(), otherBobDevice.toPublicKeyBundle())
        )
        val rawLost = ChatMessage(
            id = "m_lost",
            chatId = "grp_sec",
            senderId = "alice_uid",
            senderName = "Alice",
            memberUids = listOf("alice_uid", "bob_uid"),
            type = "text",
            text = "",
            isEncrypted = true,
            encryptionVersion = envLost.encryptionVersion,
            encryptedPayload = envLost.encryptedPayload,
            wrappedKeys = envLost.wrappedKeys
        )

        val bridge = MessageSecurityBridge()
        // Even with empty keyBundles (offline) and aliceV2 already active on server,
        // Bob decrypts m1 via embedded sik + retained bobV1 key!
        val displayed = bridge.decryptMessagesForDisplay(
            messages = listOf(rawMsg1, rawLost),
            currentUid = "bob_uid",
            localMaterial = bobV2,
            keyBundles = mapOf("alice_uid" to aliceV2.toPublicKeyBundle()),
            materialsByFingerprint = bobHistory
        )

        assertEquals("Group secret v1", displayed[0].text)
        assertEquals("", displayed[0].decryptionError)
        assertTrue(displayed[0].isEncrypted)
        assertFalse(displayed[0].text.contains("[Encrypted message]"))

        // Genuine failure sets decryptionError cleanly and never displays "[Encrypted message]"
        assertEquals("", displayed[1].text)
        assertTrue(displayed[1].decryptionError.isNotBlank())
    }
}
