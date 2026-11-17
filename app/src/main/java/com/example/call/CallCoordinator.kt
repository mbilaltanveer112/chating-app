package com.example.call

import android.content.Context
import android.util.Log
import com.example.data.model.ActiveCallUiState
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.ChatRoom
import com.example.data.model.MessageType
import com.example.data.model.PublicProfile
import com.example.data.model.formatCallDuration
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import java.util.Collections

class CallCoordinator(
    private val appContext: Context,
    private val currentUserId: String,
    private val callRepository: CallRepository,
    private val chatRepository: ChatRepository,
    private val scope: CoroutineScope,
    private val currentProfileProvider: () -> PublicProfile,
    private val isNotificationsEnabledProvider: () -> Boolean,
    private val openChatIdProvider: () -> String?,
    private val blockedUserIdsProvider: () -> Set<String> = { emptySet() },
    private val onSnackbarMessage: (String) -> Unit
) {

    private val audioManager = CallAudioManager(appContext)
    private var webRtcClient: WebRtcClient? = null

    private val _activeCall = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCall: StateFlow<ActiveCallUiState?> = _activeCall.asStateFlow()

    private val _callHistory = MutableStateFlow<List<CallSession>>(emptyList())
    val callHistory: StateFlow<List<CallSession>> = _callHistory.asStateFlow()

    private val _callHistoryError = MutableStateFlow<String?>(null)
    val callHistoryError: StateFlow<String?> = _callHistoryError.asStateFlow()

    @Volatile
    var isAppInForeground: Boolean = true

    private val coordinatorStartedAtMs = System.currentTimeMillis()
    private val seenIncomingCallIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val answeredCallIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val notifiedMissedCallIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val processedCandidateIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val pendingOutgoingCandidates = mutableListOf<IceCandidate>()

    @Volatile
    private var activeCallId: String? = null
    private var activeChatMemberUids: List<String> = emptyList()
    private var lastAppliedOfferSdp: String = ""
    private var lastAppliedAnswerSdp: String = ""
    private var hasMarkedStarted: Boolean = false
    private var hasLoggedCallMessage: Boolean = false
    private var isEndingOrDisposed: Boolean = false

    private var historyObserverJob: Job? = null
    private var sessionObserverJob: Job? = null
    private var candidatesObserverJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var reconnectTimeoutJob: Job? = null
    private var durationTimerJob: Job? = null

    val eglBaseContext: EglBase.Context?
        get() = webRtcClient?.eglBaseContext

    init {
        startHistoryObserver()
    }

    private fun startHistoryObserver() {
        historyObserverJob?.cancel()
        historyObserverJob = scope.launch {
            callRepository.observeCallHistory(currentUserId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Error observing call history", error)
                    _callHistoryError.value = error.localizedMessage ?: "Unable to load call history"
                }
                .collect { calls ->
                    _callHistoryError.value = null
                    _callHistory.value = calls
                    handleIncomingCallsSnapshot(calls)
                }
        }
    }

    private fun handleIncomingCallsSnapshot(calls: List<CallSession>) {
        val now = System.currentTimeMillis()
        val blockedUids = blockedUserIdsProvider()
        for (call in calls) {
            if (call.calleeId != currentUserId || call.id.isBlank()) continue

            val status = call.callStatus
            if (status == CallStatus.CALLING || status == CallStatus.RINGING) {
                val createdMs = call.createdAt?.toDate()?.time ?: now
                val isFresh = (now - createdMs) <= INCOMING_FRESHNESS_WINDOW_MS &&
                    createdMs >= (coordinatorStartedAtMs - 15_000L)
                if (!isFresh || !seenIncomingCallIds.add(call.id)) continue

                if (blockedUids.contains(call.callerId)) {
                    scope.launch {
                        callRepository.updateCallStatus(
                            callId = call.id,
                            status = CallStatus.REJECTED,
                            endedBy = currentUserId,
                            endReason = "blocked",
                            markEndedNow = true
                        )
                    }
                    continue
                }

                if (activeCallId != null && activeCallId != call.id) {
                    // Busy on another call
                    scope.launch {
                        callRepository.updateCallStatus(
                            callId = call.id,
                            status = CallStatus.REJECTED,
                            endedBy = currentUserId,
                            endReason = "busy",
                            markEndedNow = true
                        )
                    }
                    continue
                }

                presentIncomingCall(call)
            } else if (status == CallStatus.MISSED &&
                seenIncomingCallIds.contains(call.id) &&
                !answeredCallIds.contains(call.id) &&
                notifiedMissedCallIds.add(call.id)
            ) {
                NotificationHelper.cancelCallNotifications(appContext, call.id)
                if (activeCallId == call.id) {
                    teardownLocalMediaAndObservers()
                }
                if (isNotificationsEnabledProvider() && !blockedUids.contains(call.callerId)) {
                    NotificationHelper.showMissedCallNotification(appContext, call)
                }
            }
        }
    }

    private fun presentIncomingCall(call: CallSession) {
        activeCallId = call.id
        isEndingOrDisposed = false
        hasMarkedStarted = false
        hasLoggedCallMessage = false
        lastAppliedOfferSdp = ""
        lastAppliedAnswerSdp = ""
        processedCandidateIds.clear()

        if (call.callStatus == CallStatus.CALLING) {
            scope.launch {
                callRepository.updateCallStatus(call.id, CallStatus.RINGING)
            }
        }

        _activeCall.value = ActiveCallUiState(
            session = call.copy(status = CallStatus.RINGING.wireValue),
            isIncomingPrompt = true,
            isSpeakerOn = call.isVideo,
            isCameraEnabled = call.isVideo,
            isFrontCamera = true
        )

        audioManager.startIncomingRingtone()

        val chatIsOpenInForeground = isAppInForeground && openChatIdProvider() == call.chatId
        if (!chatIsOpenInForeground && isNotificationsEnabledProvider()) {
            NotificationHelper.showIncomingCallNotification(appContext, call)
        }

        observeSessionAndCandidates(call.id, isCaller = false)
    }

    fun startOutgoingCall(
        chatRoom: ChatRoom,
        peerProfile: PublicProfile,
        callType: CallType
    ) {
        if (activeCallId != null || _activeCall.value != null) {
            onSnackbarMessage("Another call is already active")
            return
        }
        if (blockedUserIdsProvider().contains(peerProfile.userId)) {
            onSnackbarMessage("Unblock ${peerProfile.displayName} before calling")
            return
        }
        val caller = currentProfileProvider()
        if (caller.userId == peerProfile.userId) {
            onSnackbarMessage("Cannot call yourself")
            return
        }

        isEndingOrDisposed = false
        hasMarkedStarted = false
        hasLoggedCallMessage = false
        lastAppliedOfferSdp = ""
        lastAppliedAnswerSdp = ""
        processedCandidateIds.clear()
        synchronized(pendingOutgoingCandidates) { pendingOutgoingCandidates.clear() }
        activeChatMemberUids = chatRoom.memberUids.ifEmpty { listOf(caller.userId, peerProfile.userId) }

        val placeholderSession = CallSession(
            id = "",
            chatId = chatRoom.id,
            callerId = caller.userId,
            callerName = caller.displayName,
            callerAvatarData = caller.avatarData,
            callerPhotoUrl = caller.photoUrl,
            calleeId = peerProfile.userId,
            calleeName = peerProfile.displayName,
            calleeAvatarData = peerProfile.avatarData,
            calleePhotoUrl = peerProfile.photoUrl,
            participantUids = listOf(caller.userId, peerProfile.userId),
            callType = callType.wireValue,
            status = CallStatus.CALLING.wireValue
        )

        val rtc = try {
            createAndConfigureWebRtc(
                isVideo = callType == CallType.VIDEO,
                participantUids = listOf(caller.userId, peerProfile.userId),
                onLocalCandidate = { candidate ->
                    val currentId = activeCallId
                    if (currentId.isNullOrBlank()) {
                        synchronized(pendingOutgoingCandidates) {
                            pendingOutgoingCandidates.add(candidate)
                        }
                    } else {
                        sendCandidateAsync(currentId, listOf(caller.userId, peerProfile.userId), candidate)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing WebRTC for outgoing call", e)
            onSnackbarMessage(e.localizedMessage ?: "Unable to initialize call engine")
            teardownLocalMediaAndObservers()
            return
        }

        audioManager.startCallAudio(speakerOn = callType == CallType.VIDEO)
        audioManager.startOutgoingRingback(scope)

        _activeCall.value = ActiveCallUiState(
            session = placeholderSession,
            isIncomingPrompt = false,
            isSpeakerOn = callType == CallType.VIDEO,
            isCameraEnabled = callType == CallType.VIDEO,
            isFrontCamera = rtc.isFrontCamera
        )

        scope.launch {
            try {
                val offerSdp = rtc.createOffer(iceRestart = false)
                val callId = callRepository.createCallSession(
                    chatId = chatRoom.id,
                    caller = caller,
                    callee = peerProfile,
                    callType = callType,
                    offerSdp = offerSdp
                ).getOrThrow()

                if (isEndingOrDisposed) {
                    callRepository.updateCallStatus(
                        callId = callId,
                        status = CallStatus.MISSED,
                        endedBy = currentUserId,
                        endReason = "cancelled",
                        markEndedNow = true
                    )
                    return@launch
                }

                activeCallId = callId
                _activeCall.update { current ->
                    current?.copy(session = current.session.copy(id = callId, offerSdp = offerSdp))
                }

                val queuedCandidates = synchronized(pendingOutgoingCandidates) {
                    val copy = pendingOutgoingCandidates.toList()
                    pendingOutgoingCandidates.clear()
                    copy
                }
                queuedCandidates.forEach { cand ->
                    sendCandidateAsync(callId, listOf(caller.userId, peerProfile.userId), cand)
                }

                CallForegroundService.start(
                    context = appContext,
                    callId = callId,
                    peerName = peerProfile.displayName,
                    isVideo = callType == CallType.VIDEO,
                    statusText = "Calling..."
                )

                observeSessionAndCandidates(callId, isCaller = true)
                startRingTimeout(callId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed placing call", e)
                onSnackbarMessage(e.localizedMessage ?: "Could not place call")
                teardownLocalMediaAndObservers()
            }
        }
    }

    fun acceptCall(sessionOverride: CallSession? = null) {
        val current = _activeCall.value
        val baseSession = sessionOverride ?: current?.session ?: return
        val callId = baseSession.id
        if (callId.isBlank()) return
        if (!answeredCallIds.add(callId)) return

        NotificationHelper.cancelCallNotifications(appContext, callId)
        audioManager.stopRinging()
        isEndingOrDisposed = false
        activeCallId = callId

        scope.launch {
            try {
                val latest = callRepository.getCallSession(callId) ?: baseSession
                if (latest.callStatus.isTerminal) {
                    onSnackbarMessage("Call has already ended")
                    teardownLocalMediaAndObservers()
                    return@launch
                }

                val participants = latest.participantUids.ifEmpty {
                    listOf(latest.callerId, latest.calleeId)
                }
                activeChatMemberUids = participants

                val rtc = webRtcClient ?: createAndConfigureWebRtc(
                    isVideo = latest.isVideo,
                    participantUids = participants,
                    onLocalCandidate = { candidate ->
                        sendCandidateAsync(callId, participants, candidate)
                    }
                )

                audioManager.startCallAudio(speakerOn = latest.isVideo)

                _activeCall.value = ActiveCallUiState(
                    session = latest.copy(status = CallStatus.CONNECTING.wireValue),
                    isIncomingPrompt = false,
                    isMinimized = false,
                    isSpeakerOn = latest.isVideo,
                    isCameraEnabled = latest.isVideo,
                    isFrontCamera = rtc.isFrontCamera
                )

                CallForegroundService.start(
                    context = appContext,
                    callId = callId,
                    peerName = latest.peerNameFor(currentUserId),
                    isVideo = latest.isVideo,
                    statusText = "Connecting..."
                )

                if (latest.offerSdp.isNotBlank()) {
                    lastAppliedOfferSdp = latest.offerSdp
                    rtc.setRemoteDescription(SessionDescription.Type.OFFER, latest.offerSdp)
                    val answerSdp = rtc.createAnswer()
                    callRepository.updateCallAnswer(callId, answerSdp).getOrThrow()
                }

                observeSessionAndCandidates(callId, isCaller = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed accepting call", e)
                onSnackbarMessage(e.localizedMessage ?: "Could not connect call")
                endCall(CallStatus.FAILED, endReason = "accept_failed")
            }
        }
    }

    fun rejectCall(sessionOverride: CallSession? = null) {
        val session = sessionOverride ?: _activeCall.value?.session ?: return
        val callId = session.id
        NotificationHelper.cancelCallNotifications(appContext, callId)
        audioManager.stopRinging()
        if (callId.isBlank()) {
            teardownLocalMediaAndObservers()
            return
        }

        isEndingOrDisposed = true
        scope.launch {
            callRepository.updateCallStatus(
                callId = callId,
                status = CallStatus.REJECTED,
                endedBy = currentUserId,
                endReason = "declined",
                markEndedNow = true
            )
            logCallMessageToChat(session.copy(status = CallStatus.REJECTED.wireValue, endedBy = currentUserId), 0L)
            teardownLocalMediaAndObservers()
        }
    }

    fun endCall(
        forcedStatus: CallStatus? = null,
        endReason: String = "hangup"
    ) {
        val currentState = _activeCall.value
        val session = currentState?.session
        val callId = session?.id ?: activeCallId.orEmpty()
        if (isEndingOrDisposed) {
            teardownLocalMediaAndObservers()
            return
        }
        isEndingOrDisposed = true

        val elapsed = currentState?.elapsedSeconds ?: 0L
        val currentStatus = session?.callStatus ?: CallStatus.CALLING
        val finalStatus = forcedStatus ?: when {
            session != null && session.isCaller(currentUserId) &&
                (currentStatus == CallStatus.CALLING || currentStatus == CallStatus.RINGING) ->
                CallStatus.MISSED
            else -> CallStatus.ENDED
        }

        if (callId.isBlank()) {
            teardownLocalMediaAndObservers()
            return
        }

        NotificationHelper.cancelCallNotifications(appContext, callId)
        scope.launch {
            callRepository.updateCallStatus(
                callId = callId,
                status = finalStatus,
                endedBy = currentUserId,
                endReason = endReason,
                markEndedNow = true,
                durationSeconds = elapsed
            )
            if (session != null) {
                logCallMessageToChat(
                    session.copy(
                        status = finalStatus.wireValue,
                        endedBy = currentUserId,
                        durationSeconds = elapsed
                    ),
                    elapsed
                )
            }
            teardownLocalMediaAndObservers()
        }
    }

    fun reconnectCall() {
        val current = _activeCall.value ?: return
        val callId = current.session.id
        val rtc = webRtcClient ?: return
        if (callId.isBlank() || isEndingOrDisposed) return

        _activeCall.update { state ->
            state?.copy(
                session = state.session.copy(status = CallStatus.RECONNECTING.wireValue),
                errorBanner = "Reconnecting audio & video..."
            )
        }
        startReconnectTimeout()

        scope.launch {
            try {
                if (current.session.isCaller(currentUserId)) {
                    val newOffer = rtc.createOffer(iceRestart = true)
                    callRepository.updateCallOfferSdp(callId, newOffer, CallStatus.RECONNECTING).getOrThrow()
                } else {
                    callRepository.updateCallStatus(callId, CallStatus.RECONNECTING)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Reconnect attempt failed", e)
            }
        }
    }

    fun toggleMute() {
        val current = _activeCall.value ?: return
        val nextMuted = !current.isMuted
        webRtcClient?.setMicrophoneEnabled(!nextMuted)
        audioManager.setMicrophoneMute(nextMuted)
        _activeCall.update { it?.copy(isMuted = nextMuted) }
    }

    fun toggleSpeaker() {
        val current = _activeCall.value ?: return
        val nextSpeaker = !current.isSpeakerOn
        audioManager.setSpeakerphoneOn(nextSpeaker)
        _activeCall.update { it?.copy(isSpeakerOn = nextSpeaker) }
    }

    fun toggleCamera() {
        val current = _activeCall.value ?: return
        if (!current.session.isVideo) return
        val nextCamera = !current.isCameraEnabled
        webRtcClient?.setCameraEnabled(nextCamera)
        _activeCall.update { it?.copy(isCameraEnabled = nextCamera) }
    }

    fun switchCamera() {
        val current = _activeCall.value ?: return
        if (!current.session.isVideo) return
        webRtcClient?.switchCamera { isFront ->
            _activeCall.update { it?.copy(isFrontCamera = isFront) }
        }
    }

    fun toggleFullScreenVideo() {
        _activeCall.update { state ->
            if (state == null || !state.session.isVideo) state
            else state.copy(isFullScreenVideo = !state.isFullScreenVideo)
        }
    }

    fun setMinimized(minimized: Boolean) {
        _activeCall.update { it?.copy(isMinimized = minimized) }
    }

    fun handleNotificationAction(callId: String, action: CallActionType) {
        if (callId.isBlank()) return
        scope.launch {
            val session = if (_activeCall.value?.session?.id == callId) {
                _activeCall.value?.session
            } else {
                callRepository.getCallSession(callId)
            }
            if (session == null) {
                NotificationHelper.cancelCallNotifications(appContext, callId)
                return@launch
            }
            when (action) {
                CallActionType.OPEN -> {
                    _activeCall.update { state ->
                        if (state?.session?.id == callId) state.copy(isMinimized = false) else state
                    }
                    if (_activeCall.value == null && !session.callStatus.isTerminal) {
                        presentIncomingCall(session)
                    }
                }
                CallActionType.ACCEPT -> {
                    NotificationHelper.cancelCallNotifications(appContext, callId)
                    if (!session.callStatus.isTerminal) {
                        acceptCall(session)
                    } else {
                        onSnackbarMessage("Call has ended")
                    }
                }
                CallActionType.DECLINE -> {
                    NotificationHelper.cancelCallNotifications(appContext, callId)
                    rejectCall(session)
                }
                CallActionType.HANG_UP -> {
                    NotificationHelper.cancelCallNotifications(appContext, callId)
                    endCall()
                }
            }
        }
    }

    fun deleteCallHistory(callId: String) {
        scope.launch {
            callRepository.deleteCallHistoryEntry(callId)
                .onFailure { e ->
                    onSnackbarMessage(e.localizedMessage ?: "Unable to delete call record")
                }
        }
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        webRtcClient?.attachLocalRenderer(renderer)
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        webRtcClient?.detachLocalRenderer(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        webRtcClient?.attachRemoteRenderer(renderer)
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        webRtcClient?.detachRemoteRenderer(renderer)
    }

    private fun createAndConfigureWebRtc(
        isVideo: Boolean,
        participantUids: List<String>,
        onLocalCandidate: (IceCandidate) -> Unit
    ): WebRtcClient {
        webRtcClient?.dispose()
        val rtc = WebRtcClient(appContext)
        webRtcClient = rtc
        rtc.startLocalMedia(isVideo = isVideo)
        rtc.createPeerConnection(
            onIceCandidate = onLocalCandidate,
            onIceConnectionStateChanged = { iceState ->
                handleIceStateChanged(iceState)
            },
            onRemoteVideoTrackAvailable = { available ->
                _activeCall.update { it?.copy(hasRemoteVideo = available) }
            }
        )
        return rtc
    }

    private fun handleIceStateChanged(iceState: PeerConnection.IceConnectionState) {
        if (isEndingOrDisposed) return
        val callId = activeCallId ?: return
        when (iceState) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                reconnectTimeoutJob?.cancel()
                reconnectTimeoutJob = null
                ringTimeoutJob?.cancel()
                ringTimeoutJob = null
                audioManager.stopRinging()
                startDurationTimerIfNeeded()

                val markStart = !hasMarkedStarted
                hasMarkedStarted = true
                _activeCall.update { state ->
                    state?.copy(
                        session = state.session.copy(status = CallStatus.CONNECTED.wireValue),
                        isIncomingPrompt = false,
                        errorBanner = null
                    )
                }
                scope.launch {
                    callRepository.updateCallStatus(
                        callId = callId,
                        status = CallStatus.CONNECTED,
                        markStartedNow = markStart
                    )
                }
            }
            PeerConnection.IceConnectionState.DISCONNECTED,
            PeerConnection.IceConnectionState.FAILED -> {
                if (hasMarkedStarted || _activeCall.value?.session?.callStatus == CallStatus.CONNECTING) {
                    reconnectCall()
                }
            }
            else -> Unit
        }
    }

    private fun observeSessionAndCandidates(callId: String, isCaller: Boolean) {
        sessionObserverJob?.cancel()
        sessionObserverJob = scope.launch {
            callRepository.observeCallSession(callId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Error observing call $callId", error)
                }
                .collect { remoteSession ->
                    if (remoteSession == null) {
                        teardownLocalMediaAndObservers()
                        return@collect
                    }
                    handleRemoteSessionUpdate(remoteSession, isCaller)
                }
        }

        candidatesObserverJob?.cancel()
        candidatesObserverJob = scope.launch {
            callRepository.observeIceCandidates(callId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Error observing ICE candidates for $callId", error)
                }
                .collect { candidates ->
                    for (cand in candidates) {
                        if (cand.senderId != currentUserId && processedCandidateIds.add(cand.id)) {
                            webRtcClient?.addIceCandidate(cand.sdpMid, cand.sdpMLineIndex, cand.sdp)
                        }
                    }
                }
        }
    }

    private suspend fun handleRemoteSessionUpdate(remote: CallSession, isCaller: Boolean) {
        if (remote.callStatus.isTerminal) {
            val wasUnansweredIncoming = !isCaller &&
                !answeredCallIds.contains(remote.id) &&
                remote.callStatus == CallStatus.MISSED
            NotificationHelper.cancelCallNotifications(appContext, remote.id)
            if (wasUnansweredIncoming && notifiedMissedCallIds.add(remote.id) && isNotificationsEnabledProvider()) {
                NotificationHelper.showMissedCallNotification(appContext, remote)
            } else if (remote.callStatus == CallStatus.REJECTED && remote.endedBy != currentUserId) {
                onSnackbarMessage("${remote.peerNameFor(currentUserId)} declined the call")
            } else if (remote.callStatus == CallStatus.ENDED && remote.endedBy != currentUserId) {
                onSnackbarMessage("Call ended")
            }
            teardownLocalMediaAndObservers()
            return
        }

        _activeCall.update { current ->
            if (current == null) {
                ActiveCallUiState(
                    session = remote,
                    isIncomingPrompt = !isCaller && !answeredCallIds.contains(remote.id),
                    isSpeakerOn = remote.isVideo,
                    isCameraEnabled = remote.isVideo
                )
            } else {
                val keepConnectedLocally = current.session.callStatus == CallStatus.CONNECTED &&
                    remote.callStatus == CallStatus.CONNECTING
                val effectiveStatus = if (keepConnectedLocally) CallStatus.CONNECTED.wireValue else remote.status
                current.copy(
                    session = remote.copy(status = effectiveStatus),
                    isIncomingPrompt = if (answeredCallIds.contains(remote.id)) false else current.isIncomingPrompt
                )
            }
        }

        if (isCaller) {
            if (remote.answerSdp.isNotBlank() && remote.answerSdp != lastAppliedAnswerSdp) {
                lastAppliedAnswerSdp = remote.answerSdp
                audioManager.stopRinging()
                ringTimeoutJob?.cancel()
                ringTimeoutJob = null
                runCatching {
                    webRtcClient?.setRemoteDescription(SessionDescription.Type.ANSWER, remote.answerSdp)
                }.onFailure { e ->
                    Log.w(TAG, "Failed setting remote answer SDP", e)
                }
            }
        } else {
            val shouldApplyUpdatedOffer = answeredCallIds.contains(remote.id) &&
                remote.offerSdp.isNotBlank() &&
                remote.offerSdp != lastAppliedOfferSdp
            if (shouldApplyUpdatedOffer) {
                lastAppliedOfferSdp = remote.offerSdp
                runCatching {
                    val rtc = webRtcClient ?: return@runCatching
                    rtc.setRemoteDescription(SessionDescription.Type.OFFER, remote.offerSdp)
                    val newAnswer = rtc.createAnswer()
                    callRepository.updateCallAnswer(remote.id, newAnswer)
                }.onFailure { e ->
                    Log.w(TAG, "Failed renegotiating offer SDP", e)
                }
            }
        }
    }

    private fun sendCandidateAsync(
        callId: String,
        participantUids: List<String>,
        candidate: IceCandidate
    ) {
        scope.launch {
            callRepository.sendIceCandidate(
                callId = callId,
                participantUids = participantUids,
                sdpMid = candidate.sdpMid ?: "0",
                sdpMLineIndex = candidate.sdpMLineIndex,
                sdp = candidate.sdp
            )
        }
    }

    private fun startRingTimeout(callId: String) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = scope.launch {
            delay(OUTGOING_RING_TIMEOUT_MS)
            val current = _activeCall.value ?: return@launch
            val status = current.session.callStatus
            if (current.session.id == callId &&
                (status == CallStatus.CALLING || status == CallStatus.RINGING)
            ) {
                onSnackbarMessage("No answer")
                endCall(CallStatus.MISSED, endReason = "no_answer")
            }
        }
    }

    private fun startReconnectTimeout() {
        reconnectTimeoutJob?.cancel()
        reconnectTimeoutJob = scope.launch {
            delay(RECONNECT_TIMEOUT_MS)
            val current = _activeCall.value ?: return@launch
            if (current.session.callStatus == CallStatus.RECONNECTING) {
                onSnackbarMessage("Call connection lost")
                endCall(CallStatus.FAILED, endReason = "network_timeout")
            }
        }
    }

    private fun startDurationTimerIfNeeded() {
        if (durationTimerJob?.isActive == true) return
        durationTimerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val state = _activeCall.value ?: break
                val st = state.session.callStatus
                if (st == CallStatus.CONNECTED || st == CallStatus.RECONNECTING) {
                    val nextElapsed = state.elapsedSeconds + 1L
                    _activeCall.update { it?.copy(elapsedSeconds = nextElapsed) }
                    CallForegroundService.update(
                        context = appContext,
                        callId = state.session.id,
                        peerName = state.session.peerNameFor(currentUserId),
                        isVideo = state.session.isVideo,
                        statusText = "${if (st == CallStatus.RECONNECTING) "Reconnecting • " else ""}${formatCallDuration(nextElapsed)}"
                    )
                }
            }
        }
    }

    private suspend fun logCallMessageToChat(session: CallSession, durationSeconds: Long) {
        if (hasLoggedCallMessage || session.chatId.isBlank()) return
        hasLoggedCallMessage = true
        val members = activeChatMemberUids.ifEmpty { session.participantUids }.ifEmpty {
            listOf(session.callerId, session.calleeId)
        }
        val label = when {
            durationSeconds > 0L -> "${session.type.displayLabel} • ${formatCallDuration(durationSeconds)}"
            session.callStatus == CallStatus.REJECTED -> "${session.type.displayLabel} declined"
            session.callStatus == CallStatus.MISSED -> "Missed ${session.type.displayLabel.lowercase()}"
            else -> "${session.type.displayLabel} ended"
        }
        chatRepository.sendMessage(
            chatId = session.chatId,
            memberUids = members,
            senderName = currentProfileProvider().displayName,
            type = MessageType.CALL,
            text = label,
            durationMs = durationSeconds * 1000L
        )
    }

    private fun teardownLocalMediaAndObservers() {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        reconnectTimeoutJob?.cancel()
        reconnectTimeoutJob = null
        durationTimerJob?.cancel()
        durationTimerJob = null
        sessionObserverJob?.cancel()
        sessionObserverJob = null
        candidatesObserverJob?.cancel()
        candidatesObserverJob = null

        val oldCallId = activeCallId
        activeCallId = null
        if (!oldCallId.isNullOrBlank()) {
            NotificationHelper.cancelCallNotifications(appContext, oldCallId)
        }
        CallForegroundService.stop(appContext)
        audioManager.releaseCallAudio()
        webRtcClient?.dispose()
        webRtcClient = null

        synchronized(pendingOutgoingCandidates) { pendingOutgoingCandidates.clear() }
        processedCandidateIds.clear()
        _activeCall.value = null
    }

    fun dispose() {
        historyObserverJob?.cancel()
        historyObserverJob = null
        if (activeCallId != null && !isEndingOrDisposed) {
            isEndingOrDisposed = true
            CallForegroundService.stop(appContext)
            audioManager.releaseCallAudio()
            webRtcClient?.dispose()
            webRtcClient = null
        } else {
            teardownLocalMediaAndObservers()
        }
    }

    companion object {
        private const val TAG = "CallCoordinator"
        private const val INCOMING_FRESHNESS_WINDOW_MS = 90_000L
        private const val OUTGOING_RING_TIMEOUT_MS = 45_000L
        private const val RECONNECT_TIMEOUT_MS = 15_000L
    }
}
