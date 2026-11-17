package com.example.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ReadReceiptModelTest {

    @Test
    fun readStatusForSender_transitionsFromSendingToSentToDeliveredToRead() {
        val sending = ChatMessage(
            id = "m1",
            senderId = "me",
            deliveredByUids = listOf("me"),
            readByUids = listOf("me"),
            createdAt = null
        )
        assertEquals(ReadReceiptStatus.SENDING, sending.readStatusForSender("me"))
        assertEquals("Sending...", sending.readStatusLabel("me"))
        assertNull(sending.latestDeliveredTimestamp("me"))
        assertNull(sending.latestReadTimestamp("me"))

        val sent = sending.copy(createdAt = Timestamp(Date(1_000_000L)))
        assertEquals(ReadReceiptStatus.SENT, sent.readStatusForSender("me"))
        assertEquals("Sent", sent.readStatusLabel("me"))

        val deliveredTs = Timestamp(Date(1_002_000L))
        val delivered = sent.copy(
            deliveredByUids = listOf("me", "bob"),
            deliveredTimestamps = mapOf("me" to Timestamp(Date(1_000_000L)), "bob" to deliveredTs)
        )
        assertEquals(ReadReceiptStatus.DELIVERED, delivered.readStatusForSender("me"))
        assertEquals(deliveredTs, delivered.latestDeliveredTimestamp("me"))
        assertNull(delivered.latestReadTimestamp("me"))
        assertEquals("Delivered", delivered.readStatusLabel("me"))
        assertEquals("Delivered 10:21 AM", delivered.readStatusLabel("me", formattedDeliveredTime = "10:21 AM"))
        assertEquals(listOf("bob"), delivered.deliveredOnlyUidsExcluding("me"))

        val readTs = Timestamp(Date(1_005_000L))
        val read = delivered.copy(
            readByUids = listOf("me", "bob"),
            readTimestamps = mapOf("bob" to readTs)
        )
        assertEquals(ReadReceiptStatus.READ, read.readStatusForSender("me"))
        assertEquals(readTs, read.latestReadTimestamp("me"))
        assertTrue(read.deliveredOnlyUidsExcluding("me").isEmpty())
        assertEquals("Read 10:24 AM", read.readStatusLabel("me", formattedReadTime = "10:24 AM"))
        assertEquals("Read", read.readStatusLabel("me"))
    }

    @Test
    fun readStatusLabel_formatsSelfChatAndGroupChatDeliveryAndReadProgress() {
        val selfRoom = ChatRoom(
            id = "dm_self",
            type = "direct",
            memberUids = listOf("me")
        )
        val selfMsg = ChatMessage(
            id = "m_self",
            senderId = "me",
            deliveredByUids = listOf("me"),
            readByUids = listOf("me"),
            createdAt = Timestamp(Date(1_000_000L))
        )
        assertEquals("Saved", selfMsg.readStatusLabel("me", selfRoom))

        val groupRoom = ChatRoom(
            id = "grp_1",
            type = "group",
            memberUids = listOf("me", "u1", "u2", "u3"),
            memberNames = mapOf("u1" to "Alice", "u2" to "Bob", "u3" to "Charlie")
        )
        val usersMap = mapOf(
            "u1" to PublicProfile(userId = "u1", displayName = "Alice"),
            "u2" to PublicProfile(userId = "u2", displayName = "Bob")
        )

        assertEquals("Sent", selfMsg.readStatusLabel("me", groupRoom, usersMap))

        val oneDelivered = selfMsg.copy(deliveredByUids = listOf("me", "u1"))
        assertEquals(ReadReceiptStatus.DELIVERED, oneDelivered.readStatusForSender("me"))
        assertEquals("Delivered to Alice", oneDelivered.readStatusLabel("me", groupRoom, usersMap))

        val twoDelivered = selfMsg.copy(deliveredByUids = listOf("me", "u1", "u2"))
        assertEquals("Delivered to 2", twoDelivered.readStatusLabel("me", groupRoom, usersMap))

        val allDelivered = selfMsg.copy(deliveredByUids = listOf("me", "u1", "u2", "u3"))
        assertEquals("Delivered to all", allDelivered.readStatusLabel("me", groupRoom, usersMap))

        val oneReaderTwoDelivered = allDelivered.copy(readByUids = listOf("me", "u1"))
        assertEquals(ReadReceiptStatus.READ, oneReaderTwoDelivered.readStatusForSender("me"))
        assertEquals("Read by Alice", oneReaderTwoDelivered.readStatusLabel("me", groupRoom, usersMap))
        assertEquals(listOf("u2", "u3"), oneReaderTwoDelivered.deliveredOnlyUidsExcluding("me"))

        val twoReaders = oneReaderTwoDelivered.copy(readByUids = listOf("me", "u1", "u2"))
        assertEquals("Read by 2", twoReaders.readStatusLabel("me", groupRoom, usersMap))

        val allReaders = oneReaderTwoDelivered.copy(readByUids = listOf("me", "u1", "u2", "u3"))
        assertEquals("Read by all", allReaders.readStatusLabel("me", groupRoom, usersMap))
    }

    @Test
    fun chatRoom_tracksLastMessageStatusAndUndeliveredFlags() {
        val now = Timestamp(Date(2_000_000L))
        val outgoing = ChatRoom(
            id = "dm_1",
            type = "direct",
            memberUids = listOf("me", "bob"),
            lastMessageSenderId = "me",
            lastMessageAt = now,
            deliveredByUids = listOf("me"),
            readByUids = listOf("me")
        )
        assertEquals(ReadReceiptStatus.SENT, outgoing.lastMessageStatusFor("me"))
        assertEquals(
            ReadReceiptStatus.DELIVERED,
            outgoing.copy(deliveredByUids = listOf("me", "bob")).lastMessageStatusFor("me")
        )
        assertEquals(
            ReadReceiptStatus.READ,
            outgoing.copy(deliveredByUids = listOf("me", "bob"), readByUids = listOf("me", "bob")).lastMessageStatusFor("me")
        )
        assertNull(outgoing.copy(lastMessageSenderId = "bob").lastMessageStatusFor("me"))
        assertTrue(outgoing.copy(lastMessageSenderId = "bob", deliveredByUids = listOf("bob"), readByUids = listOf("bob")).hasUndeliveredLatestMessageFor("me"))
        assertFalse(outgoing.copy(lastMessageSenderId = "bob", deliveredByUids = listOf("bob", "me"), readByUids = listOf("bob")).hasUndeliveredLatestMessageFor("me"))
    }
}
