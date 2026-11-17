package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveCallUiState
import com.example.data.model.CallStatus
import com.example.data.model.formatCallDuration
import com.example.ui.components.LocalVideoRendererView
import com.example.ui.components.RemoteVideoRendererView
import com.example.ui.components.UserAvatar
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    callState: ActiveCallUiState,
    currentUserId: String,
    eglBaseContext: EglBase.Context?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEndCall: () -> Unit,
    onReconnect: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onMinimize: () -> Unit,
    onAttachLocalRenderer: (SurfaceViewRenderer) -> Unit,
    onDetachLocalRenderer: (SurfaceViewRenderer) -> Unit,
    onAttachRemoteRenderer: (SurfaceViewRenderer) -> Unit,
    onDetachRemoteRenderer: (SurfaceViewRenderer) -> Unit
) {
    val session = callState.session
    val status = session.callStatus
    val peerName = session.peerNameFor(currentUserId)
    val peerAvatarData = session.peerAvatarDataFor(currentUserId)
    val peerPhotoUrl = session.peerPhotoUrlFor(currentUserId)

    var showControlsOverlay by remember(callState.isFullScreenVideo) {
        mutableStateOf(true)
    }

    BackHandler {
        if (callState.isIncomingPrompt) {
            onMinimize()
        } else if (callState.isFullScreenVideo) {
            onToggleFullScreen()
        } else {
            onMinimize()
        }
    }

    val statusText = when {
        callState.isIncomingPrompt -> {
            if (session.isVideo) "Incoming video call" else "Incoming voice call"
        }
        status == CallStatus.CONNECTED -> formatCallDuration(callState.elapsedSeconds)
        status == CallStatus.RECONNECTING -> {
            if (callState.elapsedSeconds > 0L) "Reconnecting • ${formatCallDuration(callState.elapsedSeconds)}"
            else "Reconnecting..."
        }
        else -> status.displayLabel
    }

    val isVideoActiveSurface = session.isVideo && !callState.isIncomingPrompt

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("call_screen_root")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            val isWide = maxWidth >= 600.dp

            // Video layer for active video calls
            if (isVideoActiveSurface) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showControlsOverlay = !showControlsOverlay
                        }
                ) {
                    if (callState.hasRemoteVideo && eglBaseContext != null) {
                        RemoteVideoRendererView(
                            eglBaseContext = eglBaseContext,
                            onAttach = onAttachRemoteRenderer,
                            onDetach = onDetachRemoteRenderer,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("remote_video_surface")
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            PulsingCallerHeader(
                                peerName = peerName,
                                avatarData = peerAvatarData,
                                photoUrl = peerPhotoUrl,
                                statusText = statusText,
                                isPulsing = status != CallStatus.CONNECTED,
                                isVideoBadge = true
                            )
                        }
                    }

                    // Local Camera PiP preview
                    if (callState.isCameraEnabled && eglBaseContext != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(top = 64.dp, end = 16.dp)
                                .size(width = if (isWide) 144.dp else 112.dp, height = if (isWide) 196.dp else 154.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .testTag("local_video_pip")
                        ) {
                            LocalVideoRendererView(
                                eglBaseContext = eglBaseContext,
                                isFrontCamera = callState.isFrontCamera,
                                isPiP = true,
                                onAttach = onAttachLocalRenderer,
                                onDetach = onDetachLocalRenderer,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            val chromeVisible = !isVideoActiveSurface || !callState.isFullScreenVideo || showControlsOverlay

            // Top bar
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("call_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Minimize call"
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isVideoActiveSurface && callState.hasRemoteVideo) {
                                    "$peerName • $statusText"
                                } else {
                                    if (session.isVideo) "End-to-end Video" else "End-to-end Voice"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isVideoActiveSurface) {
                        FilledTonalIconButton(
                            onClick = onToggleFullScreen,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("call_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = if (callState.isFullScreenVideo) {
                                    Icons.Rounded.FullscreenExit
                                } else {
                                    Icons.Rounded.Fullscreen
                                },
                                contentDescription = if (callState.isFullScreenVideo) {
                                    "Exit full screen"
                                } else {
                                    "Full screen video"
                                }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }

            // Center voice / incoming call stage
            if (!isVideoActiveSurface) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 24.dp, vertical = 96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PulsingCallerHeader(
                        peerName = peerName,
                        avatarData = peerAvatarData,
                        photoUrl = peerPhotoUrl,
                        statusText = statusText,
                        isPulsing = status != CallStatus.CONNECTED || callState.isIncomingPrompt,
                        isVideoBadge = session.isVideo
                    )
                }
            }

            // Reconnection banner
            val bannerMsg = callState.errorBanner
            if (!bannerMsg.isNullOrBlank() || status == CallStatus.RECONNECTING) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = 148.dp, start = 20.dp, end = 20.dp)
                        .widthIn(max = 460.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = bannerMsg ?: "Connection interrupted. Reconnecting...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onReconnect,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.testTag("call_retry_reconnect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            // Bottom Control Dock
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (callState.isIncomingPrompt) {
                        IncomingCallActionBar(
                            isVideo = session.isVideo,
                            onDecline = onReject,
                            onAccept = onAccept
                        )
                    } else {
                        ActiveCallControlBar(
                            isVideo = session.isVideo,
                            isMuted = callState.isMuted,
                            isSpeakerOn = callState.isSpeakerOn,
                            isCameraEnabled = callState.isCameraEnabled,
                            onToggleMute = onToggleMute,
                            onToggleSpeaker = onToggleSpeaker,
                            onToggleCamera = onToggleCamera,
                            onSwitchCamera = onSwitchCamera,
                            onEndCall = onEndCall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingCallerHeader(
    peerName: String,
    avatarData: String,
    photoUrl: String,
    statusText: String,
    isPulsing: Boolean,
    isVideoBadge: Boolean
) {
    val transition = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isPulsing) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            UserAvatar(
                name = peerName,
                avatarData = avatarData,
                photoUrl = photoUrl,
                size = 124.dp
            )
        }

        Text(
            text = peerName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isVideoBadge) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("call_status_text")
                )
            }
        }
    }
}

