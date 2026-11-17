package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class MessageType(val wireValue: String) {
    TEXT("text"),
    IMAGE("image"),
    VIDEO("video"),
    DOCUMENT("document"),
    VOICE("voice"),
    AI_IMAGE("ai_image"),
    CALL("call"),
    SYSTEM("system");

    companion object {
        fun fromWire(value: String): MessageType =
            entries.firstOrNull { it.wireValue == value } ?: TEXT
    }
}

@IgnoreExtraProperties
data class UserPrivate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarData: String = "",
    val photoUrl: String = "",
    val darkModeEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val chatThemes: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val bio: String = "",
    val avatarData: String = "",
    val photoUrl: String = "",
    @get:PropertyName("isOnline")
    @field:PropertyName("isOnline")
    val isOnline: Boolean = false,
    val discoverable: Boolean = true,
    val lastSeenVisibility: String = PrivacyVisibility.EVERYONE.wireValue,
    val onlineVisibility: String = PrivacyVisibility.EVERYONE.wireValue,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val lastHeartbeatAt: Timestamp? = null,
    val activeSessions: Map<String, Timestamp> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val type: String = "direct",
    val title: String = "",
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val createdBy: String = "",
    val lastMessageText: String = "",
    val lastMessageType: String = "text",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val pinnedBy: List<String> = emptyList(),
    val archivedBy: List<String> = emptyList(),
    val typingUids: List<String> = emptyList(),
    val typingTimestamps: Map<String, Timestamp> = emptyMap(),
    val deliveredByUids: List<String> = emptyList(),
    val readByUids: List<String> = emptyList(),
    val disappearingDurationSeconds: Long = 0L,
    val disappearingUpdatedBy: String = "",
    @ServerTimestamp val disappearingUpdatedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isGroup: Boolean get() = type == "group"

    val disappearingOption: DisappearingTimerOption
        get() = DisappearingTimerOption.fromSeconds(disappearingDurationSeconds)

    fun displayTitleFor(currentUid: String): String {
        if (isGroup) return title.ifBlank { "Group Chat" }
        val otherUid = memberUids.firstOrNull { it != currentUid }
        return if (otherUid != null) {
            memberNames[otherUid]?.takeIf { it.isNotBlank() } ?: title.ifBlank { "Direct Chat" }
        } else {
            "Saved Notes"
        }
    }

    fun otherMemberUid(currentUid: String): String? =
        if (isGroup) null else memberUids.firstOrNull { it != currentUid }

    fun isPinnedBy(uid: String): Boolean = pinnedBy.contains(uid)
    fun isArchivedBy(uid: String): Boolean = archivedBy.contains(uid)
    fun lastActivityEpochMs(): Long =
        (lastMessageAt ?: updatedAt ?: createdAt)?.toDate()?.time ?: 0L

    fun isInactiveSince(cutoffEpochMs: Long): Boolean {
        val last = lastActivityEpochMs()
        return last <= cutoffEpochMs
    }
    fun isUnreadFor(uid: String): Boolean =
        lastMessageSenderId.isNotBlank() &&
            lastMessageSenderId != uid &&
            !readByUids.contains(uid)

    fun hasUndeliveredLatestMessageFor(uid: String): Boolean =
        lastMessageSenderId.isNotBlank() &&
            lastMessageSenderId != uid &&
            !deliveredByUids.contains(uid) &&
            !readByUids.contains(uid)

    fun lastMessageStatusFor(currentUid: String): ReadReceiptStatus? {
        if (lastMessageSenderId.isBlank() || lastMessageSenderId != currentUid) return null
        return when {
            readByUids.any { it != currentUid && it != lastMessageSenderId } -> ReadReceiptStatus.READ
            deliveredByUids.any { it != currentUid && it != lastMessageSenderId } -> ReadReceiptStatus.DELIVERED
            lastMessageAt == null -> ReadReceiptStatus.SENDING
            else -> ReadReceiptStatus.SENT
        }
    }

    fun activeTypingUids(
        currentUid: String,
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMs: Long = TYPING_TTL_MS
    ): List<String> = typingUids.filter { uid ->
        if (uid == currentUid) return@filter false
        if (memberUids.isNotEmpty() && !memberUids.contains(uid)) return@filter false
        val ts = typingTimestamps[uid] ?: return@filter true
        (nowMillis - ts.toDate().time) <= maxAgeMs
    }

    fun activeTypingNames(
        currentUid: String,
        usersMap: Map<String, PublicProfile> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMs: Long = TYPING_TTL_MS
    ): List<String> = activeTypingUids(currentUid, nowMillis, maxAgeMs).map { uid ->
        usersMap[uid]?.displayName?.takeIf { it.isNotBlank() }
            ?: memberNames[uid]?.takeIf { it.isNotBlank() }
            ?: "Someone"
    }

    fun typingSummaryLabel(
        currentUid: String,
        usersMap: Map<String, PublicProfile> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMs: Long = TYPING_TTL_MS
    ): String {
        val names = activeTypingNames(currentUid, usersMap, nowMillis, maxAgeMs)
        return when (names.size) {
            0 -> ""
            1 -> "${names[0]} is typing"
            2 -> "${names[0]} and ${names[1]} are typing"
            else -> {
                val others = names.size - 2
                val suffix = if (others == 1) "other" else "others"
                "${names[0]}, ${names[1]} and $others $suffix are typing"
            }
        }
    }

    companion object {
        const val TYPING_TTL_MS = 10_000L
    }
}

