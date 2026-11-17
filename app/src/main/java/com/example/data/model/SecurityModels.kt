package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PrivacyVisibility(
    val wireValue: String,
    val label: String,
    val description: String
) {
    EVERYONE("everyone", "Everyone", "Visible to all PulseChat users"),
    CONTACTS("contacts", "My Contacts", "Only visible to people you chat with"),
    NOBODY("nobody", "Nobody", "Completely hidden from other users");

    companion object {
        fun fromWire(value: String?): PrivacyVisibility =
            entries.firstOrNull { it.wireValue == value } ?: EVERYONE
    }
}

enum class OnlineVisibility(
    val wireValue: String,
    val label: String,
    val description: String
) {
    EVERYONE(
        "everyone",
        "Everyone",
        "All PulseChat users can see when you're online"
    ),
    SAME_AS_LAST_SEEN(
        "same_as_last_seen",
        "Same as Last Seen",
        "Matches who can see your Last Seen timestamp"
    );

    companion object {
        fun fromWire(value: String?): OnlineVisibility = when (value) {
            EVERYONE.wireValue -> EVERYONE
            SAME_AS_LAST_SEEN.wireValue,
            PrivacyVisibility.CONTACTS.wireValue,
            PrivacyVisibility.NOBODY.wireValue -> SAME_AS_LAST_SEEN
            else -> EVERYONE
        }
    }
}

enum class ScreenshotProtectionMode(
    val wireValue: String,
    val label: String,
    val description: String
) {
    ALLOW(
        "allow",
        "Allow screenshots",
        "Screenshots allowed; Android 14+ capture events notify chat participants."
    ),
    BLOCK(
        "block",
        "Block screenshots where supported",
        "Enables Android FLAG_SECURE on protected chat and security screens to block screenshots and screen recording."
    );

    companion object {
        fun fromWire(value: String?): ScreenshotProtectionMode =
            entries.firstOrNull { it.wireValue == value } ?: ALLOW
    }
}

enum class DisappearingTimerOption(
    val seconds: Long,
    val label: String,
    val shortLabel: String
) {
    OFF(0L, "Off", "Off"),
    HOURS_24(86_400L, "24 Hours", "24h"),
    DAYS_7(604_800L, "7 Days", "7d"),
    DAYS_30(2_592_000L, "30 Days", "30d");

    val durationMillis: Long get() = seconds * 1000L

    companion object {
        fun fromSeconds(seconds: Long): DisappearingTimerOption =
            entries.firstOrNull { it.seconds == seconds } ?: OFF
    }
}

enum class SpamProtectionLevel(
    val wireValue: String,
    val label: String,
    val description: String
) {
    OFF("off", "Off", "No automated spam checks"),
    STANDARD("standard", "Standard", "Flags suspicious links, duplicate spam, and rapid bursts"),
    STRICT("strict", "Strict", "Tighter rate limits and strict link filtering");

    companion object {
        fun fromWire(value: String?): SpamProtectionLevel =
            entries.firstOrNull { it.wireValue == value } ?: STANDARD
    }
}

enum class ReportCategory(
    val wireValue: String,
    val label: String
) {
    SPAM("spam", "Spam"),
    HARASSMENT("harassment", "Harassment"),
    FAKE_PROFILE("fake_profile", "Fake Profile"),
    INAPPROPRIATE_CONTENT("inappropriate_content", "Inappropriate Content"),
    SUSPICIOUS_LINK("suspicious_link", "Suspicious Link"),
    PROBLEM("problem", "Report a Problem"),
    OTHER("other", "Other");

    companion object {
        fun fromWire(value: String?): ReportCategory =
            entries.firstOrNull { it.wireValue == value } ?: OTHER
    }
}

enum class ReportStatus(
    val wireValue: String,
    val label: String
) {
    PENDING("pending", "Pending Review"),
    UNDER_REVIEW("under_review", "Under Review"),
    RESOLVED("resolved", "Resolved"),
    DISMISSED("dismissed", "Dismissed");

    companion object {
        fun fromWire(value: String?): ReportStatus =
            entries.firstOrNull { it.wireValue == value } ?: PENDING
    }
}

