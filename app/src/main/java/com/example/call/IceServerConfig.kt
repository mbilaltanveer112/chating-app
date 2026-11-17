package com.example.call

import com.example.BuildConfig
import org.webrtc.PeerConnection

object IceServerConfig {

    private val defaultStunUrls = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302",
        "stun:stun3.l.google.com:19302"
    )

    fun buildIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        defaultStunUrls.forEach { stunUrl ->
            servers.add(PeerConnection.IceServer.builder(stunUrl).createIceServer())
        }

        val turnUrls = BuildConfig.TURN_SERVER_URL
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (turnUrls.isNotEmpty()) {
            val username = BuildConfig.TURN_USERNAME.trim()
            val credential = BuildConfig.TURN_CREDENTIAL.trim()
            turnUrls.forEach { turnUrl ->
                val builder = PeerConnection.IceServer.builder(turnUrl)
                if (username.isNotBlank()) builder.setUsername(username)
                if (credential.isNotBlank()) builder.setPassword(credential)
                servers.add(builder.createIceServer())
            }
        }
        return servers
    }

    fun buildRtcConfiguration(): PeerConnection.RTCConfiguration {
        return PeerConnection.RTCConfiguration(buildIceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceCandidatePoolSize = 4
        }
    }
}
