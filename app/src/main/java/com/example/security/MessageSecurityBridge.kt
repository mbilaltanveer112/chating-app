package com.example.security

import com.example.data.model.ChatMessage
import com.example.data.model.UserKeyBundle
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory decrypted message cache and batch-decryption helper for ChatMessage streams.
 * Prevents repeated ECDH/AES-GCM work on UI recompositions without persisting plaintext on disk.
 */
class MessageSecurityBridge {

    private val decryptedCache = ConcurrentHashMap<String, DecryptedMessageContent>()

    fun decryptMessagesForDisplay(
        messages: List<ChatMessage>,
        currentUid: String,
        localMaterial: LocalE2eeKeyMaterial?,
        keyBundles: Map<String, UserKeyBundle>,
        materialsByFingerprint: Map<String, LocalE2eeKeyMaterial> = emptyMap()
    ): List<ChatMessage> {
        if (messages.isEmpty()) return messages
        val allMaterials = buildMap {
            putAll(materialsByFingerprint)
            if (localMaterial != null) {
                put(localMaterial.keyFingerprint, localMaterial)
            }
        }
        return messages.map { msg ->
            if (msg.deleted) {
                return@map msg.copy(decryptionError = "")
            }
            val effectivelyEncrypted = msg.isEncrypted ||
                (msg.encryptedPayload.isNotBlank() && msg.wrappedKeys.isNotEmpty())
            if (!effectivelyEncrypted) {
                return@map msg.copy(decryptionError = "")
            }

            val wrappedEntry = msg.wrappedKeys[currentUid]
            val targetRkf = wrappedEntry?.get("rkf").orEmpty()
            val cacheKey = "${msg.id}|${msg.updatedAt?.seconds ?: 0L}|${msg.encryptedPayload.hashCode()}|$currentUid|$targetRkf"
            val cached = decryptedCache[cacheKey]
            if (cached != null) {
                return@map msg.copy(
                    isEncrypted = true,
                    text = cached.text,
                    mediaData = cached.mediaData.ifBlank { msg.mediaData },
                    replyToPreview = cached.replyToPreview.ifBlank { msg.replyToPreview },
                    decryptionError = ""
                )
            }

            if (allMaterials.isEmpty()) {
                return@map msg.copy(
                    isEncrypted = true,
                    text = "",
                    decryptionError = "Initializing device encryption keys..."
                )
            }

            val senderBundle = keyBundles[msg.senderId]
            val candidateMaterials = buildList {
                if (targetRkf.isNotBlank()) {
                    allMaterials[targetRkf]?.let { add(it) }
                }
                if (localMaterial != null && allMaterials[targetRkf] != localMaterial) {
                    add(localMaterial)
                }
                allMaterials.values.forEach { mat ->
                    if (!contains(mat)) add(mat)
                }
            }

            var decrypted: DecryptedMessageContent? = null
            for (candidate in candidateMaterials) {
                decrypted = E2eeCryptoManager.decryptForCurrentUser(
                    chatId = msg.chatId,
                    messageId = msg.id,
                    senderId = msg.senderId,
                    currentMaterial = candidate,
                    senderBundle = senderBundle,
                    encryptedPayload = msg.encryptedPayload,
                    encryptedMediaData = msg.encryptedMediaData,
                    wrappedKeys = msg.wrappedKeys
                )
                if (decrypted != null) break
            }

            if (decrypted != null) {
                decryptedCache[cacheKey] = decrypted
                msg.copy(
                    isEncrypted = true,
                    text = decrypted.text,
                    mediaData = decrypted.mediaData.ifBlank { msg.mediaData },
                    replyToPreview = decrypted.replyToPreview.ifBlank { msg.replyToPreview },
                    decryptionError = ""
                )
            } else {
                msg.copy(
                    isEncrypted = true,
                    text = "",
                    decryptionError = "Unable to decrypt message with your current device keys."
                )
            }
        }
    }

    fun clearCache() {
        decryptedCache.clear()
    }
}