@IgnoreExtraProperties
data class UserKeyBundle(
    @DocumentId val id: String = "",
    val userId: String = "",
    val identityPublicKey: String = "",
    val signedPreKeyPublic: String = "",
    val signedPreKeySignature: String = "",
    val oneTimePreKeyPublic: String = "",
    val keyFingerprint: String = "",
    val protocolVersion: Int = 1,
    val keyVersion: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockedUserRecord(
    @DocumentId val id: String = "",
    val blockerUid: String = "",
    val blockedUid: String = "",
    val blockedDisplayName: String = "",
    val blockedHandle: String = "",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockHistoryEvent(
    @DocumentId val id: String = "",
    val blockerUid: String = "",
    val targetUid: String = "",
    val targetDisplayName: String = "",
    val action: String = "blocked",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TwoStepSecurityConfig(
    @DocumentId val id: String = "two_step",
    val docType: String = "two_step",
    val enabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val recoveryEmail: String = "",
    val recoveryCodeHash: String = "",
    val recoveryCodeSalt: String = "",
    val registeredDeviceIds: List<String> = emptyList(),
    val failedAttempts: Int = 0,
    val lockedUntilEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatLockConfig(
    @DocumentId val id: String = "chat_lock",
    val docType: String = "chat_lock",
    val lockedChatIds: List<String> = emptyList(),
    val biometricEnabled: Boolean = true,
    val lockPinHash: String = "",
    val lockPinSalt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PrivacySettings(
    @DocumentId val id: String = "privacy_settings",
    val docType: String = "privacy_settings",
    val lastSeenVisibility: String = PrivacyVisibility.EVERYONE.wireValue,
    val onlineVisibility: String = PrivacyVisibility.EVERYONE.wireValue,
    val screenshotMode: String = ScreenshotProtectionMode.ALLOW.wireValue,
    val defaultDisappearingSeconds: Long = 0L,
    val spamProtectionLevel: String = SpamProtectionLevel.STANDARD.wireValue,
    val e2eeEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val lastSeenVisibilityEnum: PrivacyVisibility
        get() = PrivacyVisibility.fromWire(lastSeenVisibility)

    val onlineVisibilityEnum: PrivacyVisibility
        get() = when (onlineVisibility) {
            OnlineVisibility.SAME_AS_LAST_SEEN.wireValue -> lastSeenVisibilityEnum
            else -> PrivacyVisibility.fromWire(onlineVisibility)
        }

    val onlineVisibilitySetting: OnlineVisibility
        get() = OnlineVisibility.fromWire(onlineVisibility)

    val screenshotModeEnum: ScreenshotProtectionMode
        get() = ScreenshotProtectionMode.fromWire(screenshotMode)

    val defaultDisappearingOption: DisappearingTimerOption
        get() = DisappearingTimerOption.fromSeconds(defaultDisappearingSeconds)

    val spamProtectionLevelEnum: SpamProtectionLevel
        get() = SpamProtectionLevel.fromWire(spamProtectionLevel)
}

@IgnoreExtraProperties
data class SpamGuardState(
    @DocumentId val id: String = "spam_guard",
    val docType: String = "spam_guard",
    val restrictedUntilEpochMs: Long = 0L,
    val violationCount: Int = 0,
    val lastViolationReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reporterName: String = "",
    val reportedUserId: String = "",
    val reportedUserName: String = "",
    val chatId: String = "",
    val messageId: String = "",
    val messagePreview: String = "",
    val category: String = ReportCategory.OTHER.wireValue,
    val description: String = "",
    val status: String = ReportStatus.PENDING.wireValue,
    val reviewNotes: String = "",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: ReportCategory
        get() = ReportCategory.fromWire(category)

    val statusEnum: ReportStatus
        get() = ReportStatus.fromWire(status)
}
