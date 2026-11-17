package com.example.security

import com.example.data.model.UserKeyBundle
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class LocalE2eeKeyMaterial(
    val userId: String,
    val keyVersion: Int,
    val identityPublicKey: String,
    val identityPrivateKey: String,
    val signedPreKeyPublic: String,
    val signedPreKeyPrivate: String,
    val signedPreKeySignature: String,
    val oneTimePreKeyPublic: String,
    val oneTimePreKeyPrivate: String,
    val keyFingerprint: String
) {
    fun toPublicKeyBundle(): UserKeyBundle = UserKeyBundle(
        userId = userId,
        identityPublicKey = identityPublicKey,
        signedPreKeyPublic = signedPreKeyPublic,
        signedPreKeySignature = signedPreKeySignature,
        oneTimePreKeyPublic = oneTimePreKeyPublic,
        keyFingerprint = keyFingerprint,
        protocolVersion = E2eeCryptoManager.PROTOCOL_VERSION,
        keyVersion = keyVersion
    )
}

data class EncryptedMessageEnvelope(
    val encryptedPayload: String,
    val encryptedMediaData: String,
    val wrappedKeys: Map<String, Map<String, String>>,
    val encryptionVersion: Int = E2eeCryptoManager.PROTOCOL_VERSION
)

data class DecryptedMessageContent(
    val text: String,
    val mediaData: String,
    val replyToPreview: String = ""
)

/**
 * Signal-inspired End-to-End Encryption manager using:
 * - X3DH-style triple ECDH key agreement (Identity + Signed Pre-Key + One-Time Pre-Key) on EC P-256
 * - ECDSA SHA-256 verification of peer Signed Pre-Keys
 * - HKDF-SHA256 key derivation
 * - Per-message AES-256-GCM encryption for text AND media with Associated Authenticated Data (AAD)
 * - Android Keystore Master Key wrapping for local private keys
 *
 * Security invariant: Never logs private keys, symmetric keys, or decrypted plaintext.
 */
object E2eeCryptoManager {

    const val PROTOCOL_VERSION = 1
    private const val EC_CURVE = "secp256r1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "pulsechat_e2ee_master_v1"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val CEK_BYTES = 32

    private val secureRandom = SecureRandom()

    fun generateLocalKeyMaterial(userId: String, keyVersion: Int = 1): LocalE2eeKeyMaterial {
        val identityPair = generateEcKeyPair()
        val signedPreKeyPair = generateEcKeyPair()
        val oneTimePreKeyPair = generateEcKeyPair()

        val identityPubB64 = encodeKey(identityPair.public.encoded)
        val signedPreKeyPubB64 = encodeKey(signedPreKeyPair.public.encoded)
        val signedPreKeySigB64 = signBytes(
            identityPair.private,
            signedPreKeyPubB64.toByteArray(StandardCharsets.UTF_8)
        )
        val oneTimePreKeyPubB64 = encodeKey(oneTimePreKeyPair.public.encoded)
        val fingerprint = computePublicFingerprint(identityPubB64)

        return LocalE2eeKeyMaterial(
            userId = userId,
            keyVersion = keyVersion,
            identityPublicKey = identityPubB64,
            identityPrivateKey = encodeKey(identityPair.private.encoded),
            signedPreKeyPublic = signedPreKeyPubB64,
            signedPreKeyPrivate = encodeKey(signedPreKeyPair.private.encoded),
            signedPreKeySignature = signedPreKeySigB64,
            oneTimePreKeyPublic = oneTimePreKeyPubB64,
            oneTimePreKeyPrivate = encodeKey(oneTimePreKeyPair.private.encoded),
            keyFingerprint = fingerprint
        )
    }

