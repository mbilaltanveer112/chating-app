package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.WindowSecurityController
import com.example.call.CallActionType
import com.example.call.CallEventBus
import com.example.data.auth.AuthSessionStore
import com.example.data.model.ActiveCallUiState
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.ChatRoom
import com.example.data.model.ReportCategory
import com.example.data.model.formatCallDuration
import com.example.data.repository.ChatRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.TwoStepChallengeOverlay
import com.example.ui.components.UnlockSecurityDialog
import com.example.ui.components.findFragmentActivity
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.CallsHistoryScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.NewChatDialog
import com.example.ui.screens.PrivacySecurityScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.ChatThemePreference
import com.example.ui.theme.PulseAuthTheme
import com.example.ui.theme.PulseChatTheme
import com.example.ui.viewmodel.PulseChatViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class TopDestination(val label: String) {
    CHATS("Chats"),
    CALLS("Calls"),
    AI_STUDIO("AI Studio"),
    PROFILE("Profile")
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    initialChatId: String? = null,
    initialCallId: String? = null,
    initialCallAction: CallActionType? = null
) {
    val context = LocalContext.current
    val auth = Firebase.auth
    val sessionStore = remember(context) { AuthSessionStore(context) }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    val systemDarkTheme = isSystemInDarkTheme()

    var isHoldingAuthUi by remember { mutableStateOf(false) }
    var checkedRememberMeForUid by remember { mutableStateOf<String?>(null) }
    var checkedPhoneUid by remember { mutableStateOf<String?>(null) }
    var needsPhoneProfileSetup by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid, isHoldingAuthUi) {
        if (user == null) {
            isHoldingAuthUi = false
            checkedRememberMeForUid = null
            checkedPhoneUid = null
            needsPhoneProfileSetup = false
        } else if (!isHoldingAuthUi) {
            if (!AuthSessionStore.isSessionActiveInProcess() && checkedRememberMeForUid != user.uid) {
                checkedRememberMeForUid = user.uid
                if (!sessionStore.isRememberMeEnabled()) {
                    AuthSessionStore.clearInProcessSession()
                    auth.signOut()
                    return@LaunchedEffect
                }
                AuthSessionStore.markSessionActiveInProcess()
            }
            if (checkedPhoneUid != user.uid) {
                val isPhoneOnly = user.providerData.any { it.providerId == PhoneAuthProvider.PROVIDER_ID } &&
                    user.providerData.none { it.providerId == "google.com" }
                if (isPhoneOnly) {
                    val exists = ChatRepository(context.applicationContext).doesUserProfileExist(user.uid)
                    needsPhoneProfileSetup = !exists
                } else {
                    needsPhoneProfileSetup = false
                }
                checkedPhoneUid = user.uid
            }
        }
    }

    when {
        user == null || isHoldingAuthUi -> {
            PulseAuthTheme(darkTheme = systemDarkTheme) {
                AuthScreen(
                    onHoldAuthNavigation = { hold -> isHoldingAuthUi = hold },
                    onAuthSuccess = {
                        isHoldingAuthUi = false
                        checkedPhoneUid = auth.currentUser?.uid
                        needsPhoneProfileSetup = false
                    }
                )
            }
        }
        checkedPhoneUid != user.uid -> {
            PulseAuthTheme(darkTheme = systemDarkTheme) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        needsPhoneProfileSetup -> {
            PulseAuthTheme(darkTheme = systemDarkTheme) {
                AuthScreen(
                    initialProfileSetupUid = user.uid,
                    initialProfileSetupPhone = user.phoneNumber,
                    onHoldAuthNavigation = { hold -> isHoldingAuthUi = hold },
                    onAuthSuccess = {
                        isHoldingAuthUi = false
                        needsPhoneProfileSetup = false
                        checkedPhoneUid = user.uid
                    }
                )
            }
        }
        else -> {
            key(user.uid) {
                AuthenticatedPulseChatApp(
                    currentUserId = user.uid,
                    initialChatId = initialChatId,
                    initialCallId = initialCallId,
                    initialCallAction = initialCallAction
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedPulseChatApp(
    currentUserId: String,
    initialChatId: String?,
    initialCallId: String?,
    initialCallAction: CallActionType?
) {
    val context = LocalContext.current
    val viewModel: PulseChatViewModel = viewModel(
        key = currentUserId,
        factory = PulseChatViewModel.provideFactory(context, currentUserId)
    )

    val privateUser by viewModel.privateUser.collectAsStateWithLifecycle()
    val chatsState by viewModel.chats.collectAsStateWithLifecycle()
    val usersState by viewModel.discoverableUsers.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val forwardingMessage by viewModel.forwardingMessage.collectAsStateWithLifecycle()
    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
    val aiSourceBitmap by viewModel.aiSourceBitmap.collectAsStateWithLifecycle()
    val aiSuggestedPrompt by viewModel.aiSuggestedPrompt.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val callHistoryState by viewModel.callHistory.collectAsStateWithLifecycle()

    val privacySettings by viewModel.privacySettings.collectAsStateWithLifecycle()
    val twoStepConfig by viewModel.twoStepConfig.collectAsStateWithLifecycle()
    val chatLockConfig by viewModel.chatLockConfig.collectAsStateWithLifecycle()
    val spamGuardState by viewModel.spamGuardState.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val blockedUserIds by viewModel.blockedUserIds.collectAsStateWithLifecycle()
    val blockedEitherWayUserIds by viewModel.blockedEitherWayUserIds.collectAsStateWithLifecycle()
    val peerProfilesMap by viewModel.peerProfilesMap.collectAsStateWithLifecycle()
    val blockHistory by viewModel.blockHistory.collectAsStateWithLifecycle()
    val keyBundles by viewModel.keyBundles.collectAsStateWithLifecycle()
    val localE2eeMaterial by viewModel.localE2eeMaterial.collectAsStateWithLifecycle()
    val myReports by viewModel.myReports.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val adminReports by viewModel.adminReports.collectAsStateWithLifecycle()
    val requiresTwoStepChallenge by viewModel.requiresTwoStepChallenge.collectAsStateWithLifecycle()
    val shouldBlockScreenshots by viewModel.shouldBlockScreenshots.collectAsStateWithLifecycle()
    val lockedVaultUnlocked by viewModel.lockedVaultUnlocked.collectAsStateWithLifecycle()
    val unlockedChatIds by viewModel.unlockedChatIds.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val hostActivity = remember(context) { context.findFragmentActivity() }

    LaunchedEffect(hostActivity, shouldBlockScreenshots) {
        WindowSecurityController.setFlagSecure(hostActivity, shouldBlockScreenshots)
    }

    var currentTab by remember { mutableStateOf(TopDestination.CHATS) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showPrivacySecurityScreen by remember { mutableStateOf(false) }
    var privacyInitialSection by remember { mutableStateOf<String?>(null) }
    var pendingChatToUnlock by remember { mutableStateOf<ChatRoom?>(null) }
    var consumedInitialChatId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingCallOperation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRequiresCamera by remember { mutableStateOf(false) }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val camGranted = !pendingRequiresCamera || (
            results[Manifest.permission.CAMERA]
                ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            )
        val op = pendingCallOperation
        pendingCallOperation = null
        if (micGranted && camGranted) {
            op?.invoke()
        } else {
            val needed = if (pendingRequiresCamera) "Microphone and Camera permissions" else "Microphone permission"
            viewModel.notifyMessage("$needed are required to place or answer calls")
        }
    }

    fun runWithCallPermissions(isVideo: Boolean, action: () -> Unit) {
        val required = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.CAMERA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val mediaMissing = required.any {
            it == Manifest.permission.RECORD_AUDIO || it == Manifest.permission.CAMERA
        }
        if (!mediaMissing) {
            action()
        } else {
            pendingRequiresCamera = isVideo
            pendingCallOperation = action
            callPermissionsLauncher.launch(required.toTypedArray())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUserId, hostActivity) {
        val initiallyStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        viewModel.onForegroundChanged(initiallyStarted)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForegroundChanged(true)
                Lifecycle.Event.ON_STOP -> {
                    if (hostActivity?.isChangingConfigurations != true) {
                        viewModel.onForegroundChanged(false)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(initialChatId, chatLockConfig.lockedChatIds) {
        if (!initialChatId.isNullOrBlank() && initialChatId != consumedInitialChatId) {
            consumedInitialChatId = initialChatId
            currentTab = TopDestination.CHATS
            val lockedSet = chatLockConfig.lockedChatIds.toSet()
            if (lockedSet.contains(initialChatId) && !unlockedChatIds.contains(initialChatId)) {
                val room = ((chatsState as? UiState.Success)?.data.orEmpty())
                    .firstOrNull { it.id == initialChatId }
                    ?: ChatRoom(id = initialChatId, title = "Locked Chat")
                pendingChatToUnlock = room
            } else {
                viewModel.openChat(initialChatId)
            }
        }
    }

    LaunchedEffect(initialCallId, initialCallAction) {
        if (!initialCallId.isNullOrBlank() && initialCallAction != null) {
            if (initialCallAction == CallActionType.ACCEPT) {
                runWithCallPermissions(isVideo = true) {
                    viewModel.handleCallNotificationAction(initialCallId, CallActionType.ACCEPT)
                }
            } else {
                viewModel.handleCallNotificationAction(initialCallId, initialCallAction)
            }
        }
    }

    LaunchedEffect(Unit) {
        CallEventBus.actions.collect { event ->
            if (event.action == CallActionType.ACCEPT) {
                val isVideo = activeCall?.session?.isVideo ?: true
                runWithCallPermissions(isVideo = isVideo) {
                    viewModel.handleCallNotificationAction(event.callId, CallActionType.ACCEPT)
                }
            } else {
                viewModel.handleCallNotificationAction(event.callId, event.action)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.archiveUndoEvents.collect { prompt ->
            val result = snackbarHostState.showSnackbar(
                message = prompt.message,
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreArchiveState(prompt.chatIds, prompt.restoreArchivedState)
            }
        }
    }

    val darkTheme = privateUser?.darkModeEnabled ?: true
    val chatThemesMap = privateUser?.chatThemes.orEmpty()
    val allChats = (chatsState as? UiState.Success)?.data.orEmpty()
    val usersMap = remember(usersState, peerProfilesMap) {
        val discoverableMap = (usersState as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()
        peerProfilesMap + discoverableMap
    }

    val lockedChatIdsSet = remember(chatLockConfig.lockedChatIds) {
        chatLockConfig.lockedChatIds.toSet()
    }

    fun openChatRespectingLock(room: ChatRoom) {
        if (lockedChatIdsSet.contains(room.id) && !unlockedChatIds.contains(room.id)) {
            pendingChatToUnlock = room
        } else {
            showPrivacySecurityScreen = false
            viewModel.openChat(room.id)
        }
    }

    PulseChatTheme(darkTheme = darkTheme) {
        if (requiresTwoStepChallenge) {
            TwoStepChallengeOverlay(
                recoveryEmail = twoStepConfig?.recoveryEmail.orEmpty(),
                onVerifyPin = viewModel::verifyTwoStepPin,
                onRecoverWithCode = viewModel::recoverTwoStepWithCode,
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onBeforeSignOut = viewModel::goOfflineNow,
                        onSignOutComplete = {}
                    )
                }
            )
            return@PulseChatTheme
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                val currentCall = activeCall
                if (currentCall != null && currentCall.isMinimized) {
                    MinimizedCallBanner(
                        callState = currentCall,
                        currentUserId = currentUserId,
                        onExpand = { viewModel.setCallMinimized(false) },
                        onEndCall = { viewModel.endActiveCall() }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    val openChatId = selectedChatId
                    if (openChatId != null) {
                        val activeChatTheme = remember(openChatId, chatThemesMap) {
                            ChatThemePreference.fromWireString(chatThemesMap[openChatId])
                        }
                        val peerUid = activeChat?.otherMemberUid(currentUserId)
                        val peerHasKeyBundle = peerUid != null && keyBundles.containsKey(peerUid)
                        val safetyNumber = remember(peerUid, keyBundles, localE2eeMaterial) {
                            if (peerUid != null) viewModel.computeSafetyNumberForPeer(peerUid) else ""
                        }
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) }
                        ) { _ ->
                            ChatDetailScreen(
                                chatId = openChatId,
                                currentUserId = currentUserId,
                                chat = activeChat,
                                allChats = allChats,
                                messagesState = activeMessages,
                                usersMap = usersMap,
                                replyingTo = replyingTo,
                                forwardingMessage = forwardingMessage,
                                isDarkTheme = darkTheme,
                                chatTheme = activeChatTheme,
                                blockedUserIds = blockedUserIds,
                                blockedEitherWayUserIds = blockedEitherWayUserIds,
                                isChatLocked = lockedChatIdsSet.contains(openChatId),
                                e2eeEnabled = privacySettings.e2eeEnabled,
                                peerHasKeyBundle = peerHasKeyBundle,
                                safetyNumber = safetyNumber,
                                myKeyFingerprint = localE2eeMaterial?.keyFingerprint.orEmpty(),
                                myKeyVersion = localE2eeMaterial?.keyVersion ?: 1,
                                onBack = { viewModel.closeChat() },
                                onSendText = viewModel::sendTextMessage,
                                onSendMedia = viewModel::sendMediaMessage,
                                onSendBitmap = viewModel::sendBitmapToChat,
                                onComposerTextChanged = viewModel::onComposerTextChanged,
                                onStopTyping = { chatId -> viewModel.stopTyping(chatId) },
                                onMarkMessagesDelivered = { id, msgs, room ->
                                    viewModel.markVisibleMessagesDelivered(id, msgs, room)
                                },
                                onMarkMessagesRead = { id, msgs, room ->
                                    viewModel.markVisibleMessagesRead(id, msgs, room)
                                },
                                onSetReply = viewModel::setReplyingTo,
                                onSetForward = viewModel::setForwardingMessage,
                                onConfirmForward = viewModel::forwardMessageTo,
                                onToggleReaction = viewModel::toggleReaction,
                                onDeleteMessage = viewModel::deleteMessage,
                                onTogglePin = viewModel::togglePinChat,
                                onToggleArchive = viewModel::toggleArchiveChat,
                                onToggleLockChat = { room -> viewModel.toggleChatLocked(room.id) },
                                onSetDisappearingTimer = viewModel::setChatDisappearingTimer,
                                onBlockUser = { target -> viewModel.blockUser(target) },
                                onUnblockUser = viewModel::unblockUser,
                                onSubmitReport = viewModel::submitReport,
                                onRotateE2eeKeys = viewModel::rotateE2eeKeys,
                                onDeleteChat = { room -> viewModel.deleteChat(room.id) },
                                onUpdateChatTheme = { theme -> viewModel.updateChatTheme(openChatId, theme) },
                                onStartVoiceCall = { room, peer ->
                                    runWithCallPermissions(isVideo = false) {
                                        viewModel.startDirectCall(peer, CallType.VOICE, existingChatRoom = room)
                                    }
                                },
                                onStartVideoCall = { room, peer ->
                                    runWithCallPermissions(isVideo = true) {
                                        viewModel.startDirectCall(peer, CallType.VIDEO, existingChatRoom = room)
                                    }
                                },
                                onOpenAiStudioWithBitmap = { bmp, prompt ->
                                    viewModel.setAiSourceBitmap(bmp, prompt)
                                    viewModel.closeChat()
                                    currentTab = TopDestination.AI_STUDIO
                                },
                                onNotify = viewModel::notifyMessage
                            )
                        }
                    } else if (showPrivacySecurityScreen) {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) }
                        ) { _ ->
                            PrivacySecurityScreen(
                                currentUserId = currentUserId,
                                privacySettings = privacySettings,
                                twoStepConfig = twoStepConfig,
                                chatLockConfig = chatLockConfig,
                                spamGuardState = spamGuardState,
                                blockedUsers = blockedUsers,
                                blockHistory = blockHistory,
                                localE2eeMaterial = localE2eeMaterial,
                                allChats = allChats,
                                myReports = myReports,
                                isAdmin = isAdmin,
                                adminReports = adminReports,
                                lockedVaultUnlocked = lockedVaultUnlocked,
                                hasAnySecurityPin = viewModel.hasAnySecurityPinConfigured(),
                                initialSection = privacyInitialSection,
                                onSensitiveScreenChanged = viewModel::setSensitiveScreenActive,
                                onUpdatePrivacySettings = viewModel::updatePrivacySettings,
                                onEnableTwoStep = viewModel::enableTwoStepVerification,
                                onDisableTwoStep = viewModel::disableTwoStepVerification,
                                onSetChatLockPin = viewModel::setChatLockPin,
                                onSetChatLockBiometric = viewModel::setChatLockBiometricEnabled,
                                onVerifyChatLockPin = viewModel::verifyChatLockPin,
                                onSetLockedVaultUnlocked = viewModel::setLockedVaultUnlocked,
                                onToggleChatLocked = viewModel::toggleChatLocked,
                                onOpenLockedChat = { room ->
                                    viewModel.markChatUnlockedForSession(room.id)
                                    showPrivacySecurityScreen = false
                                    privacyInitialSection = null
                                    viewModel.openChat(room.id)
                                },
                                onUnblockUser = viewModel::unblockUser,
                                onRotateE2eeKeys = viewModel::rotateE2eeKeys,
                                onSubmitProblemReport = { category, description, onDone ->
                                    viewModel.submitReport(
                                        reportedUserId = "",
                                        reportedUserName = "PulseChat Support",
                                        category = category,
                                        description = description,
                                        onSubmitted = onDone
                                    )
                                },
                                onAdminReviewReport = viewModel::reviewReport,
                                onBack = {
                                    showPrivacySecurityScreen = false
                                    privacyInitialSection = null
                                }
                            )
                        }
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val useNavRail = maxWidth >= 600.dp
                            Scaffold(
                                snackbarHost = { SnackbarHost(snackbarHostState) },
                                bottomBar = {
                                    if (!useNavRail) {
                                        NavigationBar(
                                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                                        ) {
                                            TopDestination.entries.forEach { dest ->
                                                val selected = currentTab == dest
                                                NavigationBarItem(
                                                    selected = selected,
                                                    onClick = { currentTab = dest },
                                                    icon = {
                                                        Icon(
                                                            imageVector = iconForDestination(dest, selected),
                                                            contentDescription = dest.label
                                                        )
                                                    },
                                                    label = { Text(dest.label) },
                                                    modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                                                )
                                            }
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    if (useNavRail) {
                                        NavigationRail {
                                            TopDestination.entries.forEach { dest ->
                                                val selected = currentTab == dest
                                                NavigationRailItem(
                                                    selected = selected,
                                                    onClick = { currentTab = dest },
                                                    icon = {
                                                        Icon(
                                                            imageVector = iconForDestination(dest, selected),
                                                            contentDescription = dest.label
                                                        )
                                                    },
                                                    label = { Text(dest.label) },
                                                    modifier = Modifier.testTag("rail_${dest.name.lowercase()}")
                                                )
                                            }
                                        }
                                    }

                                    when (currentTab) {
                                        TopDestination.CHATS -> {
                                            ChatsListScreen(
                                                currentUserId = currentUserId,
                                                chatsState = chatsState,
                                                usersState = usersState,
                                                searchQuery = searchQuery,
                                                showArchived = showArchived,
                                                isDarkTheme = darkTheme,
                                                chatThemes = chatThemesMap,
                                                lockedChatIds = lockedChatIdsSet,
                                                blockedUserIds = blockedUserIds,
                                                extraUsersMap = peerProfilesMap,
                                                blockedEitherWayUserIds = blockedEitherWayUserIds,
                                                onSearchChange = viewModel::updateSearchQuery,
                                                onToggleArchived = viewModel::setShowArchived,
                                                onOpenChat = ::openChatRespectingLock,
                                                onStartDirectChat = { contact ->
                                                    viewModel.startDirectChat(contact) { }
                                                },
                                                onTogglePin = viewModel::togglePinChat,
                                                onToggleArchive = viewModel::toggleArchiveChat,
                                                onBatchArchiveChats = viewModel::archiveChats,
                                                onBatchUnarchiveChats = viewModel::unarchiveChats,
                                                onToggleLockChat = { room -> viewModel.toggleChatLocked(room.id) },
                                                onOpenLockedChatsVault = {
                                                    privacyInitialSection = "locked_chats"
                                                    showPrivacySecurityScreen = true
                                                },
                                                onDeleteChat = { room -> viewModel.deleteChat(room.id) },
                                                onUpdateChatTheme = { room, theme ->
                                                    viewModel.updateChatTheme(room.id, theme)
                                                },
                                                onNewChatClick = { showNewChatDialog = true },
                                                onOpenAiStudioClick = { currentTab = TopDestination.AI_STUDIO }
                                            )
                                        }
                                        TopDestination.CALLS -> {
                                            CallsHistoryScreen(
                                                currentUserId = currentUserId,
                                                callHistoryState = callHistoryState,
                                                usersState = usersState,
                                                allChats = allChats,
                                                blockedUserIds = blockedUserIds,
                                                extraUsersMap = peerProfilesMap,
                                                blockedEitherWayUserIds = blockedEitherWayUserIds,
                                                onStartCall = { peer, callType ->
                                                    runWithCallPermissions(isVideo = callType == CallType.VIDEO) {
                                                        viewModel.startDirectCall(peer, callType)
                                                    }
                                                },
                                                onDeleteEntry = viewModel::deleteCallHistoryEntry
                                            )
                                        }
                                        TopDestination.AI_STUDIO -> {
                                            AiStudioScreen(
                                                currentUserId = currentUserId,
                                                aiUiState = aiUiState,
                                                sourceBitmap = aiSourceBitmap,
                                                suggestedPrompt = aiSuggestedPrompt,
                                                chats = allChats.filterNot { lockedChatIdsSet.contains(it.id) },
                                                onSourceBitmapChanged = { bmp -> viewModel.setAiSourceBitmap(bmp) },
                                                onGenerate = viewModel::generateAiImage,
                                                onEdit = viewModel::editAiImage,
                                                onSendToChat = { room, bmp, caption ->
                                                    viewModel.sendBitmapToChat(room, bmp, caption, isAiImage = true)
                                                    viewModel.openChat(room.id)
                                                    currentTab = TopDestination.CHATS
                                                },
                                                onNotify = viewModel::notifyMessage
                                            )
                                        }
                                        TopDestination.PROFILE -> {
                                            ProfileScreen(
                                                privateUser = privateUser,
                                                twoStepEnabled = twoStepConfig?.enabled == true,
                                                lockedChatsCount = lockedChatIdsSet.size,
                                                blockedUsersCount = blockedUsers.size,
                                                onOpenPrivacySecurity = {
                                                    privacyInitialSection = null
                                                    showPrivacySecurityScreen = true
                                                },
                                                onSaveProfile = viewModel::updateProfile,
                                                onToggleDarkMode = viewModel::toggleDarkMode,
                                                onToggleNotifications = viewModel::toggleNotifications,
                                                onChangePassword = viewModel::changeAccountPassword,
                                                onLinkUsername = viewModel::linkUsernameCredentials,
                                                onBeforeSignOut = viewModel::goOfflineNow,
                                                onSignedOut = {},
                                                onNotify = viewModel::notifyMessage
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Full-screen Active / Incoming Call Overlay
            val currentCall = activeCall
            if (currentCall != null && !currentCall.isMinimized) {
                CallScreen(
                    callState = currentCall,
                    currentUserId = currentUserId,
                    eglBaseContext = viewModel.callEglBaseContext,
                    onAccept = {
                        runWithCallPermissions(isVideo = currentCall.session.isVideo) {
                            viewModel.acceptActiveCall()
                        }
                    },
                    onReject = { viewModel.rejectActiveCall() },
                    onEndCall = { viewModel.endActiveCall() },
                    onReconnect = { viewModel.reconnectActiveCall() },
                    onToggleMute = { viewModel.toggleCallMute() },
                    onToggleSpeaker = { viewModel.toggleCallSpeaker() },
                    onToggleCamera = { viewModel.toggleCallCamera() },
                    onSwitchCamera = { viewModel.switchCallCamera() },
                    onToggleFullScreen = { viewModel.toggleCallFullScreen() },
                    onMinimize = { viewModel.setCallMinimized(true) },
                    onAttachLocalRenderer = { viewModel.attachLocalVideoRenderer(it) },
                    onDetachLocalRenderer = { viewModel.detachLocalVideoRenderer(it) },
                    onAttachRemoteRenderer = { viewModel.attachRemoteVideoRenderer(it) },
                    onDetachRemoteRenderer = { viewModel.detachRemoteVideoRenderer(it) }
                )
            }
        }
    }

    val targetUnlockChat = pendingChatToUnlock
    if (targetUnlockChat != null) {
        UnlockSecurityDialog(
            title = "Unlock ${targetUnlockChat.displayTitleFor(currentUserId)}",
            subtitle = "Authenticate with biometrics or your security PIN to open this locked conversation.",
            biometricEnabled = chatLockConfig.biometricEnabled,
            hasPinConfigured = viewModel.hasAnySecurityPinConfigured(),
            onVerifyPin = viewModel::verifyChatLockPin,
            onCreatePin = viewModel::setChatLockPin,
            onUnlocked = {
                viewModel.markChatUnlockedForSession(targetUnlockChat.id)
                pendingChatToUnlock = null
                showPrivacySecurityScreen = false
                viewModel.openChat(targetUnlockChat.id)
            },
            onDismiss = { pendingChatToUnlock = null }
        )
    }

    if (showNewChatDialog) {
        NewChatDialog(
            currentUserId = currentUserId,
            usersState = usersState,
            allChats = allChats,
            blockedUserIds = blockedUserIds,
            blockedEitherWayUserIds = blockedEitherWayUserIds,
            onStartDirectChat = { other ->
                showNewChatDialog = false
                viewModel.startDirectChat(other) { }
            },
            onCreateGroupChat = { title, members ->
                showNewChatDialog = false
                viewModel.startGroupChat(title, members) { }
            },
            onDismiss = { showNewChatDialog = false }
        )
    }
}

@Composable
private fun MinimizedCallBanner(
    callState: ActiveCallUiState,
    currentUserId: String,
    onExpand: () -> Unit,
    onEndCall: () -> Unit
) {
    val session = callState.session
    val peerName = session.peerNameFor(currentUserId)
    val label = if (session.callStatus == CallStatus.CONNECTED) {
        formatCallDuration(callState.elapsedSeconds)
    } else {
        session.callStatus.displayLabel
    }

    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .testTag("minimized_call_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (session.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to return to call • $label",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onEndCall,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("minimized_call_end_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CallEnd,
                    contentDescription = "End call",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun iconForDestination(dest: TopDestination, selected: Boolean) = when (dest) {
    TopDestination.CHATS -> if (selected) Icons.Rounded.Forum else Icons.Outlined.Forum
    TopDestination.CALLS -> if (selected) Icons.Rounded.Call else Icons.Outlined.Call
    TopDestination.AI_STUDIO -> if (selected) Icons.Rounded.AutoAwesome else Icons.Outlined.AutoAwesome
    TopDestination.PROFILE -> if (selected) Icons.Rounded.Person else Icons.Outlined.Person
}
