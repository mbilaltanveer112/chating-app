package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.MessageType
import com.example.data.model.PublicProfile
import com.example.data.model.ReadReceiptStatus
import com.example.data.model.ReportCategory
import com.example.security.PresenceFormatter
import com.example.security.PrivacyVisibilityResolver
import com.example.security.SpamProtectionEngine
import com.example.ui.components.BlockUserConfirmDialog
import com.example.ui.components.ChatCategoryBadge
import com.example.ui.components.ChatPickerBottomSheet
import com.example.ui.components.ChatThemeBottomSheet
import com.example.ui.components.ChatThemedBackground
import com.example.ui.components.DisappearingTimerDialog
import com.example.ui.components.DocumentAttachmentContent
import com.example.ui.components.FullScreenImageDialog
import com.example.ui.components.MessageReadInfoBottomSheet
import com.example.ui.components.ReportDialog
import com.example.ui.components.SafetyNumberDialog
import com.example.ui.components.TypingDots
import com.example.ui.components.TypingIndicatorBubble
import com.example.ui.components.UserAvatar
import com.example.ui.components.VideoAttachmentContent
import com.example.ui.components.VoiceNoteContent
import com.example.ui.theme.ChatAccentOption
import com.example.ui.theme.ChatCategoryTag
import com.example.ui.theme.ChatThemePreference
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.UiState
import com.example.util.MediaUtils
import com.example.util.VoicePlayerManager
import com.example.util.VoiceRecorderManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val QUICK_EMOJIS = listOf("👍", "❤️", "😂", "🔥", "🎉", "✨", "🙌", "😍")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    chatId: String,
    currentUserId: String,
    chat: ChatRoom?,
    allChats: List<ChatRoom>,
    messagesState: UiState<List<ChatMessage>>,
    usersMap: Map<String, PublicProfile>,
    replyingTo: ChatMessage?,
    forwardingMessage: ChatMessage?,
    isDarkTheme: Boolean = true,
    chatTheme: ChatThemePreference = ChatThemePreference.Default,
    blockedUserIds: Set<String> = emptySet(),
    blockedEitherWayUserIds: Set<String> = blockedUserIds,
    isChatLocked: Boolean = false,
    e2eeEnabled: Boolean = true,
    peerHasKeyBundle: Boolean = false,
    safetyNumber: String = "",
    myKeyFingerprint: String = "",
    myKeyVersion: Int = 1,
    onBack: () -> Unit,
    onSendText: (ChatRoom, String) -> Unit,
    onSendMedia: (
        chat: ChatRoom,
        type: MessageType,
        caption: String,
        mediaData: String?,
        fileName: String?,
        mimeType: String?,
        sizeBytes: Long?,
        durationMs: Long?,
        waveform: List<Int>?
    ) -> Unit,
    onSendBitmap: (ChatRoom, Bitmap, String, Boolean) -> Unit,
    onComposerTextChanged: (String, String) -> Unit,
    onStopTyping: (String) -> Unit,
    onMarkMessagesDelivered: (String, List<ChatMessage>, ChatRoom?) -> Unit = { _, _, _ -> },
    onMarkMessagesRead: (String, List<ChatMessage>, ChatRoom?) -> Unit,
    onSetReply: (ChatMessage?) -> Unit,
    onSetForward: (ChatMessage?) -> Unit,
    onConfirmForward: (ChatRoom) -> Unit,
    onToggleReaction: (String, ChatMessage, String) -> Unit,
    onDeleteMessage: (String, String) -> Unit,
    onTogglePin: (ChatRoom) -> Unit,
    onToggleArchive: (ChatRoom) -> Unit,
    onToggleLockChat: (ChatRoom) -> Unit = {},
    onSetDisappearingTimer: (ChatRoom, Long) -> Unit = { _, _ -> },
    onBlockUser: (PublicProfile) -> Unit = {},
    onUnblockUser: (String, String) -> Unit = { _, _ -> },
    onSubmitReport: (
        reportedUserId: String,
        reportedUserName: String,
        category: ReportCategory,
        description: String,
        chatId: String,
        messageId: String,
        messagePreview: String,
        onSubmitted: (String) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onRotateE2eeKeys: () -> Unit = {},
    onDeleteChat: (ChatRoom) -> Unit,
    onUpdateChatTheme: (ChatThemePreference?) -> Unit = {},
    onStartVoiceCall: (ChatRoom, PublicProfile) -> Unit = { _, _ -> },
    onStartVideoCall: (ChatRoom, PublicProfile) -> Unit = { _, _ -> },
    onOpenAiStudioWithBitmap: (Bitmap?, String) -> Unit,
    onNotify: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val effectiveAccent = if (chatTheme.accent == ChatAccentOption.DEFAULT) {
        MaterialTheme.colorScheme.primary
    } else {
        chatTheme.accent.accentColor(isDarkTheme)
    }
    val effectiveOnAccent = if (chatTheme.accent == ChatAccentOption.DEFAULT) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        chatTheme.accent.onAccentColor(isDarkTheme)
    }

    var inputText by remember { mutableStateOf("") }
    var showEmojiBar by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var inspectReadInfoMessageId by remember { mutableStateOf<String?>(null) }
    var showSafetyNumberDialog by remember { mutableStateOf(false) }
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Triple<String, String, ReportCategory>?>(null) }
    var reportMessageContext by remember { mutableStateOf<ChatMessage?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val latestChatId by rememberUpdatedState(chat?.id ?: chatId)
    val latestOnStopTyping by rememberUpdatedState(onStopTyping)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> {
                    isResumed = false
                    latestChatId.takeIf { it.isNotBlank() }?.let(latestOnStopTyping)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Pre-send Image Preview state
    var pendingPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCaption by remember { mutableStateOf("") }

    // Full-screen viewer state
    var viewerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewerCaption by remember { mutableStateOf("") }
    var viewerIsAi by remember { mutableStateOf(false) }

    // Voice recording & playback
    val voiceRecorder = remember(context) { VoiceRecorderManager(context) }
    val voicePlayer = remember(context) { VoicePlayerManager(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationMs by remember { mutableLongStateOf(0L) }
    val recordedWaveform = remember { mutableStateListOf<Int>() }

    var playingMessageId by remember { mutableStateOf<String?>(null) }
    var playingProgress by remember { mutableFloatStateOf(0f) }
    var isAudioPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.stopAndDiscard()
            voicePlayer.stop()
        }
    }

    DisposableEffect(chat?.id) {
        val currentChatId = chat?.id
        onDispose {
            if (!currentChatId.isNullOrBlank()) {
                onStopTyping(currentChatId)
            }
        }
    }

    // Recording amplitude & timer loop
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationMs = 0L
            recordedWaveform.clear()
            while (isRecording) {
                delay(200L)
                recordingDurationMs += 200L
                val amp = voiceRecorder.sampleNormalizedAmplitude()
                if (recordedWaveform.size < 32) {
                    recordedWaveform.add(amp)
                } else {
                    recordedWaveform.removeAt(0)
                    recordedWaveform.add(amp)
                }
            }
        }
    }

    // Playback progress updater
    LaunchedEffect(playingMessageId, isAudioPlaying) {
        while (playingMessageId != null && isAudioPlaying) {
            delay(150L)
            val id = playingMessageId ?: break
            val (prog, stillPlaying) = voicePlayer.currentProgress(id)
            playingProgress = prog
            isAudioPlaying = stillPlaying
            if (!stillPlaying && prog == 0f) {
                playingMessageId = null
            }
        }
    }

    var typingClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hasRemoteTypingFlag = chat?.typingUids?.any { it != currentUserId } == true
    LaunchedEffect(hasRemoteTypingFlag, chat?.typingUids, chat?.typingTimestamps) {
        typingClockMs = System.currentTimeMillis()
        if (hasRemoteTypingFlag) {
            while (true) {
                delay(1000L)
                typingClockMs = System.currentTimeMillis()
            }
        }
    }

    var presenceClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-scroll when new messages arrive and mark visible unread messages read when chat is open in foreground
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val latestOutgoingMessageId = remember(messages, currentUserId) {
        messages.lastOrNull { it.senderId == currentUserId && !it.deleted }?.id
    }
    LaunchedEffect(messages.size, chatId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(chatId, messages, chat?.deliveredByUids) {
        onMarkMessagesDelivered(chatId, messages, chat)
    }
    LaunchedEffect(chatId, messages, chat?.readByUids, isResumed) {
        if (isResumed) {
            onMarkMessagesRead(chatId, messages, chat)
        }
    }

    // Pickers & Permissions
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = MediaUtils.uriToBitmap(context, uri)
                if (bmp != null) {
                    pendingPreviewBitmap = bmp
                    pendingCaption = ""
                } else {
                    onNotify("Could not read selected image")
                }
            }
        }
    }

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        if (success && uri != null) {
            scope.launch {
                val bmp = MediaUtils.uriToBitmap(context, uri)
                if (bmp != null) {
                    pendingPreviewBitmap = bmp
                    pendingCaption = ""
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = MediaUtils.createCameraPhotoUri(context)
            cameraTempUri = uri
            cameraLauncher.launch(uri)
        } else {
            onNotify("Camera permission is needed to take photos")
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val room = chat ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val payload = MediaUtils.readVideoPayload(context, uri)
                onSendMedia(
                    room,
                    MessageType.VIDEO,
                    payload.fileName,
                    payload.thumbnailBase64,
                    payload.fileName,
                    payload.mimeType,
                    payload.sizeBytes,
                    payload.durationMs,
                    null
                )
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val room = chat ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val doc = MediaUtils.readDocumentPayload(context, uri)
                if (doc.base64Data == null) {
                    onNotify("Shared document info (files > 240 KB sync metadata only)")
                }
                onSendMedia(
                    room,
                    MessageType.DOCUMENT,
                    doc.fileName,
                    doc.base64Data,
                    doc.fileName,
                    doc.mimeType,
                    doc.sizeBytes,
                    null,
                    null
                )
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.startRecording()
                .onSuccess { isRecording = true }
                .onFailure { onNotify(it.localizedMessage ?: "Microphone unavailable") }
        } else {
            onNotify("Microphone permission is required to record voice notes")
        }
    }

    val roomTitle = chat?.displayTitleFor(currentUserId) ?: "Conversation"
    val otherUid = chat?.otherMemberUid(currentUserId)
    val otherProfile = otherUid?.let { usersMap[it] }
    val isPeerBlocked = otherUid != null && otherUid != currentUserId && blockedUserIds.contains(otherUid)
    val isBlockedEitherWay = otherUid != null && otherUid != currentUserId && (
        blockedUserIds.contains(otherUid) || blockedEitherWayUserIds.contains(otherUid)
        )
    val isContact = remember(otherUid, currentUserId, allChats) {
        if (otherUid == null) false
        else PrivacyVisibilityResolver.isContactOfViewer(currentUserId, otherUid, allChats)
    }
    LaunchedEffect(otherProfile, otherProfile?.lastHeartbeatAt, otherProfile?.activeSessions, otherProfile?.isOnline) {
        val now = System.currentTimeMillis()
        presenceClockMs = now
        if (PrivacyVisibilityResolver.isSessionActive(otherProfile, now)) {
            while (true) {
                delay(5000L)
                presenceClockMs = System.currentTimeMillis()
            }
        }
    }
    val peerVisibleOnline = remember(otherProfile, currentUserId, isBlockedEitherWay, isContact, presenceClockMs) {
        PrivacyVisibilityResolver.isEffectivelyOnline(
            target = otherProfile,
            viewerUid = currentUserId,
            isBlockedEitherWay = isBlockedEitherWay,
            isContact = isContact,
            nowMillis = presenceClockMs
        )
    }

    val activeTyperUids = remember(chat, currentUserId, typingClockMs) {
        chat?.activeTypingUids(currentUserId, typingClockMs).orEmpty()
    }
    val typingSummary = remember(chat, usersMap, currentUserId, typingClockMs) {
        chat?.typingSummaryLabel(currentUserId, usersMap, typingClockMs).orEmpty()
    }
    val activeTyperPairs = remember(activeTyperUids, chat, usersMap) {
        activeTyperUids.map { uid ->
            val profile = usersMap[uid]
            val name = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: chat?.memberNames?.get(uid)?.takeIf { it.isNotBlank() }
                ?: "Someone"
            name to profile
        }
    }
    val othersTyping = activeTyperUids.isNotEmpty() && !isBlockedEitherWay

    LaunchedEffect(othersTyping, activeTyperUids.size) {
        if (othersTyping) {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    val statusSubtitle = remember(chat, otherUid, otherProfile, isPeerBlocked, isBlockedEitherWay, isContact, currentUserId, presenceClockMs) {
        when {
            chat?.isGroup == true -> "${chat.memberUids.size} members"
            isPeerBlocked -> "Blocked user"
            otherUid == null -> "Personal notes"
            isBlockedEitherWay -> "Direct message"
            else -> {
                val lastSeenMs = PrivacyVisibilityResolver.effectiveLastSeenMillis(otherProfile)
                val formattedLastSeen = lastSeenMs?.let {
                    PresenceFormatter.formatRelativeLastSeen(it, nowMillis = presenceClockMs)
                }.orEmpty()
                PrivacyVisibilityResolver.resolvePresenceSubtitle(
                    target = otherProfile,
                    viewerUid = currentUserId,
                    isBlockedEitherWay = isBlockedEitherWay,
                    isContact = isContact,
                    formattedLastSeen = formattedLastSeen,
                    nowMillis = presenceClockMs
                ).ifBlank { "Direct message" }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to chats"
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UserAvatar(
                            name = roomTitle,
                            avatarData = otherProfile?.avatarData.orEmpty(),
                            photoUrl = otherProfile?.photoUrl.orEmpty(),
                            isOnline = peerVisibleOnline,
                            isGroup = chat?.isGroup ?: false,
                            size = 38.dp
                        )
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = roomTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (chatTheme.tag != ChatCategoryTag.NONE) {
                                    ChatCategoryBadge(
                                        tag = chatTheme.tag,
                                        accentColor = effectiveAccent
                                    )
                                }
                            }
                            if (othersTyping) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.testTag("top_bar_typing_status")
                                ) {
                                    TypingDots(
                                        color = effectiveAccent,
                                        dotSize = 4.dp
                                    )
                                    Text(
                                        text = if (chat?.isGroup == true) "$typingSummary..." else "is typing...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = effectiveAccent,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.testTag("chat_header_presence_status")
                                ) {
                                    if (peerVisibleOnline && !isBlockedEitherWay && chat?.isGroup != true) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(OnlineGreen)
                                                .testTag("chat_header_online_dot")
                                        )
                                    }
                                    Text(
                                        text = statusSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (peerVisibleOnline && !isBlockedEitherWay && chat?.isGroup != true) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = when {
                                            isPeerBlocked -> MaterialTheme.colorScheme.error
                                            peerVisibleOnline && chat?.isGroup != true -> OnlineGreen
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (chat != null) {
                        val callTargetProfile = remember(chat, otherUid, otherProfile, roomTitle, currentUserId, isPeerBlocked) {
                            if (!chat.isGroup && otherUid != null && otherUid != currentUserId && !isPeerBlocked) {
                                otherProfile ?: PublicProfile(userId = otherUid, displayName = roomTitle)
                            } else {
                                null
                            }
                        }
                        if (callTargetProfile != null) {
                            IconButton(
                                onClick = { onStartVoiceCall(chat, callTargetProfile) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("voice_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Call,
                                    contentDescription = "Start voice call",
                                    tint = effectiveAccent
                                )
                            }
                            IconButton(
                                onClick = { onStartVideoCall(chat, callTargetProfile) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("video_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Videocam,
                                    contentDescription = "Start video call",
                                    tint = effectiveAccent
                                )
                            }
                        }
                        IconButton(
                            onClick = { showThemeSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_theme_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = "Customize chat theme",
                                tint = effectiveAccent
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showHeaderMenu = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_more_options_button")
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Chat options")
                            }
                            DropdownMenu(
                                expanded = showHeaderMenu,
                                onDismissRequest = { showHeaderMenu = false }
                            ) {
                                val pinned = chat.isPinnedBy(currentUserId)
                                val archived = chat.isArchivedBy(currentUserId)
                                DropdownMenuItem(
                                    text = { Text("Disappearing messages (${chat.disappearingOption.shortLabel})") },
                                    leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        showDisappearingDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isChatLocked) "Unlock chat" else "Lock chat") },
                                    leadingIcon = {
                                        Icon(
                                            if (isChatLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        onToggleLockChat(chat)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Encryption & Safety Number") },
                                    leadingIcon = { Icon(Icons.Rounded.EnhancedEncryption, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        showSafetyNumberDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Customize theme") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Palette,
                                            contentDescription = null,
                                            tint = effectiveAccent
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        showThemeSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (pinned) "Unpin" else "Pin chat") },
                                    leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        onTogglePin(chat)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (archived) "Unarchive" else "Archive") },
                                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        onToggleArchive(chat)
                                    }
                                )
                                if (!chat.isGroup && otherUid != null && otherUid != currentUserId) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isPeerBlocked) "Unblock user" else "Block user",
                                                color = if (isPeerBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Block,
                                                contentDescription = null,
                                                tint = if (isPeerBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showHeaderMenu = false
                                            if (isPeerBlocked) {
                                                onUnblockUser(otherUid, roomTitle)
                                            } else {
                                                showBlockConfirmDialog = true
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Report user") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Report,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showHeaderMenu = false
                                            reportMessageContext = null
                                            reportTarget = Triple(otherUid, roomTitle, ReportCategory.HARASSMENT)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete chat") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        onDeleteChat(chat)
                                        onBack()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Reply banner
                AnimatedVisibility(visible = replyingTo != null) {
                    val reply = replyingTo
                    if (reply != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Replying to ${reply.senderName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = reply.text.ifBlank { reply.decryptionError.ifBlank { reply.type } },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onSetReply(null) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Cancel reply")
                                }
                            }
                        }
                    }
                }

                // Quick Emoji Bar
                AnimatedVisibility(visible = showEmojiBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        QUICK_EMOJIS.forEach { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier
                                    .clickable {
                                        val nextText = inputText + emoji
                                        inputText = nextText
                                        chat?.let { room ->
                                            onComposerTextChanged(room.id, nextText)
                                        }
                                    }
                                    .padding(6.dp)
                            )
                        }
                    }
                }

                // Voice Recording Bar OR Blocked Notice OR Standard Composer
                if (isPeerBlocked) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "You blocked $roomTitle",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { onUnblockUser(otherUid, roomTitle) },
                                modifier = Modifier.testTag("chat_unblock_button")
                            ) {
                                Text("Unblock")
                            }
                        }
                    }
                } else if (isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                voiceRecorder.stopAndDiscard()
                                isRecording = false
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("cancel_voice_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Cancel voice recording",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                                Text(
                                    text = MediaUtils.formatDuration(recordingDurationMs),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Recording voice note...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = effectiveAccent,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    val room = chat ?: return@clickable
                                    val snapshotBars = recordedWaveform.toList().ifEmpty { listOf(20, 45, 70, 40) }
                                    voiceRecorder.stopAndEncode()
                                        .onSuccess { (b64, durMs, sizeBytes) ->
                                            isRecording = false
                                            onSendMedia(
                                                room,
                                                MessageType.VOICE,
                                                "Voice note",
                                                b64,
                                                "voice_note.m4a",
                                                "audio/mp4",
                                                sizeBytes,
                                                durMs,
                                                snapshotBars
                                            )
                                        }
                                        .onFailure { e ->
                                            isRecording = false
                                            onNotify(e.localizedMessage ?: "Voice note failed")
                                        }
                                }
                                .testTag("send_voice_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send voice note",
                                    tint = effectiveOnAccent
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { showAttachSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("attach_media_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Attach media or AI image"
                            )
                        }

                        IconButton(
                            onClick = { showEmojiBar = !showEmojiBar },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EmojiEmotions,
                                contentDescription = "Toggle emojis",
                                tint = if (showEmojiBar) effectiveAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { newText ->
                                inputText = newText
                                chat?.let { room ->
                                    onComposerTextChanged(room.id, newText)
                                }
                            },
                            placeholder = { Text("Message...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_input")
                        )

                        if (inputText.isNotBlank()) {
                            Surface(
                                shape = CircleShape,
                                color = effectiveAccent,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        val room = chat ?: return@clickable
                                        val body = inputText
                                        inputText = ""
                                        onSendText(room, body)
                                    }
                                    .testTag("send_message_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Send message",
                                        tint = effectiveOnAccent
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = effectiveAccent.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            voiceRecorder.startRecording()
                                                .onSuccess { isRecording = true }
                                                .onFailure { onNotify(it.localizedMessage ?: "Mic error") }
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                    .testTag("record_voice_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "Record voice note",
                                        tint = effectiveAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ChatThemedBackground(
                theme = chatTheme,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.fillMaxSize()
            )
            when (messagesState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Text(
                        text = messagesState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp)
                    )
                }
                is UiState.Success -> {
                    if (messagesState.data.isEmpty() && !othersTyping) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No messages yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Send a text, record a voice note, or create an AI image with +.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (chat != null && (e2eeEnabled || chat.disappearingDurationSeconds > 0L || chat.isArchivedBy(currentUserId))) {
                                item(key = "security_header_pill") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (chat.isArchivedBy(currentUserId)) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                                modifier = Modifier
                                                    .clickable { onToggleArchive(chat) }
                                                    .testTag("archived_banner_chip")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Archive,
                                                        contentDescription = "Archived conversation",
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Archived conversation • Tap to unarchive",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                        if (e2eeEnabled) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                tonalElevation = 2.dp,
                                                modifier = Modifier
                                                    .clickable { showSafetyNumberDialog = true }
                                                    .testTag("e2ee_banner_chip")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.EnhancedEncryption,
                                                        contentDescription = "End-to-end encrypted",
                                                        tint = effectiveAccent,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "End-to-end encrypted • Tap to verify",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        if (chat.disappearingDurationSeconds > 0L) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                modifier = Modifier
                                                    .clickable { showDisappearingDialog = true }
                                                    .testTag("disappearing_banner_chip")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Timer,
                                                        contentDescription = "Disappearing messages timer",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Disappearing messages: ${chat.disappearingOption.label}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            items(messagesState.data, key = { it.id }) { msg ->
                                if (msg.messageType == MessageType.SYSTEM) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .animateItem(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                            tonalElevation = 1.dp,
                                            modifier = Modifier.testTag("system_notice_${msg.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (msg.systemEventType == "screenshot") {
                                                        Icons.Rounded.Screenshot
                                                    } else {
                                                        Icons.Rounded.Timer
                                                    },
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = msg.text,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    return@items
                                }

                                val isFromMe = msg.senderId == currentUserId
                                val linkSafety = remember(msg.id, msg.text) {
                                    SpamProtectionEngine.analyzeLinks(msg.text)
                                }
                                var msgMenuExpanded by remember { mutableStateOf(false) }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(),
                                    contentAlignment = if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Column(
                                        horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(
                                                topStart = 20.dp,
                                                topEnd = 20.dp,
                                                bottomStart = if (isFromMe) 20.dp else 6.dp,
                                                bottomEnd = if (isFromMe) 6.dp else 20.dp
                                            ),
                                            color = if (isFromMe) {
                                                effectiveAccent
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            modifier = Modifier
                                                .widthIn(max = 310.dp)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (!msg.deleted && msg.decryptionError.isBlank() &&
                                                            (msg.messageType == MessageType.IMAGE || msg.messageType == MessageType.AI_IMAGE)
                                                        ) {
                                                            val bmp = MediaUtils.decodeBase64ToBitmap(msg.mediaData)
                                                            if (bmp != null) {
                                                                viewerBitmap = bmp
                                                                viewerCaption = msg.text
                                                                viewerIsAi = msg.messageType == MessageType.AI_IMAGE
                                                            }
                                                        }
                                                    },
                                                    onLongClick = { msgMenuExpanded = true }
                                                )
                                                .testTag("message_bubble_${msg.id}")
                                        ) {
                                            val textColor = if (isFromMe) {
                                                effectiveOnAccent
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (!isFromMe && chat?.isGroup == true) {
                                                    Text(
                                                        text = msg.senderName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                if (msg.forwardedFrom.isNotBlank() && !msg.deleted) {
                                                    Text(
                                                        text = "Forwarded from ${msg.forwardedFrom}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontStyle = FontStyle.Italic,
                                                        color = textColor.copy(alpha = 0.78f)
                                                    )
                                                }
                                                if (msg.replyToPreview.isNotBlank() && !msg.deleted) {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = textColor.copy(alpha = 0.12f),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(8.dp)) {
                                                            Text(
                                                                text = msg.replyToSenderName,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = textColor
                                                            )
                                                            Text(
                                                                text = msg.replyToPreview,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = textColor.copy(alpha = 0.85f),
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }

                                                if (msg.deleted) {
                                                    Text(
                                                        text = msg.text,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontStyle = FontStyle.Italic,
                                                        color = textColor.copy(alpha = 0.75f)
                                                    )
                                                } else if (msg.decryptionError.isNotBlank()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.testTag("decryption_error_${msg.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Lock,
                                                            contentDescription = "Decryption error",
                                                            tint = if (isFromMe) textColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = msg.decryptionError,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontStyle = FontStyle.Italic,
                                                            color = if (isFromMe) textColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                } else {
                                                    when (msg.messageType) {
                                                        MessageType.IMAGE, MessageType.AI_IMAGE -> {
                                                            val bmp = remember(msg.mediaData) {
                                                                MediaUtils.decodeBase64ToBitmap(msg.mediaData)
                                                            }
                                                            if (bmp != null) {
                                                                Box {
                                                                    Image(
                                                                        bitmap = bmp.asImageBitmap(),
                                                                        contentDescription = msg.text.ifBlank { "Shared image" },
                                                                        contentScale = ContentScale.Crop,
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .height(210.dp)
                                                                            .clip(RoundedCornerShape(14.dp))
                                                                    )
                                                                    if (msg.messageType == MessageType.AI_IMAGE) {
                                                                        Surface(
                                                                            shape = RoundedCornerShape(8.dp),
                                                                            color = Color.Black.copy(alpha = 0.65f),
                                                                            modifier = Modifier
                                                                                .align(Alignment.TopStart)
                                                                                .padding(8.dp)
                                                                        ) {
                                                                            Row(
                                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                            ) {
                                                                                Icon(
                                                                                    imageVector = Icons.Rounded.AutoAwesome,
                                                                                    contentDescription = null,
                                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                                    modifier = Modifier.size(13.dp)
                                                                                )
                                                                                Text(
                                                                                    text = "AI",
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    color = Color.White
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            if (msg.text.isNotBlank()) {
                                                                Text(
                                                                    text = msg.text,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = textColor
                                                                )
                                                            }
                                                        }
                                                        MessageType.VOICE -> {
                                                            VoiceNoteContent(
                                                                message = msg,
                                                                isFromMe = isFromMe,
                                                                isPlaying = playingMessageId == msg.id && isAudioPlaying,
                                                                progress = if (playingMessageId == msg.id) playingProgress else 0f,
                                                                onTogglePlay = {
                                                                    voicePlayer.playOrToggle(msg.id, msg.mediaData) { activeId, prog, playing ->
                                                                        playingMessageId = activeId
                                                                        playingProgress = prog
                                                                        isAudioPlaying = playing
                                                                    }
                                                                }
                                                            )
                                                        }
                                                        MessageType.DOCUMENT -> {
                                                            DocumentAttachmentContent(
                                                                message = msg,
                                                                isFromMe = isFromMe,
                                                                onOpenDocument = {
                                                                    if (msg.mediaData.isNotBlank()) {
                                                                        MediaUtils.openBase64Document(
                                                                            context,
                                                                            msg.mediaData,
                                                                            msg.mediaFileName,
                                                                            msg.mediaMimeType
                                                                        ).onFailure {
                                                                            onNotify("No app found to open ${msg.mediaFileName}")
                                                                        }
                                                                    } else {
                                                                        onNotify("Large document metadata only")
                                                                    }
                                                                }
                                                            )
                                                        }
                                                        MessageType.VIDEO -> {
                                                            VideoAttachmentContent(
                                                                message = msg,
                                                                isFromMe = isFromMe
                                                            )
                                                        }
                                                        MessageType.TEXT -> {
                                                            Text(
                                                                text = msg.text,
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = textColor
                                                            )
                                                        }
                                                        MessageType.CALL -> {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (msg.text.contains("Video", ignoreCase = true)) {
                                                                        Icons.Rounded.Videocam
                                                                    } else {
                                                                        Icons.Rounded.Call
                                                                    },
                                                                    contentDescription = null,
                                                                    tint = textColor,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Text(
                                                                    text = msg.text.ifBlank { "Call" },
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = textColor
                                                                )
                                                            }
                                                        }
                                                        MessageType.SYSTEM -> {
                                                            Text(
                                                                text = msg.text,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = textColor
                                                            )
                                                        }
                                                    }
                                                }

                                                if (!isFromMe && !msg.deleted && linkSafety.hasSuspiciousLinks) {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Warning,
                                                                contentDescription = "Suspicious link warning",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Text(
                                                                text = "Suspicious link: ${linkSafety.reasons.firstOrNull().orEmpty()}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Text(
                                                                text = "Report",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.clickable {
                                                                    reportMessageContext = msg
                                                                    reportTarget = Triple(
                                                                        msg.senderId,
                                                                        msg.senderName.ifBlank { roomTitle },
                                                                        ReportCategory.SUSPICIOUS_LINK
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                // Timestamp & Read receipt row
                                                Row(
                                                    modifier = Modifier.align(Alignment.End),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (msg.isEncrypted) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Lock,
                                                            contentDescription = "Encrypted",
                                                            tint = textColor.copy(alpha = 0.65f),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                    }
                                                    if (msg.expiresAtEpochMs > 0L) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Timer,
                                                            contentDescription = "Disappearing message",
                                                            tint = textColor.copy(alpha = 0.65f),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                    }
                                                    val timeStr = msg.createdAt?.toDate()?.let { timeFormat.format(it) }.orEmpty()
                                                    Text(
                                                        text = timeStr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = textColor.copy(alpha = 0.72f)
                                                    )
                                                    if (isFromMe && !msg.deleted) {
                                                        val receiptStatus = msg.readStatusForSender(currentUserId)
                                                        val (icon, desc, alpha) = when (receiptStatus) {
                                                            ReadReceiptStatus.SENDING -> Triple(Icons.Rounded.Schedule, "Sending", 0.65f)
                                                            ReadReceiptStatus.SENT -> Triple(Icons.Rounded.Done, "Sent", 0.72f)
                                                            ReadReceiptStatus.DELIVERED -> Triple(Icons.Rounded.DoneAll, "Delivered", 0.62f)
                                                            ReadReceiptStatus.READ -> Triple(Icons.Rounded.DoneAll, "Read", 1f)
                                                        }
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = desc,
                                                            tint = textColor.copy(alpha = alpha),
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .testTag("message_receipt_icon_${msg.id}")
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (isFromMe && !msg.deleted && msg.id == latestOutgoingMessageId) {
                                            val receiptStatus = msg.readStatusForSender(currentUserId)
                                            val readTimeStr = msg.latestReadTimestamp(currentUserId)
                                                ?.toDate()
                                                ?.let { timeFormat.format(it) }
                                                .orEmpty()
                                            val deliveredTimeStr = msg.latestDeliveredTimestamp(currentUserId)
                                                ?.toDate()
                                                ?.let { timeFormat.format(it) }
                                                .orEmpty()
                                            val receiptLabel = msg.readStatusLabel(
                                                currentUid = currentUserId,
                                                chat = chat,
                                                usersMap = usersMap,
                                                formattedReadTime = readTimeStr,
                                                formattedDeliveredTime = deliveredTimeStr
                                            )
                                            val isRead = receiptStatus == ReadReceiptStatus.READ
                                            val statusColor = if (isRead) {
                                                effectiveAccent
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .padding(top = 3.dp, end = 4.dp)
                                                    .testTag("latest_read_receipt_status")
                                            ) {
                                                Icon(
                                                    imageVector = when (receiptStatus) {
                                                        ReadReceiptStatus.SENDING -> Icons.Rounded.Schedule
                                                        ReadReceiptStatus.SENT -> Icons.Rounded.Done
                                                        ReadReceiptStatus.DELIVERED -> Icons.Rounded.DoneAll
                                                        ReadReceiptStatus.READ -> Icons.Rounded.DoneAll
                                                    },
                                                    contentDescription = receiptLabel,
                                                    tint = statusColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = receiptLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isRead) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = statusColor
                                                )
                                            }
                                        }

                                        // Reactions Pill
                                        if (msg.reactions.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                tonalElevation = 2.dp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text(
                                                    text = msg.reactions.values.groupingBy { it }.eachCount()
                                                        .entries.joinToString(" ") { (emoji, count) ->
                                                            if (count > 1) "$emoji $count" else emoji
                                                        },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = msgMenuExpanded,
                                            onDismissRequest = { msgMenuExpanded = false }
                                        ) {
                                            if (!msg.deleted && chat != null) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    listOf("👍", "❤️", "😂", "🔥", "✨").forEach { emoji ->
                                                        Text(
                                                            text = emoji,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            modifier = Modifier
                                                                .clickable {
                                                                    msgMenuExpanded = false
                                                                    onToggleReaction(chat.id, msg, emoji)
                                                                }
                                                                .padding(4.dp)
                                                        )
                                                    }
                                                }
                                                DropdownMenuItem(
                                                    text = { Text("Reply") },
                                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null) },
                                                    onClick = {
                                                        msgMenuExpanded = false
                                                        onSetReply(msg)
                                                    }
                                                )
                                                if (msg.decryptionError.isBlank()) {
                                                    DropdownMenuItem(
                                                        text = { Text("Forward") },
                                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Forward, contentDescription = null) },
                                                        onClick = {
                                                            msgMenuExpanded = false
                                                            onSetForward(msg)
                                                        }
                                                    )
                                                    if (msg.messageType == MessageType.IMAGE || msg.messageType == MessageType.AI_IMAGE) {
                                                        DropdownMenuItem(
                                                            text = { Text("Edit with AI") },
                                                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                                                            onClick = {
                                                                msgMenuExpanded = false
                                                                val bmp = MediaUtils.decodeBase64ToBitmap(msg.mediaData)
                                                                if (bmp != null) {
                                                                    onOpenAiStudioWithBitmap(bmp, msg.text)
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                                if (!isFromMe) {
                                                    DropdownMenuItem(
                                                        text = { Text("Report message") },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.Report,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        },
                                                        onClick = {
                                                            msgMenuExpanded = false
                                                            reportMessageContext = msg
                                                            reportTarget = Triple(
                                                                msg.senderId,
                                                                msg.senderName.ifBlank { roomTitle },
                                                                ReportCategory.INAPPROPRIATE_CONTENT
                                                            )
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Report as spam") },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.Warning,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        },
                                                        onClick = {
                                                            msgMenuExpanded = false
                                                            reportMessageContext = msg
                                                            reportTarget = Triple(
                                                                msg.senderId,
                                                                msg.senderName.ifBlank { roomTitle },
                                                                ReportCategory.SPAM
                                                            )
                                                        }
                                                    )
                                                }
                                                if (isFromMe) {
                                                    DropdownMenuItem(
                                                        text = { Text("Message Info") },
                                                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                                        onClick = {
                                                            msgMenuExpanded = false
                                                            inspectReadInfoMessageId = msg.id
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Delete") },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.Delete,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        },
                                                        onClick = {
                                                            msgMenuExpanded = false
                                                            onDeleteMessage(chat.id, msg.id)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (othersTyping) {
                                item(key = "typing_indicator") {
                                    TypingIndicatorBubble(
                                        typers = activeTyperPairs,
                                        label = typingSummary,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Attachment Action Bottom Sheet
    if (showAttachSheet && chat != null) {
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Share or Create",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AttachmentTile(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "AI Image",
                        highlight = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("attach_ai_image_button"),
                        onClick = {
                            showAttachSheet = false
                            onOpenAiStudioWithBitmap(null, "")
                        }
                    )
                    AttachmentTile(
                        icon = Icons.Rounded.PhotoLibrary,
                        label = "Photo",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachSheet = false
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    AttachmentTile(
                        icon = Icons.Rounded.CameraAlt,
                        label = "Camera",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachSheet = false
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                val uri = MediaUtils.createCameraPhotoUri(context)
                                cameraTempUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AttachmentTile(
                        icon = Icons.Rounded.Videocam,
                        label = "Video",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachSheet = false
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                    AttachmentTile(
                        icon = Icons.Rounded.Description,
                        label = "Document",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachSheet = false
                            documentPicker.launch(arrayOf("*/*"))
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // Pre-send Image Preview Dialog
    val previewBmp = pendingPreviewBitmap
    if (previewBmp != null && chat != null) {
        AlertDialog(
            onDismissRequest = { pendingPreviewBitmap = null },
            title = { Text("Preview Photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        bitmap = previewBmp.asImageBitmap(),
                        contentDescription = "Photo preview before sending",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    OutlinedTextField(
                        value = pendingCaption,
                        onValueChange = { pendingCaption = it },
                        label = { Text("Add a caption...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            val bmpToEdit = previewBmp
                            pendingPreviewBitmap = null
                            onOpenAiStudioWithBitmap(bmpToEdit, pendingCaption)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit with Gemini AI First")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bmpToSend = previewBmp
                        val cap = pendingCaption
                        pendingPreviewBitmap = null
                        onSendBitmap(chat, bmpToSend, cap, false)
                    }
                ) {
                    Text("Send Photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPreviewBitmap = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full-screen Image Viewer
    val fsBmp = viewerBitmap
    if (fsBmp != null) {
        FullScreenImageDialog(
            bitmap = fsBmp,
            caption = viewerCaption,
            isAiImage = viewerIsAi,
            onDismiss = { viewerBitmap = null },
            onSaveToGallery = {
                scope.launch {
                    MediaUtils.saveBitmapToGallery(context, fsBmp)
                        .onSuccess { onNotify("Saved to Photos/PulseChat") }
                        .onFailure { onNotify(it.localizedMessage ?: "Failed to save image") }
                }
            },
            onEditWithAi = {
                viewerBitmap = null
                onOpenAiStudioWithBitmap(fsBmp, viewerCaption)
            }
        )
    }

    // Forward Message Sheet
    if (forwardingMessage != null) {
        ChatPickerBottomSheet(
            title = "Forward message to...",
            chats = allChats,
            currentUserId = currentUserId,
            onSelectChat = onConfirmForward,
            onDismiss = { onSetForward(null) }
        )
    }

    // Message Read Receipt Info Sheet
    val inspectMessage = inspectReadInfoMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    if (inspectMessage != null) {
        MessageReadInfoBottomSheet(
            message = inspectMessage,
            chat = chat,
            currentUserId = currentUserId,
            usersMap = usersMap,
            blockedUserIds = blockedUserIds + blockedEitherWayUserIds,
            allChats = allChats,
            nowMillis = presenceClockMs,
            onDismiss = { inspectReadInfoMessageId = null }
        )
    }

    // Per-Chat Background & Accent Theme Sheet
    if (showThemeSheet && chat != null) {
        ChatThemeBottomSheet(
            chatTitle = roomTitle,
            initialTheme = chatTheme,
            isDarkTheme = isDarkTheme,
            onApplyTheme = onUpdateChatTheme,
            onDismiss = { showThemeSheet = false }
        )
    }

    if (showSafetyNumberDialog && chat != null) {
        SafetyNumberDialog(
            peerName = roomTitle,
            safetyNumber = safetyNumber,
            myKeyFingerprint = myKeyFingerprint,
            myKeyVersion = myKeyVersion,
            peerHasKeyBundle = peerHasKeyBundle,
            onRotateKeys = onRotateE2eeKeys,
            onDismiss = { showSafetyNumberDialog = false }
        )
    }

    if (showDisappearingDialog && chat != null) {
        DisappearingTimerDialog(
            title = "Disappearing Messages",
            subtitle = "New messages in this conversation will automatically disappear and be removed after the selected timer.",
            currentDurationSeconds = chat.disappearingDurationSeconds,
            onSelectDuration = { seconds -> onSetDisappearingTimer(chat, seconds) },
            onDismiss = { showDisappearingDialog = false }
        )
    }

    if (showBlockConfirmDialog && otherUid != null) {
        val target = otherProfile ?: PublicProfile(userId = otherUid, displayName = roomTitle)
        BlockUserConfirmDialog(
            displayName = target.displayName,
            onConfirmBlock = { onBlockUser(target) },
            onDismiss = { showBlockConfirmDialog = false }
        )
    }

    val activeReportTarget = reportTarget
    if (activeReportTarget != null && chat != null) {
        val (targetUid, targetName, initialCat) = activeReportTarget
        val msgCtx = reportMessageContext
        ReportDialog(
            title = if (msgCtx != null) "Report Message" else "Report Account",
            targetUserName = targetName,
            messagePreview = msgCtx?.text.orEmpty(),
            initialCategory = initialCat,
            onSubmit = { category, description, done ->
                onSubmitReport(
                    targetUid,
                    targetName,
                    category,
                    description,
                    chat.id,
                    msgCtx?.id.orEmpty(),
                    msgCtx?.text.orEmpty(),
                    done
                )
            },
            onDismiss = {
                reportTarget = null
                reportMessageContext = null
            }
        )
    }
}

@Composable
private fun AttachmentTile(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .height(86.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
