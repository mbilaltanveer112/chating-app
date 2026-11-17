package com.example.call

import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebRtcClient(context: Context) {

    private val appContext = context.applicationContext
    private val rootEglBase: EglBase
    val eglBaseContext: EglBase.Context
        get() = rootEglBase.eglBaseContext

    private val audioDeviceModule: JavaAudioDeviceModule
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val localRenderers = CopyOnWriteArraySet<SurfaceViewRenderer>()
    private val remoteRenderers = CopyOnWriteArraySet<SurfaceViewRenderer>()
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var hasRemoteDescription = false

    @Volatile
    private var isDisposed = false

    @Volatile
    var isFrontCamera: Boolean = true
        private set

    init {
        ensureFactoryInitialized(appContext)
        rootEglBase = EglBase.create()
        audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun startLocalMedia(isVideo: Boolean) {
        if (isDisposed) return
        if (localAudioTrack == null) {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, audioSource).apply {
                setEnabled(true)
            }
        }

        if (isVideo && localVideoTrack == null) {
            val capturer = createCameraCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("PulseCaptureThread", eglBaseContext)
                videoSource = peerConnectionFactory.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, appContext, videoSource?.capturerObserver)
                try {
                    capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to start camera capture", e)
                }
                localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_VIDEO_TRACK_ID, videoSource).apply {
                    setEnabled(true)
                }
                localRenderers.forEach { renderer ->
                    localVideoTrack?.addSink(renderer)
                }
            } else {
                Log.w(TAG, "No camera available; falling back to audio-only local stream")
            }
        }
    }

    fun createPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit,
        onIceConnectionStateChanged: (PeerConnection.IceConnectionState) -> Unit,
        onRemoteVideoTrackAvailable: (Boolean) -> Unit
    ) {
        if (isDisposed) return
        peerConnection?.close()
        peerConnection?.dispose()
        hasRemoteDescription = false
        pendingRemoteCandidates.clear()

        val rtcConfig = IceServerConfig.buildRtcConfiguration()
        val pc = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    if (state != null) {
                        onIceConnectionStateChanged(state)
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        onIceCandidate(candidate)
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

                override fun onAddStream(stream: MediaStream?) {
                    val track = stream?.videoTracks?.firstOrNull()
                    if (track != null) {
                        bindRemoteVideoTrack(track, onRemoteVideoTrackAvailable)
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) = Unit
                override fun onDataChannel(channel: DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        bindRemoteVideoTrack(track, onRemoteVideoTrackAvailable)
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        bindRemoteVideoTrack(track, onRemoteVideoTrackAvailable)
                    }
                }
            }
        ) ?: throw IllegalStateException("Failed to create WebRTC PeerConnection")

        val streamIds = listOf(LOCAL_STREAM_ID)
        localAudioTrack?.let { pc.addTrack(it, streamIds) }
        localVideoTrack?.let { pc.addTrack(it, streamIds) }

        peerConnection = pc
    }

    private fun bindRemoteVideoTrack(
        track: VideoTrack,
        onRemoteVideoTrackAvailable: (Boolean) -> Unit
    ) {
        if (isDisposed) return
        remoteVideoTrack?.let { old ->
            remoteRenderers.forEach { renderer -> old.removeSink(renderer) }
        }
        remoteVideoTrack = track
        track.setEnabled(true)
        remoteRenderers.forEach { renderer ->
            track.addSink(renderer)
        }
        onRemoteVideoTrackAvailable(true)
    }

    suspend fun createOffer(iceRestart: Boolean = false): String {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            if (iceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }
        val sdp = suspendCancellableCoroutine<SessionDescription> { cont ->
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc != null && cont.isActive) cont.resume(desc)
                    else if (cont.isActive) cont.resumeWithException(IllegalStateException("Null SDP offer"))
                }

                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "createOffer failed"))
                }
            }, constraints)
        }
        setLocalDescription(sdp)
        return sdp.description
    }

    suspend fun createAnswer(): String {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        val sdp = suspendCancellableCoroutine<SessionDescription> { cont ->
            pc.createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc != null && cont.isActive) cont.resume(desc)
                    else if (cont.isActive) cont.resumeWithException(IllegalStateException("Null SDP answer"))
                }

                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "createAnswer failed"))
                }
            }, constraints)
        }
        setLocalDescription(sdp)
        return sdp.description
    }

    private suspend fun setLocalDescription(sdp: SessionDescription) {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setLocalDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "setLocalDescription failed"))
                }
            }, sdp)
        }
    }

    suspend fun setRemoteDescription(type: SessionDescription.Type, sdpText: String) {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val desc = SessionDescription(type, sdpText)
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "setRemoteDescription failed"))
                }
            }, desc)
        }
        hasRemoteDescription = true
        val queued = synchronized(pendingRemoteCandidates) {
            val copy = pendingRemoteCandidates.toList()
            pendingRemoteCandidates.clear()
            copy
        }
        queued.forEach { pc.addIceCandidate(it) }
    }

    fun addIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        if (isDisposed || sdp.isBlank()) return
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        val pc = peerConnection
        if (pc != null && hasRemoteDescription) {
            pc.addIceCandidate(candidate)
        } else {
            synchronized(pendingRemoteCandidates) {
                pendingRemoteCandidates.add(candidate)
            }
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        try {
            if (enabled) {
                videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
            } else {
                videoCapturer?.stopCapture()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Camera toggle error", e)
        }
    }

    fun switchCamera(onSwitched: (Boolean) -> Unit) {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isFrontCamera = isFront
                onSwitched(isFront)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.w(TAG, "Switch camera failed: $errorDescription")
            }
        })
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        localRenderers.add(renderer)
        localVideoTrack?.addSink(renderer)
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        localRenderers.remove(renderer)
        localVideoTrack?.removeSink(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderers.add(renderer)
        remoteVideoTrack?.addSink(renderer)
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderers.remove(renderer)
        remoteVideoTrack?.removeSink(renderer)
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true

        localRenderers.forEach { renderer ->
            runCatching { localVideoTrack?.removeSink(renderer) }
        }
        remoteRenderers.forEach { renderer ->
            runCatching { remoteVideoTrack?.removeSink(renderer) }
        }
        localRenderers.clear()
        remoteRenderers.clear()

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null

        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null

        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null

        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null

        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null

        runCatching { peerConnectionFactory.dispose() }
        runCatching { audioDeviceModule.release() }
        runCatching { rootEglBase.release() }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        return try {
            val enumerator = Camera2Enumerator(appContext)
            val deviceNames = enumerator.deviceNames
            val frontName = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            val backName = deviceNames.firstOrNull { enumerator.isBackFacing(it) }
            val chosen = frontName ?: backName ?: deviceNames.firstOrNull()
            isFrontCamera = chosen != null && enumerator.isFrontFacing(chosen)
            chosen?.let { enumerator.createCapturer(it, null) }
        } catch (e: Exception) {
            Log.w(TAG, "Camera2Enumerator unavailable", e)
            null
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        private const val TAG = "WebRtcClient"
        private const val LOCAL_STREAM_ID = "pulse_media_stream"
        private const val LOCAL_AUDIO_TRACK_ID = "pulse_audio_track"
        private const val LOCAL_VIDEO_TRACK_ID = "pulse_video_track"
        private const val VIDEO_WIDTH = 1280
        private const val VIDEO_HEIGHT = 720
        private const val VIDEO_FPS = 30

        @Volatile
        private var factoryInitialized = false

        private fun ensureFactoryInitialized(context: Context) {
            if (!factoryInitialized) {
                synchronized(this) {
                    if (!factoryInitialized) {
                        val options = PeerConnectionFactory.InitializationOptions.builder(context)
                            .setEnableInternalTracer(false)
                            .createInitializationOptions()
                        PeerConnectionFactory.initialize(options)
                        factoryInitialized = true
                    }
                }
            }
        }
    }
}