enum class ReadReceiptStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val type: String = "text",
    val text: String = "",
    val mediaData: String = "",
    val mediaFileName: String = "",
    val mediaMimeType: String = "",
    val mediaSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val waveform: List<Int> = emptyList(),
    val replyToMessageId: String = "",
    val replyToSenderName: String = "",
    val replyToPreview: String = "",
    val forwardedFrom: String = "",
    val deliveredByUids: List<String> = emptyList(),
    val deliveredTimestamps: Map<String, Timestamp> = emptyMap(),
    val readByUids: List<String> = emptyList(),
    val readTimestamps: Map<String, Timestamp> = emptyMap(),
    val reactions: Map<String, String> = emptyMap(),
    val deleted: Boolean = false,
    @get:PropertyName("isEncrypted")
    @field:PropertyName("isEncrypted")
    val isEncrypted: Boolean = false,
    val encryptionVersion: Int = 0,
    val encryptedPayload: String = "",
    val encryptedMediaData: String = "",
    val wrappedKeys: Map<String, Map<String, String>> = emptyMap(),
    val expiresAtEpochMs: Long = 0L,
    val systemEventType: String = "",
    val screenshotActorId: String = "",
    @get:Exclude val decryptionError: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val messageType: MessageType get() = MessageType.fromWire(type)

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMs > 0L && nowMillis >= expiresAtEpochMs

    fun readerUidsExcluding(currentUid: String): List<String> =
        (readByUids + readTimestamps.keys)
            .distinct()
            .filter { it != senderId && it != currentUid }

    fun allDeliveredUidsExcluding(currentUid: String): List<String> =
        (deliveredByUids + deliveredTimestamps.keys + readByUids + readTimestamps.keys)
            .distinct()
            .filter { it != senderId && it != currentUid }

    fun deliveredOnlyUidsExcluding(currentUid: String): List<String> {
        val readers = readerUidsExcluding(currentUid).toSet()
        return allDeliveredUidsExcluding(currentUid).filter { !readers.contains(it) }
    }

    fun readStatusForSender(currentUid: String): ReadReceiptStatus = when {
        readerUidsExcluding(currentUid).isNotEmpty() -> ReadReceiptStatus.READ
        allDeliveredUidsExcluding(currentUid).isNotEmpty() -> ReadReceiptStatus.DELIVERED
        createdAt == null -> ReadReceiptStatus.SENDING
        else -> ReadReceiptStatus.SENT
    }

    fun latestDeliveredTimestamp(currentUid: String): Timestamp? =
        allDeliveredUidsExcluding(currentUid)
            .mapNotNull { deliveredTimestamps[it] ?: readTimestamps[it] }
            .maxByOrNull { it.toDate().time }

    fun latestReadTimestamp(currentUid: String): Timestamp? =
        readerUidsExcluding(currentUid)
            .mapNotNull { readTimestamps[it] }
            .maxByOrNull { it.toDate().time }

    fun readStatusLabel(
        currentUid: String,
        chat: ChatRoom? = null,
        usersMap: Map<String, PublicProfile> = emptyMap(),
        formattedReadTime: String = "",
        formattedDeliveredTime: String = ""
    ): String {
        val status = readStatusForSender(currentUid)
        if (status == ReadReceiptStatus.SENDING) return "Sending..."
        if (status == ReadReceiptStatus.SENT) {
            val isSelfOnly = chat != null && chat.memberUids.all { it == currentUid }
            return if (isSelfOnly) "Saved" else "Sent"
        }

        fun resolveName(uid: String): String =
            usersMap[uid]?.displayName?.takeIf { it.isNotBlank() }
                ?: chat?.memberNames?.get(uid)?.takeIf { it.isNotBlank() }
                ?: "1 member"

        if (status == ReadReceiptStatus.DELIVERED) {
            val delivered = allDeliveredUidsExcluding(currentUid)
            return if (chat?.isGroup == true) {
                val otherMemberCount = chat.memberUids.count { it != currentUid && it != senderId }
                when {
                    otherMemberCount > 0 && delivered.size >= otherMemberCount -> "Delivered to all"
                    delivered.size == 1 -> "Delivered to ${resolveName(delivered[0])}"
                    else -> "Delivered to ${delivered.size}"
                }
            } else {
                if (formattedDeliveredTime.isNotBlank()) "Delivered $formattedDeliveredTime" else "Delivered"
            }
        }

        val readers = readerUidsExcluding(currentUid)
        return if (chat?.isGroup == true) {
            val otherMemberCount = chat.memberUids.count { it != currentUid && it != senderId }
            when {
                otherMemberCount > 0 && readers.size >= otherMemberCount -> "Read by all"
                readers.size == 1 -> "Read by ${resolveName(readers[0])}"
                else -> "Read by ${readers.size}"
            }
        } else {
            if (formattedReadTime.isNotBlank()) "Read $formattedReadTime" else "Read"
        }
    }
}