@Composable
private fun IncomingCallActionBar(
    isVideo: Boolean,
    onDecline: () -> Unit,
    onAccept: () -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onDecline,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .size(68.dp)
                    .testTag("call_decline_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CallEnd,
                    contentDescription = "Decline call",
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "Decline",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onAccept,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(68.dp)
                    .testTag("call_accept_button")
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = "Accept call",
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "Accept",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ActiveCallControlBar(
    isVideo: Boolean,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isCameraEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        modifier = Modifier.widthIn(max = 520.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallToggleButton(
                icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                selected = isMuted,
                onClick = onToggleMute,
                modifier = Modifier.testTag("call_mute_button")
            )

            if (isVideo) {
                CallToggleButton(
                    icon = if (isCameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    label = if (isCameraEnabled) "Camera" else "Cam Off",
                    selected = !isCameraEnabled,
                    onClick = onToggleCamera,
                    modifier = Modifier.testTag("call_camera_toggle_button")
                )
                CallToggleButton(
                    icon = Icons.Rounded.Cameraswitch,
                    label = "Flip",
                    selected = false,
                    onClick = onSwitchCamera,
                    modifier = Modifier.testTag("call_switch_camera_button")
                )
            }

            CallToggleButton(
                icon = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.PhoneInTalk,
                label = if (isSpeakerOn) "Speaker" else "Earpiece",
                selected = isSpeakerOn,
                onClick = onToggleSpeaker,
                modifier = Modifier.testTag("call_speaker_button")
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onEndCall,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("call_end_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "End call",
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = "End",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CallToggleButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            modifier = modifier.size(54.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
