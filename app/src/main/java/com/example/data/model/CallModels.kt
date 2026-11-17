package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CallType(val wireValue: String, val displayLabel: String) {
    VOICE("voice", "Voice call"),
    VIDEO("video", "Video call");

    companion object {
        fun fromWire(value: String?): CallType =
            entries.firstOrNull { it.wireValue == value } ?: VOICE
    }
}

enum class CallStatus(val wireValue: String, val displayLabel: String) {
    CALLING("calling", "Calling..."),
    RINGING("ringing", "Ringing..."),
    CONNECTING("connecting", "Connecting..."),
    CONNECTED("connected", "Connected"),
    RECONNECTING("reconnecting", "Reconnecting..."),
    ENDED("ended", "Ended"),
    REJECTED("rejected", "Declined"),
    MISSED("missed", "Missed"),
    FAILED("failed", "Connection failed");

    val isTerminal: Boolean
        get() = this == ENDED || this == REJECTED || this == MISSED || this == FAILED

    val isOngoing: Boolean
        get() = !isTerminal

    companion object {
        fun fromWire(value: String?): CallStatus =
            entries.firstOrNull { it.wireValue == value } ?: CALLING
    }
}

enum class CallDirection(val label: String) {
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
    MISSED("Missed"),
    REJECTED("Declined")
}

@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerAvatarData: String = "",
    val callerPhotoUrl: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val calleeAvatarData: String = "",
    val calleePhotoUrl: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = CallType.VOICE.wireValue,
    val status: String = CallStatus.CALLING.wireValue,
    val offerSdp: String = "",
    val answerSdp: String = "",
    val endedBy: String = "",
    val endReason: String = "",
    val durationSeconds: Long = 0L,
    @ServerTimestamp val startedAt: Timestamp? = null,
    @ServerTimestamp val endedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val type: CallType get() = CallType.fromWire(callType)
    val callStatus: CallStatus get() = CallStatus.fromWire(status)
    val isVideo: Boolean get() = type == CallType.VIDEO

    fun isCaller(currentUid: String): Boolean = callerId == currentUid

    fun peerUidFor(currentUid: String): String =
        if (callerId == currentUid) calleeId else callerId

    fun peerNameFor(currentUid: String): String =
        if (callerId == currentUid) calleeName.ifBlank { "Contact" }
        else callerName.ifBlank { "Contact" }

    fun peerAvatarDataFor(currentUid: String): String =
        if (callerId == currentUid) calleeAvatarData else callerAvatarData

    fun peerPhotoUrlFor(currentUid: String): String =
        if (callerId == currentUid) calleePhotoUrl else callerPhotoUrl

    fun directionFor(currentUid: String): CallDirection = when {
        callerId == currentUid -> CallDirection.OUTGOING
        callStatus == CallStatus.MISSED -> CallDirection.MISSED
        callStatus == CallStatus.REJECTED && endedBy == currentUid -> CallDirection.REJECTED
        callStatus == CallStatus.REJECTED -> CallDirection.MISSED
        callStatus == CallStatus.FAILED && durationSeconds == 0L -> CallDirection.MISSED
        else -> CallDirection.INCOMING
    }

    fun historySubtitle(currentUid: String): String {
        val kind = if (isVideo) "Video" else "Voice"
        return when {
            durationSeconds > 0L -> "$kind • ${formatCallDuration(durationSeconds)}"
            callStatus == CallStatus.MISSED -> if (isCaller(currentUid)) "Cancelled $kind call" else "Missed $kind call"
            callStatus == CallStatus.REJECTED -> if (isCaller(currentUid)) "$kind call declined" else "Declined $kind call"
            callStatus == CallStatus.FAILED -> "$kind call failed"
            isCaller(currentUid) -> "Cancelled $kind call"
            else -> "Missed $kind call"
        }
    }
}

@IgnoreExtraProperties
data class IceCandidateRecord(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val participantUids: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdp: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class ActiveCallUiState(
    val session: CallSession,
    val isIncomingPrompt: Boolean = false,
    val isMinimized: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val isFullScreenVideo: Boolean = false,
    val hasRemoteVideo: Boolean = false,
    val errorBanner: String? = null
)

fun formatCallDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val seconds = safe % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}
