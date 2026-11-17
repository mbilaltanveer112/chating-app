package com.example.data.model

import com.example.call.IceServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallModelsTest {

    @Test
    fun formatCallDuration_formatsMinutesSecondsAndHours() {
        assertEquals("00:00", formatCallDuration(0L))
        assertEquals("00:00", formatCallDuration(-10L))
        assertEquals("00:42", formatCallDuration(42L))
        assertEquals("02:05", formatCallDuration(125L))
        assertEquals("1:01:01", formatCallDuration(3661L))
    }

    @Test
    fun callStatus_terminalAndOngoingFlags_matchState() {
        assertTrue(CallStatus.CALLING.isOngoing)
        assertTrue(CallStatus.RINGING.isOngoing)
        assertTrue(CallStatus.CONNECTING.isOngoing)
        assertTrue(CallStatus.CONNECTED.isOngoing)
        assertTrue(CallStatus.RECONNECTING.isOngoing)

        assertFalse(CallStatus.ENDED.isOngoing)
        assertTrue(CallStatus.ENDED.isTerminal)
        assertTrue(CallStatus.REJECTED.isTerminal)
        assertTrue(CallStatus.MISSED.isTerminal)
        assertTrue(CallStatus.FAILED.isTerminal)
    }

    @Test
    fun callSession_directionAndSubtitles_reflectCallerAndCallee() {
        val completedCall = CallSession(
            id = "c1",
            callerId = "alice",
            callerName = "Alice",
            calleeId = "bob",
            calleeName = "Bob",
            callType = CallType.VIDEO.wireValue,
            status = CallStatus.ENDED.wireValue,
            durationSeconds = 95L
        )
        assertEquals(CallDirection.OUTGOING, completedCall.directionFor("alice"))
        assertEquals(CallDirection.INCOMING, completedCall.directionFor("bob"))
        assertEquals("Bob", completedCall.peerNameFor("alice"))
        assertEquals("Alice", completedCall.peerNameFor("bob"))
        assertEquals("Video • 01:35", completedCall.historySubtitle("alice"))

        val missedVoiceCall = CallSession(
            id = "c2",
            callerId = "alice",
            calleeId = "bob",
            callType = CallType.VOICE.wireValue,
            status = CallStatus.MISSED.wireValue,
            durationSeconds = 0L
        )
        assertEquals(CallDirection.OUTGOING, missedVoiceCall.directionFor("alice"))
        assertEquals(CallDirection.MISSED, missedVoiceCall.directionFor("bob"))
        assertEquals("Cancelled Voice call", missedVoiceCall.historySubtitle("alice"))
        assertEquals("Missed Voice call", missedVoiceCall.historySubtitle("bob"))

        val rejectedCall = CallSession(
            id = "c3",
            callerId = "alice",
            calleeId = "bob",
            callType = CallType.VOICE.wireValue,
            status = CallStatus.REJECTED.wireValue,
            endedBy = "bob"
        )
        assertEquals(CallDirection.REJECTED, rejectedCall.directionFor("bob"))
        assertEquals("Voice call declined", rejectedCall.historySubtitle("alice"))
        assertEquals("Declined Voice call", rejectedCall.historySubtitle("bob"))
    }

    @Test
    fun iceServerConfig_containsStunServers() {
        val servers = IceServerConfig.buildIceServers()
        assertTrue(servers.any { it.urls.any { u -> u.startsWith("stun:") } })
    }
}
