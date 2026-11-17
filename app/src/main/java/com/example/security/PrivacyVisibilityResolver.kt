package com.example.security

import com.example.data.model.ChatRoom
import com.example.data.model.OnlineVisibility
import com.example.data.model.PrivacyVisibility
import com.example.data.model.PublicProfile

object PrivacyVisibilityResolver {

    const val ONLINE_HEARTBEAT_INTERVAL_MS = 20_000L
    const val ONLINE_HEARTBEAT_TIMEOUT_MS = 45_000L

    fun isContactOfViewer(
        viewerUid: String,
        targetUid: String,
        chats: List<ChatRoom>
    ): Boolean {
        if (viewerUid == targetUid) return true
        return chats.any { room ->
            room.memberUids.contains(viewerUid) && room.memberUids.contains(targetUid)
        }
    }

    fun shouldHideOnlineFromPublicDoc(
        onlineVisibility: String?,
        lastSeenVisibility: String?
    ): Boolean {
        return when (onlineVisibility) {
            PrivacyVisibility.NOBODY.wireValue -> true
            OnlineVisibility.SAME_AS_LAST_SEEN.wireValue ->
                PrivacyVisibility.fromWire(lastSeenVisibility) == PrivacyVisibility.NOBODY
            else -> false
        }
    }

    fun canViewOnlineStatus(
        target: PublicProfile?,
        viewerUid: String,
        isBlockedEitherWay: Boolean,
        isContact: Boolean
    ): Boolean {
        if (target == null) return false
        if (target.userId == viewerUid) return true
        if (isBlockedEitherWay) return false
        return when (target.onlineVisibility) {
            OnlineVisibility.EVERYONE.wireValue -> true
            OnlineVisibility.SAME_AS_LAST_SEEN.wireValue ->
                canViewLastSeen(
                    target = target,
                    viewerUid = viewerUid,
                    isBlockedEitherWay = false,
                    isContact = isContact
                )
            PrivacyVisibility.CONTACTS.wireValue -> isContact
            PrivacyVisibility.NOBODY.wireValue -> false
            else -> true
        }
    }

    fun canViewLastSeen(
        target: PublicProfile?,
        viewerUid: String,
        isBlockedEitherWay: Boolean,
        isContact: Boolean
    ): Boolean {
        if (target == null) return false
        if (target.userId == viewerUid) return true
        if (isBlockedEitherWay) return false
        return when (PrivacyVisibility.fromWire(target.lastSeenVisibility)) {
            PrivacyVisibility.EVERYONE -> true
            PrivacyVisibility.CONTACTS -> isContact
            PrivacyVisibility.NOBODY -> false
        }
    }

    fun isSessionActive(
        target: PublicProfile?,
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = ONLINE_HEARTBEAT_TIMEOUT_MS
    ): Boolean {
        if (target == null) return false
        if (target.activeSessions.isNotEmpty()) {
            return target.activeSessions.values.any { ts ->
                (nowMillis - ts.toDate().time) <= timeoutMs
            }
        }
        if (!target.isOnline) return false
        val heartbeat = target.lastHeartbeatAt ?: return true
        return (nowMillis - heartbeat.toDate().time) <= timeoutMs
    }

    fun effectiveLastSeenMillis(target: PublicProfile?): Long? {
        if (target == null) return null
        val candidates = buildList {
            target.lastSeen?.toDate()?.time?.let { add(it) }
            target.lastHeartbeatAt?.toDate()?.time?.let { add(it) }
            target.activeSessions.values.forEach { ts -> add(ts.toDate().time) }
        }
        return candidates.maxOrNull()
    }

    fun isEffectivelyOnline(
        target: PublicProfile?,
        viewerUid: String,
        isBlockedEitherWay: Boolean,
        isContact: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = ONLINE_HEARTBEAT_TIMEOUT_MS
    ): Boolean {
        if (target == null) return false
        if (!canViewOnlineStatus(target, viewerUid, isBlockedEitherWay, isContact)) return false
        return isSessionActive(target, nowMillis, timeoutMs)
    }

    fun resolvePresenceSubtitle(
        target: PublicProfile?,
        viewerUid: String,
        isBlockedEitherWay: Boolean,
        isContact: Boolean,
        formattedLastSeen: String,
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = ONLINE_HEARTBEAT_TIMEOUT_MS
    ): String {
        if (target == null || isBlockedEitherWay) return ""
        if (isEffectivelyOnline(target, viewerUid, isBlockedEitherWay, isContact, nowMillis, timeoutMs)) {
            return "Online"
        }
        val showLastSeen = canViewLastSeen(target, viewerUid, isBlockedEitherWay, isContact)
        if (showLastSeen && formattedLastSeen.isNotBlank()) {
            return if (formattedLastSeen.startsWith("Last seen", ignoreCase = true)) {
                formattedLastSeen
            } else {
                "Last seen $formattedLastSeen"
            }
        }
        return ""
    }
}