    /**
     * Wraps sensitive private key material using the hardware-backed Android Keystore master key
     * (when available on device) so private keys are never stored in plaintext in DataStore.
     */
    fun wrapPrivateMaterialForStorage(material: LocalE2eeKeyMaterial): String {
        val json = JSONObject().apply {
            put("userId", material.userId)
            put("keyVersion", material.keyVersion)
            put("ikPub", material.identityPublicKey)
            put("ikPriv", material.identityPrivateKey)
            put("spkPub", material.signedPreKeyPublic)
            put("spkPriv", material.signedPreKeyPrivate)
            put("spkSig", material.signedPreKeySignature)
            put("opkPub", material.oneTimePreKeyPublic)
            put("opkPriv", material.oneTimePreKeyPrivate)
            put("fp", material.keyFingerprint)
        }.toString()

        val masterKey = getOrCreateAndroidKeyStoreMasterKey()
        return if (masterKey != null) {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(json.toByteArray(StandardCharsets.UTF_8))
            JSONObject().apply {
                put("mode", "keystore_aes_gcm")
                put("iv", encodeKey(iv))
                put("ct", encodeKey(ciphertext))
            }.toString()
        } else {
            // Headless JVM / Robolectric fallback when AndroidKeyStore provider is absent
            JSONObject().apply {
                put("mode", "jvm_fallback")
                put("raw", encodeKey(json.toByteArray(StandardCharsets.UTF_8)))
            }.toString()
        }
    }

    fun unwrapPrivateMaterialFromStorage(stored: String): LocalE2eeKeyMaterial? {
        if (stored.isBlank()) return null
        return runCatching {
            val wrapper = JSONObject(stored)
            val jsonStr = when (wrapper.optString("mode")) {
                "keystore_aes_gcm" -> {
                    val masterKey = getOrCreateAndroidKeyStoreMasterKey() ?: return null
                    val iv = decodeKey(wrapper.getString("iv"))
                    val ct = decodeKey(wrapper.getString("ct"))
                    val cipher = Cipher.getInstance(AES_GCM)
                    cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    String(cipher.doFinal(ct), StandardCharsets.UTF_8)
                }
                "jvm_fallback" -> {
                    String(decodeKey(wrapper.getString("raw")), StandardCharsets.UTF_8)
                }
                else -> return null
            }
            val obj = JSONObject(jsonStr)
            LocalE2eeKeyMaterial(
                userId = obj.getString("userId"),
                keyVersion = obj.optInt("keyVersion", 1),
                identityPublicKey = obj.getString("ikPub"),
                identityPrivateKey = obj.getString("ikPriv"),
                signedPreKeyPublic = obj.getString("spkPub"),
                signedPreKeyPrivate = obj.getString("spkPriv"),
                signedPreKeySignature = obj.getString("spkSig"),
                oneTimePreKeyPublic = obj.getString("opkPub"),
                oneTimePreKeyPrivate = obj.getString("opkPriv"),
                keyFingerprint = obj.getString("fp")
            )
        }.getOrNull()
    }

