package com.example.security

import com.example.data.model.OnlineVisibility
import com.example.data.model.PrivacyVisibility
import com.example.data.model.PublicProfile
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PrivacyVisibilityResolverTest {

    private val onlineTarget = PublicProfile(
        userId = "bob_uid",
        displayName = "Bob",
        isOnline = true,
        lastSeenVisibility = PrivacyVisibility.CONTACTS.wireValue,
        onlineVisibility = PrivacyVisibility.CONTACTS.wireValue
    )

    private val offlineTarget = onlineTarget.copy(
        isOnline = false,
        lastSeenVisibility = PrivacyVisibility.EVERYONE.wireValue,
        onlineVisibility = PrivacyVisibility.NOBODY.wireValue
    )

    @Test
    fun visibilityRules_respectEveryoneContactsNobodyAndBlocks() {
        // Contacts only -> true for contact, false for stranger
        assertTrue(
            PrivacyVisibilityResolver.isEffectivelyOnline(
                target = onlineTarget,
                viewerUid = "alice_uid",
                isBlockedEitherWay = false,
                isContact = true
            )
        )
        assertFalse(
            PrivacyVisibilityResolver.isEffectivelyOnline(
                target = onlineTarget,
                viewerUid = "stranger_uid",
                isBlockedEitherWay = false,
                isContact = false
            )
        )

        // Blocked user never sees online or last seen even if contact
        assertFalse(
            PrivacyVisibilityResolver.isEffectivelyOnline(
                target = onlineTarget,
                viewerUid = "alice_uid",
                isBlockedEitherWay = true,
                isContact = true
            )
        )
        assertEquals(
            "",
            PrivacyVisibilityResolver.resolvePresenceSubtitle(
                target = onlineTarget,
                viewerUid = "alice_uid",
                isBlockedEitherWay = true,
                isContact = true,
                formattedLastSeen = "today at 10:15 PM"
            )
        )

        // Online Nobody still allows Last Seen Everyone when offline
        assertFalse(
            PrivacyVisibilityResolver.canViewOnlineStatus(
                target = offlineTarget,
                viewerUid = "alice_uid",
                isBlockedEitherWay = false,
                isContact = false
            )
        )
        assertEquals(
            "Last seen today at 10:15 PM",
            PrivacyVisibilityResolver.resolvePresenceSubtitle(
                target = offlineTarget,
                viewerUid = "alice_uid",
                isBlockedEitherWay = false,
                isContact = false,
                formattedLastSeen = "today at 10:15 PM"
            )
        )
    }

    @Test
    fun onlineVisibilitySameAsLastSeen_andMultiDeviceHeartbeatTimeout_behaveAccurately() {
        val nowMs = 1_700_000_000_000L
        val freshSession = Timestamp(Date(nowMs - 10_000L))
        val expiredSession = Timestamp(Date(nowMs - PrivacyVisibilityResolver.ONLINE_HEARTBEAT_TIMEOUT_MS - 5_000L))

        val multiDeviceProfile = PublicProfile(
            userId = "bob_uid",
            displayName = "Bob",
            isOnline = true,
            lastSeenVisibility = PrivacyVisibility.CONTACTS.wireValue,
            onlineVisibility = OnlineVisibility.SAME_AS_LAST_SEEN.wireValue,
            lastSeen = Timestamp(Date(nowMs - 300_000L)),
            lastHeartbeatAt = freshSession,
            activeSessions = mapOf(
                "dev_1" to expiredSession,
                "dev_2" to freshSession
            )
        )

        // Same as Last Seen (Contacts): contact sees Online, non-contact sees hidden
        assertTrue(
            PrivacyVisibilityResolver.isEffectivelyOnline(
                target = multiDeviceProfile,
                viewerUid = "alice_uid",
                isBlockedEitherWay = false,
                isContact = true,
                nowMillis = nowMs
            )
        )
        assertFalse(
            PrivacyVisibilityResolver.isEffectivelyOnline(
                target = multiDeviceProfile,
                viewerUid = "stranger_uid",
                isBlockedEitherWay = false,
                isContact = false,
                nowMillis = nowMs
            )
        )
        assertEquals(
            "",
            PrivacyVisibilityResolver.resolvePresenceSubtitle(
                target = multiDeviceProfile,
                viewerUid = "stranger_uid",
                isBlockedEitherWay = false,
                isContact = false,
                formattedLastSeen = "today at 1:14 PM",
                nowMillis = nowMs
            )
        )

        // When all device sessions expire past TTL, target flips offline automatically without extra DB writes
        val laterMs = nowMs + PrivacyVisibilityResolver.ONLINE_HEARTBEAT_TIMEOUT_MS + 1_000L
        assertFalse(
            PrivacyVisibilityResolver.isEffectivelyOnline(
                target = multiDeviceProfile,
                viewerUid = "alice_uid",
                isBlockedEitherWay = false,
                isContact = true,
                nowMillis = laterMs
            )
        )
        assertEquals(
            freshSession.toDate().time,
            PrivacyVisibilityResolver.effectiveLastSeenMillis(multiDeviceProfile)
        )
    }
}
