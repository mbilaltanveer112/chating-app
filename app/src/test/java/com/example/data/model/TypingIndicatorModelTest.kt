package com.example.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class TypingIndicatorModelTest {

    @Test
    fun activeTypingUids_excludesSelfAndExpiredEntries() {
        val nowMs = 1_000_000L
        val freshTs = Timestamp(Date(nowMs - 2_000L))
        val staleTs = Timestamp(Date(nowMs - 15_000L))

        val room = ChatRoom(
            id = "chat_1",
            type = "group",
            memberUids = listOf("me", "alice", "bob", "charlie"),
            memberNames = mapOf("alice" to "Alice", "bob" to "Bob", "charlie" to "Charlie"),
            typingUids = listOf("me", "alice", "bob", "charlie"),
            typingTimestamps = mapOf(
                "me" to freshTs,
                "alice" to freshTs,
                "bob" to staleTs
                // charlie omitted (pending write) -> treated as active
            )
        )

        val active = room.activeTypingUids(currentUid = "me", nowMillis = nowMs)
        assertEquals(listOf("alice", "charlie"), active)
    }

    @Test
    fun typingSummaryLabel_formatsPrivateAndGroupChatsCorrectly() {
        val nowMs = 2_000_000L
        val freshTs = Timestamp(Date(nowMs - 1_000L))
        val usersMap = mapOf(
            "u1" to PublicProfile(userId = "u1", displayName = "Alice"),
            "u2" to PublicProfile(userId = "u2", displayName = "Bob"),
            "u3" to PublicProfile(userId = "u3", displayName = "Charlie"),
            "u4" to PublicProfile(userId = "u4", displayName = "Diana")
        )

        val privateRoom = ChatRoom(
            id = "dm_1",
            type = "direct",
            memberUids = listOf("me", "u1"),
            typingUids = listOf("u1"),
            typingTimestamps = mapOf("u1" to freshTs)
        )
        assertEquals("Alice is typing", privateRoom.typingSummaryLabel("me", usersMap, nowMs))

        val twoTypersGroup = ChatRoom(
            id = "grp_1",
            type = "group",
            memberUids = listOf("me", "u1", "u2", "u3"),
            typingUids = listOf("u1", "u2"),
            typingTimestamps = mapOf("u1" to freshTs, "u2" to freshTs)
        )
        assertEquals("Alice and Bob are typing", twoTypersGroup.typingSummaryLabel("me", usersMap, nowMs))

        val threeTypersGroup = twoTypersGroup.copy(
            typingUids = listOf("u1", "u2", "u3"),
            typingTimestamps = mapOf("u1" to freshTs, "u2" to freshTs, "u3" to freshTs)
        )
        assertEquals("Alice, Bob and 1 other are typing", threeTypersGroup.typingSummaryLabel("me", usersMap, nowMs))

        val fourTypersGroup = twoTypersGroup.copy(
            memberUids = listOf("me", "u1", "u2", "u3", "u4"),
            typingUids = listOf("u1", "u2", "u3", "u4"),
            typingTimestamps = mapOf("u1" to freshTs, "u2" to freshTs, "u3" to freshTs, "u4" to freshTs)
        )
        assertEquals("Alice, Bob and 2 others are typing", fourTypersGroup.typingSummaryLabel("me", usersMap, nowMs))

        val removedMemberTyping = twoTypersGroup.copy(
            typingUids = listOf("u1", "u4"),
            typingTimestamps = mapOf("u1" to freshTs, "u4" to freshTs)
        )
        assertEquals(listOf("u1"), removedMemberTyping.activeTypingUids("me", nowMs))

        val emptyTyping = privateRoom.copy(typingUids = emptyList(), typingTimestamps = emptyMap())
        assertTrue(emptyTyping.typingSummaryLabel("me", usersMap, nowMs).isEmpty())
    }
}