    fun verifySignedPreKey(bundle: UserKeyBundle): Boolean {
        if (bundle.identityPublicKey.isBlank() ||
            bundle.signedPreKeyPublic.isBlank() ||
            bundle.signedPreKeySignature.isBlank()
        ) return false
        return runCatching {
            val pub = decodePublicKey(bundle.identityPublicKey)
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pub)
            sig.update(bundle.signedPreKeyPublic.toByteArray(StandardCharsets.UTF_8))
            sig.verify(decodeKey(bundle.signedPreKeySignature))
        }.getOrDefault(false)
    }

    /**
     * Encrypts text and optional Base64 media bytes once with a fresh 256-bit CEK,
     * then performs X3DH-style ECDH + HKDF-SHA256 key wrapping for each recipient bundle
     * (including sender's own bundle so sender can decrypt their own history).
     */
    fun encryptForRecipients(
        chatId: String,
        messageId: String,
        senderMaterial: LocalE2eeKeyMaterial,
        plaintextText: String,
        mediaData: String,
        recipientBundles: List<UserKeyBundle>,
        replyToPreview: String = ""
    ): EncryptedMessageEnvelope {
        val cek = ByteArray(CEK_BYTES).also { secureRandom.nextBytes(it) }
        val cekSpec = SecretKeySpec(cek, "AES")
        val textAad = "$chatId|$messageId|${senderMaterial.userId}".toByteArray(StandardCharsets.UTF_8)

        val payloadJson = JSONObject().apply {
            put("text", plaintextText)
            if (replyToPreview.isNotBlank()) {
                put("replyPreview", replyToPreview.take(200))
            }
            put("v", PROTOCOL_VERSION)
        }.toString()

        val encryptedPayload = aesGcmEncryptToEnvelope(
            key = cekSpec,
            plaintext = payloadJson.toByteArray(StandardCharsets.UTF_8),
            aad = textAad
        )

        val encryptedMedia = if (mediaData.isNotBlank()) {
            val mediaAad = "$chatId|$messageId|${senderMaterial.userId}|media".toByteArray(StandardCharsets.UTF_8)
            aesGcmEncryptToEnvelope(
                key = cekSpec,
                plaintext = mediaData.toByteArray(StandardCharsets.UTF_8),
                aad = mediaAad
            )
        } else {
            ""
        }

        val senderIdentityPriv = decodePrivateKey(senderMaterial.identityPrivateKey)
        val wrapped = mutableMapOf<String, Map<String, String>>()

        val uniqueBundles = (recipientBundles + senderMaterial.toPublicKeyBundle())
            .distinctBy { it.userId }

        for (bundle in uniqueBundles) {
            if (!verifySignedPreKey(bundle)) continue
            val ephemeralPair = generateEcKeyPair()
            val recipIkPub = decodePublicKey(bundle.identityPublicKey)
            val recipSpkPub = decodePublicKey(bundle.signedPreKeyPublic)
            val recipOpkPub = bundle.oneTimePreKeyPublic
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { decodePublicKey(it) }.getOrNull() }

            val sharedSecret = deriveX3dhSenderSecret(
                senderIdentityPriv = senderIdentityPriv,
                senderEphemeralPriv = ephemeralPair.private,
                recipientIdentityPub = recipIkPub,
                recipientSignedPreKeyPub = recipSpkPub,
                recipientOneTimePreKeyPub = recipOpkPub
            )
            val kekBytes = hkdfSha256(
                ikm = sharedSecret,
                salt = chatId.toByteArray(StandardCharsets.UTF_8),
                info = "PulseChat-X3DH-v1|$chatId|${bundle.userId}".toByteArray(StandardCharsets.UTF_8),
                length = 32
            )
            val kek = SecretKeySpec(kekBytes, "AES")
            val wrapIv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
            val wrapCipher = Cipher.getInstance(AES_GCM)
            wrapCipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, wrapIv))
            wrapCipher.updateAAD("$chatId|$messageId|${bundle.userId}".toByteArray(StandardCharsets.UTF_8))
            val wrappedCek = wrapCipher.doFinal(cek)

            wrapped[bundle.userId] = mapOf(
                "epk" to encodeKey(ephemeralPair.public.encoded),
                "iv" to encodeKey(wrapIv),
                "cek" to encodeKey(wrappedCek),
                "sik" to senderMaterial.identityPublicKey,
                "skf" to senderMaterial.keyFingerprint,
                "rkf" to bundle.keyFingerprint,
                "usedOpk" to (recipOpkPub != null).toString()
            )
            kekBytes.fill(0)
            sharedSecret.fill(0)
        }

        cek.fill(0)
        return EncryptedMessageEnvelope(
            encryptedPayload = encryptedPayload,
            encryptedMediaData = encryptedMedia,
            wrappedKeys = wrapped,
            encryptionVersion = PROTOCOL_VERSION
        )
    }

    fun decryptForCurrentUser(
        chatId: String,
        messageId: String,
        senderId: String,
        currentMaterial: LocalE2eeKeyMaterial,
        senderBundle: UserKeyBundle?,
        encryptedPayload: String,
        encryptedMediaData: String,
        wrappedKeys: Map<String, Map<String, String>>
    ): DecryptedMessageContent? {
        val entry = wrappedKeys[currentMaterial.userId] ?: return null
        val epkB64 = entry["epk"] ?: return null
        val ivB64 = entry["iv"] ?: return null
        val wrappedCekB64 = entry["cek"] ?: return null
        val usedOpk = entry["usedOpk"]?.toBooleanStrictOrNull() ?: true
        val expectedSkf = entry["skf"].orEmpty()
        val embeddedSik = entry["sik"].orEmpty()

        val senderIkPubB64 = when {
            senderId == currentMaterial.userId &&
                (expectedSkf.isBlank() || expectedSkf == currentMaterial.keyFingerprint) ->
                currentMaterial.identityPublicKey
            senderBundle != null &&
                (expectedSkf.isBlank() || senderBundle.keyFingerprint == expectedSkf) ->
                senderBundle.identityPublicKey
            embeddedSik.isNotBlank() &&
                (expectedSkf.isBlank() || computePublicFingerprint(embeddedSik) == expectedSkf) ->
                embeddedSik
            senderBundle != null -> senderBundle.identityPublicKey
            else -> return null
        }

        return runCatching {
            val senderIkPub = decodePublicKey(senderIkPubB64)
            val senderEpkPub = decodePublicKey(epkB64)

            val myIkPriv = decodePrivateKey(currentMaterial.identityPrivateKey)
            val mySpkPriv = decodePrivateKey(currentMaterial.signedPreKeyPrivate)
            val myOpkPriv = if (usedOpk) {
                currentMaterial.oneTimePreKeyPrivate
                    .takeIf { it.isNotBlank() }
                    ?.let { decodePrivateKey(it) }
            } else null

            val sharedSecret = deriveX3dhReceiverSecret(
                recipientIdentityPriv = myIkPriv,
                recipientSignedPreKeyPriv = mySpkPriv,
                recipientOneTimePreKeyPriv = myOpkPriv,
                senderIdentityPub = senderIkPub,
                senderEphemeralPub = senderEpkPub
            )

            val kekBytes = hkdfSha256(
                ikm = sharedSecret,
                salt = chatId.toByteArray(StandardCharsets.UTF_8),
                info = "PulseChat-X3DH-v1|$chatId|${currentMaterial.userId}".toByteArray(StandardCharsets.UTF_8),
                length = 32
            )
            val kek = SecretKeySpec(kekBytes, "AES")
            val unwrapCipher = Cipher.getInstance(AES_GCM)
            unwrapCipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, decodeKey(ivB64)))
            unwrapCipher.updateAAD("$chatId|$messageId|${currentMaterial.userId}".toByteArray(StandardCharsets.UTF_8))
            val cek = unwrapCipher.doFinal(decodeKey(wrappedCekB64))
            val cekSpec = SecretKeySpec(cek, "AES")

            val textAad = "$chatId|$messageId|$senderId".toByteArray(StandardCharsets.UTF_8)
            val decryptedPayloadJson = if (encryptedPayload.isNotBlank()) {
                String(aesGcmDecryptFromEnvelope(cekSpec, encryptedPayload, textAad), StandardCharsets.UTF_8)
            } else {
                ""
            }
            val payloadObj = if (decryptedPayloadJson.isNotBlank()) {
                JSONObject(decryptedPayloadJson)
            } else {
                null
            }
            val decodedText = payloadObj?.optString("text", "").orEmpty()
            val decodedReplyPreview = payloadObj?.optString("replyPreview", "").orEmpty()

            val decodedMedia = if (encryptedMediaData.isNotBlank()) {
                val mediaAad = "$chatId|$messageId|$senderId|media".toByteArray(StandardCharsets.UTF_8)
                String(aesGcmDecryptFromEnvelope(cekSpec, encryptedMediaData, mediaAad), StandardCharsets.UTF_8)
            } else {
                ""
            }

            cek.fill(0)
            kekBytes.fill(0)
            sharedSecret.fill(0)
            DecryptedMessageContent(
                text = decodedText,
                mediaData = decodedMedia,
                replyToPreview = decodedReplyPreview
            )
        }.getOrNull()
    }

    /**
     * Generates a 60-digit Signal-style Safety Number across two users' identity public keys.
     */
    fun computeSafetyNumber(identityKeyA: String, identityKeyB: String): String {
        if (identityKeyA.isBlank() || identityKeyB.isBlank()) return ""
        val ordered = listOf(identityKeyA, identityKeyB).sorted()
        val digest = MessageDigest.getInstance("SHA-256")
        var state = (ordered[0] + "|" + ordered[1]).toByteArray(StandardCharsets.UTF_8)
        repeat(64) {
            digest.reset()
            state = digest.digest(state)
        }
        val digits = StringBuilder(60)
        var idx = 0
        while (digits.length < 60) {
            val b0 = state[idx % state.size].toInt() and 0xFF
            val b1 = state[(idx + 1) % state.size].toInt() and 0xFF
            val value = ((b0 shl 8) or b1) % 100000
            digits.append(String.format(java.util.Locale.US, "%05d", value))
            idx += 2
            if (idx >= state.size) {
                digest.reset()
                state = digest.digest(state)
                idx = 0
            }
        }
        return digits.toString().chunked(5).chunked(4).joinToString("\n") { row ->
            row.joinToString(" ")
        }
    }

    fun computePublicFingerprint(identityPublicKeyB64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identityPublicKeyB64.toByteArray(StandardCharsets.UTF_8))
        return digest.take(8).joinToString("") { "%02X".format(it) }
            .chunked(4).joinToString("-")
    }

    private fun deriveX3dhSenderSecret(
        senderIdentityPriv: PrivateKey,
        senderEphemeralPriv: PrivateKey,
        recipientIdentityPub: PublicKey,
        recipientSignedPreKeyPub: PublicKey,
        recipientOneTimePreKeyPub: PublicKey?
    ): ByteArray {
        val dh1 = ecdh(senderIdentityPriv, recipientSignedPreKeyPub)
        val dh2 = ecdh(senderEphemeralPriv, recipientIdentityPub)
        val dh3 = ecdh(senderEphemeralPriv, recipientSignedPreKeyPub)
        val dh4 = recipientOneTimePreKeyPub?.let { ecdh(senderEphemeralPriv, it) } ?: ByteArray(0)
        return dh1 + dh2 + dh3 + dh4
    }

    private fun deriveX3dhReceiverSecret(
        recipientIdentityPriv: PrivateKey,
        recipientSignedPreKeyPriv: PrivateKey,
        recipientOneTimePreKeyPriv: PrivateKey?,
        senderIdentityPub: PublicKey,
        senderEphemeralPub: PublicKey
    ): ByteArray {
        val dh1 = ecdh(recipientSignedPreKeyPriv, senderIdentityPub)
        val dh2 = ecdh(recipientIdentityPriv, senderEphemeralPub)
        val dh3 = ecdh(recipientSignedPreKeyPriv, senderEphemeralPub)
        val dh4 = recipientOneTimePreKeyPriv?.let { ecdh(it, senderEphemeralPub) } ?: ByteArray(0)
        return dh1 + dh2 + dh3 + dh4
    }

    private fun ecdh(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            counter++
        }
        return result
    }

    private fun aesGcmEncryptToEnvelope(key: SecretKeySpec, plaintext: ByteArray, aad: ByteArray): String {
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return "${encodeKey(iv)}.${encodeKey(ciphertext)}"
    }

    private fun aesGcmDecryptFromEnvelope(key: SecretKeySpec, envelope: String, aad: ByteArray): ByteArray {
        val dot = envelope.indexOf('.')
        require(dot > 0) { "Malformed E2EE envelope" }
        val iv = decodeKey(envelope.substring(0, dot))
        val ct = decodeKey(envelope.substring(dot + 1))
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ct)
    }

    private fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        return kpg.generateKeyPair()
    }

    private fun signBytes(privateKey: PrivateKey, data: ByteArray): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey, secureRandom)
        sig.update(data)
        return encodeKey(sig.sign())
    }

    private fun decodePublicKey(b64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decodeKey(b64)))

    private fun decodePrivateKey(b64: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decodeKey(b64)))

    private fun encodeKey(bytes: ByteArray): String =
        try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: RuntimeException) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }

    private fun decodeKey(b64: String): ByteArray =
        try {
            android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
        } catch (_: RuntimeException) {
            java.util.Base64.getDecoder().decode(b64)
        }

    private fun getOrCreateAndroidKeyStoreMasterKey(): SecretKey? {
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGeneratorClass = Class.forName("android.security.keystore.KeyGenParameterSpec\$Builder")
            val purposeClass = Class.forName("android.security.keystore.KeyProperties")
            val purposeEncrypt = purposeClass.getField("PURPOSE_ENCRYPT").getInt(null)
            val purposeDecrypt = purposeClass.getField("PURPOSE_DECRYPT").getInt(null)
            val blockModeGcm = purposeClass.getField("BLOCK_MODE_GCM").get(null) as String
            val paddingNone = purposeClass.getField("ENCRYPTION_PADDING_NONE").get(null) as String

            val builder = keyGeneratorClass
                .getConstructor(String::class.java, Int::class.javaPrimitiveType)
                .newInstance(KEYSTORE_ALIAS, purposeEncrypt or purposeDecrypt)
            keyGeneratorClass.getMethod("setBlockModes", Array<String>::class.java)
                .invoke(builder, arrayOf(blockModeGcm))
            keyGeneratorClass.getMethod("setEncryptionPaddings", Array<String>::class.java)
                .invoke(builder, arrayOf(paddingNone))
            keyGeneratorClass.getMethod("setKeySize", Int::class.javaPrimitiveType)
                .invoke(builder, 256)
            val spec = keyGeneratorClass.getMethod("build").invoke(builder)

            val kg = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            kg.init(spec as java.security.spec.AlgorithmParameterSpec)
            kg.generateKey()
        }.getOrNull()
    }
}
