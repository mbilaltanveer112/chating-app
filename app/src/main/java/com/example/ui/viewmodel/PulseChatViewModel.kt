package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.call.CallActionType
import com.example.call.CallCoordinator
import com.example.data.auth.AuthSessionStore
import com.example.data.auth.MongoAuthRepository
import com.example.data.auth.PulseAuthService
import com.example.data.model.ActiveCallUiState
import com.example.data.model.BlockHistoryEvent
import com.example.data.model.BlockedUserRecord
import com.example.data.model.CallSession
import com.example.data.model.CallType
import com.example.data.model.ChatLockConfig
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.MessageType
import com.example.data.model.PrivacySettings
import com.example.data.model.PublicProfile
import com.example.data.model.ReportCategory
import com.example.data.model.ReportStatus
import com.example.data.model.SpamGuardState
import com.example.data.model.TwoStepSecurityConfig
import com.example.data.model.UserKeyBundle
import com.example.data.model.UserPrivate
import com.example.data.model.UserReport
import com.example.data.repository.AiAspectRatio
import com.example.data.repository.AiImageRepository
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.FirebaseAiImageRepository
import com.example.data.repository.SecurityRepository
import com.example.security.DeviceSecurityStore
import com.example.security.E2eeCryptoManager
import com.example.security.LocalE2eeKeyMaterial
import com.example.security.MessageSecurityBridge
import com.example.security.OutgoingSpamVerdict
import com.example.security.PinCryptoUtils
import com.example.security.PrivacyVisibilityResolver
import com.example.security.ScreenshotEventBus
import com.example.security.SpamProtectionEngine
import com.example.ui.theme.ChatThemePreference
import com.example.util.MediaUtils
import com.example.util.NetworkConnectivityMonitor
import com.example.util.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiStudioUiState {
    data object Idle : AiStudioUiState
    data class Loading(val isEditing: Boolean) : AiStudioUiState
    data class Success(
        val bitmap: Bitmap,
        val prompt: String,
        val isEdited: Boolean
    ) : AiStudioUiState
    data class Error(val message: String) : AiStudioUiState
}

data class ArchiveUndoPrompt(
    val message: String,
    val chatIds: List<String>,
    val restoreArchivedState: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class PulseChatViewModel(
    private val chatRepository: ChatRepository,
    private val callRepository: CallRepository,
    private val securityRepository: SecurityRepository,
    private val aiImageRepository: AiImageRepository,
    private val deviceSecurityStore: DeviceSecurityStore,
    private val currentUserId: String,
    private val appContext: Context,
    private val authService: PulseAuthService = MongoAuthRepository(appContext)
) : ViewModel() {

    private val sessionStartedAt = Timestamp.now()
    private val notifiedMessageTimestamps = mutableMapOf<String, Long>()
    private val inFlightReadMessageIds = ConcurrentHashMap.newKeySet<String>()
    private val inFlightDeliveredMessageIds = ConcurrentHashMap.newKeySet<String>()
    private val inFlightDeliveredChatKeys = ConcurrentHashMap.newKeySet<String>()
    private val purgedExpiredIds = ConcurrentHashMap.newKeySet<String>()
    private var inFlightReadChatId: String? = null
    private var typingIdleJob: Job? = null
    private var activeTypingChatId: String? = null
    private var lastTypingPingAtMs: Long = 0L
    private val typingWriteMutex = Mutex()
    private val networkMonitor = NetworkConnectivityMonitor(appContext)
    private val _isAppForeground = MutableStateFlow(false)
    private val presenceMutex = Mutex()
    private var presenceHeartbeatJob: Job? = null
    private var lastOnlinePingAtMs: Long = 0L

    private val spamProtectionEngine = SpamProtectionEngine()
    private val messageSecurityBridge = MessageSecurityBridge()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo: StateFlow<ChatMessage?> = _replyingTo.asStateFlow()

    private val _forwardingMessage = MutableStateFlow<ChatMessage?>(null)
    val forwardingMessage: StateFlow<ChatMessage?> = _forwardingMessage.asStateFlow()

    private val _aiSourceBitmap = MutableStateFlow<Bitmap?>(null)
    val aiSourceBitmap: StateFlow<Bitmap?> = _aiSourceBitmap.asStateFlow()

    private val _aiSuggestedPrompt = MutableStateFlow("")
    val aiSuggestedPrompt: StateFlow<String> = _aiSuggestedPrompt.asStateFlow()

    private val _aiUiState = MutableStateFlow<AiStudioUiState>(AiStudioUiState.Idle)
    val aiUiState: StateFlow<AiStudioUiState> = _aiUiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _archiveUndoEvents = MutableSharedFlow<ArchiveUndoPrompt>(extraBufferCapacity = 8)
    val archiveUndoEvents = _archiveUndoEvents.asSharedFlow()

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    val isOnlineConnected: StateFlow<Boolean> = combine(
        _isAppForeground,
        networkMonitor.isConnected
    ) { foreground, connected -> foreground && connected }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false
        )

    private val e2eeKeyInitMutex = Mutex()
    private val _localE2eeMaterial = MutableStateFlow<LocalE2eeKeyMaterial?>(null)
    val localE2eeMaterial: StateFlow<LocalE2eeKeyMaterial?> = _localE2eeMaterial.asStateFlow()
    private val _e2eeMaterialsByFp = MutableStateFlow<Map<String, LocalE2eeKeyMaterial>>(emptyMap())

    private val _sessionTwoStepVerified = MutableStateFlow(false)
    private val _lockedVaultUnlocked = MutableStateFlow(false)
    val lockedVaultUnlocked: StateFlow<Boolean> = _lockedVaultUnlocked.asStateFlow()

    private val _unlockedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedChatIds: StateFlow<Set<String>> = _unlockedChatIds.asStateFlow()

    private val _sensitiveScreenActive = MutableStateFlow(false)

    private lateinit var callCoordinator: CallCoordinator

    val privateUser: StateFlow<UserPrivate?> = chatRepository.observePrivateUser(currentUserId)
        .catch { error ->
            val fsErr = unwrapFirestoreException(error)
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing private profile", fsErr ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    private val _privacySettingsLoaded = MutableStateFlow(false)

    val privacySettings: StateFlow<PrivacySettings> = securityRepository.observePrivacySettings(currentUserId)
        .onEach {
            val firstLoad = !_privacySettingsLoaded.value
            _privacySettingsLoaded.value = true
            if (firstLoad && _isAppForeground.value && networkMonitor.isConnected.value) {
                reconcilePresenceAndHeartbeat()
            }
        }
        .catch { error ->
            if (error is CancellationException) throw error
            _privacySettingsLoaded.value = true
            emit(PrivacySettings())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PrivacySettings()
        )

    val twoStepConfig: StateFlow<TwoStepSecurityConfig?> = securityRepository.observeTwoStepConfig(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val chatLockConfig: StateFlow<ChatLockConfig> = securityRepository.observeChatLockConfig(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(ChatLockConfig())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ChatLockConfig()
        )

    val spamGuardState: StateFlow<SpamGuardState> = securityRepository.observeSpamGuardState(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(SpamGuardState())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SpamGuardState()
        )

    val blockedUsers: StateFlow<List<BlockedUserRecord>> = securityRepository.observeBlockedUsers(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val blockedUserIds: StateFlow<Set<String>> = blockedUsers
        .map { list -> list.map { it.blockedUid }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val blockedByUserIds: StateFlow<Set<String>> = securityRepository.observeBlockedByUsers(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptySet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val blockedEitherWayUserIds: StateFlow<Set<String>> = combine(
        blockedUserIds,
        blockedByUserIds
    ) { blockedByMe, blockedMe ->
        blockedByMe + blockedMe
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptySet()
    )

    val blockHistory: StateFlow<List<BlockHistoryEvent>> = securityRepository.observeBlockHistory(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val keyBundles: StateFlow<Map<String, UserKeyBundle>> = securityRepository.observeKeyBundles()
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    val myReports: StateFlow<List<UserReport>> = securityRepository.observeMyReports(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val isAdmin: StateFlow<Boolean> = securityRepository.observeIsAdmin(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false
        )

    val adminReports: StateFlow<List<UserReport>> = isAdmin
        .flatMapLatest { admin ->
            if (!admin) flowOf(emptyList())
            else securityRepository.observeAllReportsForAdmin()
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val requiresTwoStepChallenge: StateFlow<Boolean> = combine(
        twoStepConfig,
        _deviceId,
        _sessionTwoStepVerified
    ) { cfg, devId, verified ->
        if (cfg == null || !cfg.enabled || verified) {
            false
        } else {
            devId.isBlank() || !cfg.registeredDeviceIds.contains(devId)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = false
    )

    val shouldBlockScreenshots: StateFlow<Boolean> = combine(
        privacySettings,
        _sensitiveScreenActive,
        requiresTwoStepChallenge
    ) { settings, sensitive, twoStepGate ->
        settings.screenshotModeEnum.wireValue == "block" || sensitive || twoStepGate
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = false
    )

    val discoverableUsers: StateFlow<UiState<List<PublicProfile>>> = chatRepository.observeDiscoverableUsers()
        .map<List<PublicProfile>, UiState<List<PublicProfile>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = unwrapFirestoreException(error)
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing users", root)
            emit(UiState.Error(root.message ?: "Failed to load directory"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val chats: StateFlow<UiState<List<ChatRoom>>> = combine(
        chatRepository.observeChats(currentUserId),
        chatLockConfig,
        blockedUserIds
    ) { rooms, lockConfig, blockedIds ->
        maybeNotifyIncomingMessages(rooms, lockConfig.lockedChatIds.toSet(), blockedIds)
        acknowledgeIncomingDeliveries(rooms, blockedIds)
        rooms
    }
        .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = unwrapFirestoreException(error)
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing chats", root)
            emit(UiState.Error(root.message ?: "Failed to observe chats"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val activeChat: StateFlow<ChatRoom?> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) flowOf(null)
            else chatRepository.observeChat(chatId)
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing chat $chatId", fsErr ?: error)
                    emit(null)
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    private val nonDiscoverablePeerProfiles: StateFlow<Map<String, PublicProfile>> = combine(
        discoverableUsers,
        chats,
        activeChat
    ) { usersState, chatsState, openRoom ->
        val discoverableIds = (usersState as? UiState.Success)?.data
            ?.map { it.userId }
            ?.toSet()
            .orEmpty()
        val activePeerIds = openRoom?.memberUids.orEmpty()
            .filter { it != currentUserId && it.isNotBlank() && !discoverableIds.contains(it) }
        val chatPeerIds = (chatsState as? UiState.Success)?.data.orEmpty()
            .flatMap { it.memberUids }
            .filter { it != currentUserId && it.isNotBlank() && !discoverableIds.contains(it) }
        (activePeerIds + chatPeerIds).distinct().take(30)
    }
        .distinctUntilChanged()
        .flatMapLatest { missingIds ->
            if (missingIds.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(missingIds.map { uid ->
                    chatRepository.observePublicProfile(uid).map { uid to it }
                }) { pairs ->
                    pairs.mapNotNull { (uid, profile) ->
                        profile?.let { uid to it }
                    }.toMap()
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    val peerProfilesMap: StateFlow<Map<String, PublicProfile>> = combine(
        discoverableUsers,
        nonDiscoverablePeerProfiles
    ) { usersState, extraProfiles ->
        val discoverableMap = (usersState as? UiState.Success)?.data
            ?.associateBy { it.userId }
            .orEmpty()
        extraProfiles + discoverableMap
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyMap()
    )

    val activeMessages: StateFlow<UiState<List<ChatMessage>>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                combine(
                    chatRepository.observeMessages(chatId, currentUserId),
                    _localE2eeMaterial,
                    _e2eeMaterialsByFp,
                    keyBundles
                ) { rawMessages, localMaterial, materialsByFp, bundles ->
                    val now = System.currentTimeMillis()
                    val expiredIds = rawMessages
                        .filter { it.id.isNotBlank() && it.isExpired(now) }
                        .map { it.id }
                        .filter { purgedExpiredIds.add(it) }
                    if (expiredIds.isNotEmpty()) {
                        viewModelScope.launch {
                            chatRepository.purgeExpiredMessages(chatId, expiredIds)
                        }
                    }

                    val liveMessages = rawMessages.filterNot { it.isExpired(now) }
                    messageSecurityBridge.decryptMessagesForDisplay(
                        messages = liveMessages,
                        currentUid = currentUserId,
                        localMaterial = localMaterial,
                        keyBundles = bundles,
                        materialsByFingerprint = materialsByFp
                    )
                }
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = unwrapFirestoreException(error)
                        if (fsErr == null && error is CancellationException) throw error
                        val root = fsErr ?: error
                        Log.w(TAG, "Error observing messages for $chatId", root)
                        emit(UiState.Error(root.message ?: "Failed to load messages"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val activeCall: StateFlow<ActiveCallUiState?>
        get() = callCoordinator.activeCall

    val callHistory: StateFlow<UiState<List<CallSession>>> by lazy {
        combine(
            callCoordinator.callHistory,
            callCoordinator.callHistoryError,
            blockedUserIds
        ) { history, error, blockedIds ->
            val filtered = history.filterNot { call ->
                val peerUid = call.peerUidFor(currentUserId)
                blockedIds.contains(peerUid)
            }
            if (error != null && filtered.isEmpty()) UiState.Error(error)
            else UiState.Success(filtered)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )
    }

    val callEglBaseContext: EglBase.Context?
        get() = callCoordinator.eglBaseContext

    init {
        callCoordinator = CallCoordinator(
            appContext = appContext,
            currentUserId = currentUserId,
            callRepository = callRepository,
            chatRepository = chatRepository,
            scope = viewModelScope,
            currentProfileProvider = ::currentPublicProfile,
            isNotificationsEnabledProvider = { privateUser.value?.notificationsEnabled ?: true },
            openChatIdProvider = { _selectedChatId.value },
            blockedUserIdsProvider = { blockedUserIds.value },
            onSnackbarMessage = { msg -> _snackbarEvents.tryEmit(msg) }
        )

        viewModelScope.launch {
            ensureLocalE2eeKeysReady()
        }

        viewModelScope.launch {
            ScreenshotEventBus.events.collect { event ->
                handleLocalScreenshotCaptured(event.timestampMs)
            }
        }

        viewModelScope.launch {
            val devId = deviceSecurityStore.getOrCreateDeviceId()
            _deviceId.value = devId
            val user = Firebase.auth.currentUser
            if (user != null) {
                chatRepository.ensureUserProfile(
                    displayName = user.displayName
                        ?: user.email?.substringBefore("@")
                        ?: user.phoneNumber?.takeIf { it.isNotBlank() }
                        ?: "Pulse User",
                    email = user.email.orEmpty(),
                    photoUrl = user.photoUrl?.toString().orEmpty(),
                    phoneNumber = user.phoneNumber.orEmpty(),
                    deviceId = devId
                ).onSuccess {
                    lastOnlinePingAtMs = System.currentTimeMillis()
                }
            }
            reconcilePresenceAndHeartbeat()
        }

        viewModelScope.launch {
            var previousConnected: Boolean? = null
            networkMonitor.isConnected.collect { connected ->
                val changed = previousConnected != null && previousConnected != connected
                previousConnected = connected
                if (changed || (_isAppForeground.value && connected)) {
                    reconcilePresenceAndHeartbeat()
                }
            }
        }
    }

    private suspend fun ensureLocalE2eeKeysReady(): LocalE2eeKeyMaterial = e2eeKeyInitMutex.withLock {
        val current = _localE2eeMaterial.value
        if (current != null && _e2eeMaterialsByFp.value.isNotEmpty()) {
            return@withLock current
        }
        val loaded = deviceSecurityStore.loadE2eeMaterial(currentUserId)
        val remote = securityRepository.getKeyBundle(currentUserId)
        val material = loaded ?: E2eeCryptoManager.generateLocalKeyMaterial(
            userId = currentUserId,
            keyVersion = ((remote?.keyVersion ?: 0) + 1).coerceAtLeast(1)
        ).also {
            deviceSecurityStore.saveE2eeMaterial(it)
        }
        val allMaterials = deviceSecurityStore.loadAllE2eeMaterials(currentUserId).toMutableMap()
        allMaterials[material.keyFingerprint] = material
        _e2eeMaterialsByFp.value = allMaterials
        _localE2eeMaterial.value = material
        if (remote == null || remote.keyFingerprint != material.keyFingerprint || remote.keyVersion != material.keyVersion) {
            securityRepository.publishKeyBundle(material.toPublicKeyBundle())
        }
        material
    }

    private fun handleLocalScreenshotCaptured(timestampMs: Long) {
        val chat = activeChat.value ?: return
        if (!callCoordinator.isAppInForeground) return
        val formatted = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestampMs))
        viewModelScope.launch {
            chatRepository.sendScreenshotNotice(
                chatId = chat.id,
                memberUids = chat.memberUids,
                actorName = currentDisplayName(),
                formattedDateTime = formatted
            ).onSuccess {
                _snackbarEvents.tryEmit("Screenshot detected • Participants notified")
            }
        }
    }

    fun setSensitiveScreenActive(active: Boolean) {
        _sensitiveScreenActive.value = active
    }

    fun setPresence(isOnline: Boolean) {
        onForegroundChanged(isOnline)
    }

    fun onForegroundChanged(isForeground: Boolean) {
        val changed = _isAppForeground.value != isForeground
        _isAppForeground.value = isForeground
        callCoordinator.isAppInForeground = isForeground
        if (!isForeground) {
            stopTyping()
        }
        if (changed || isForeground) {
            reconcilePresenceAndHeartbeat()
        }
    }

    private fun reconcilePresenceAndHeartbeat() {
        val isForeground = _isAppForeground.value
        val isConnected = networkMonitor.isConnected.value
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null

        if (isForeground && isConnected) {
            presenceHeartbeatJob = viewModelScope.launch {
                val now = System.currentTimeMillis()
                if (now - lastOnlinePingAtMs >= 1_500L) {
                    sendPresencePing(isOnline = true)
                }
                while (isActive && _isAppForeground.value && networkMonitor.isConnected.value) {
                    delay(PrivacyVisibilityResolver.ONLINE_HEARTBEAT_INTERVAL_MS)
                    if (_isAppForeground.value && networkMonitor.isConnected.value) {
                        sendPresencePing(isOnline = true)
                    }
                }
            }
        } else if (!isForeground && isConnected) {
            viewModelScope.launch {
                sendPresencePing(isOnline = false)
            }
        } else {
            lastOnlinePingAtMs = 0L
        }
    }

    private suspend fun resolveDeviceId(): String {
        val existing = _deviceId.value
        if (existing.isNotBlank()) return existing
        return deviceSecurityStore.getOrCreateDeviceId().also { _deviceId.value = it }
    }

    private suspend fun sendPresencePing(isOnline: Boolean) {
        presenceMutex.withLock {
            val devId = resolveDeviceId()
            val settingsLoaded = _privacySettingsLoaded.value
            val settings = privacySettings.value
            chatRepository.updatePresence(
                isOnline = isOnline,
                onlineVisibility = if (settingsLoaded) settings.onlineVisibility else null,
                lastSeenVisibility = if (settingsLoaded) settings.lastSeenVisibility else null,
                deviceId = devId
            ).onSuccess {
                lastOnlinePingAtMs = if (isOnline) System.currentTimeMillis() else 0L
            }
        }
    }

    suspend fun goOfflineNow() {
        withContext(NonCancellable) {
            _isAppForeground.value = false
            callCoordinator.isAppInForeground = false
            presenceHeartbeatJob?.cancel()
            presenceHeartbeatJob = null
            clearLocalTypingTracking()
            withTimeoutOrNull(2_500L) {
                sendPresencePing(isOnline = false)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setShowArchived(show: Boolean) {
        _showArchived.value = show
    }

    fun openChat(chatId: String) {
        if (inFlightReadChatId != chatId) {
            inFlightReadMessageIds.clear()
            inFlightDeliveredMessageIds.clear()
            inFlightReadChatId = chatId
        }
        _selectedChatId.value = chatId
        _replyingTo.value = null
        viewModelScope.launch {
            chatRepository.markChatAsRead(chatId)
        }
    }

    fun closeChat() {
        val current = _selectedChatId.value
        _selectedChatId.value = null
        _replyingTo.value = null
        inFlightReadChatId = null
        inFlightReadMessageIds.clear()
        inFlightDeliveredMessageIds.clear()
        if (!current.isNullOrBlank()) {
            stopTyping(current)
        }
    }

    fun markVisibleMessagesDelivered(
        chatId: String,
        messages: List<ChatMessage>,
        activeChatRoom: ChatRoom? = null
    ) {
        if (inFlightReadChatId != chatId) {
            inFlightReadMessageIds.clear()
            inFlightDeliveredMessageIds.clear()
            inFlightReadChatId = chatId
        }
        val undeliveredIds = messages
            .asSequence()
            .filter {
                it.id.isNotBlank() &&
                    it.senderId != currentUserId &&
                    !it.deliveredByUids.contains(currentUserId) &&
                    !it.readByUids.contains(currentUserId)
            }
            .map { it.id }
            .filter { inFlightDeliveredMessageIds.add(it) }
            .toList()
        val roomNeedsDelivered = activeChatRoom?.id == chatId &&
            activeChatRoom.hasUndeliveredLatestMessageFor(currentUserId)

        if (undeliveredIds.isEmpty() && !roomNeedsDelivered) return

        viewModelScope.launch {
            if (roomNeedsDelivered || undeliveredIds.isNotEmpty()) {
                chatRepository.markChatAsDelivered(chatId)
            }
            if (undeliveredIds.isNotEmpty()) {
                chatRepository.markMessagesDelivered(chatId, undeliveredIds)
                    .onFailure {
                        inFlightDeliveredMessageIds.removeAll(undeliveredIds.toSet())
                    }
            }
        }
    }

    fun markVisibleMessagesRead(
        chatId: String,
        messages: List<ChatMessage>,
        activeChatRoom: ChatRoom? = null
    ) {
        if (inFlightReadChatId != chatId) {
            inFlightReadMessageIds.clear()
            inFlightDeliveredMessageIds.clear()
            inFlightReadChatId = chatId
        }
        val unreadMessages = messages.filter {
            it.id.isNotBlank() &&
                it.senderId != currentUserId &&
                !it.readByUids.contains(currentUserId) &&
                !inFlightReadMessageIds.contains(it.id)
        }
        val unreadIds = unreadMessages.map { it.id }
        val alsoDeliveredIds = unreadMessages
            .filter { !it.deliveredTimestamps.containsKey(currentUserId) }
            .map { it.id }
            .toSet()
        val roomNeedsRead = activeChatRoom?.id == chatId && activeChatRoom.isUnreadFor(currentUserId)

        if (unreadIds.isEmpty() && !roomNeedsRead) return

        inFlightReadMessageIds.addAll(unreadIds)
        inFlightDeliveredMessageIds.addAll(unreadIds)
        viewModelScope.launch {
            if (roomNeedsRead || unreadIds.isNotEmpty()) {
                chatRepository.markChatAsRead(chatId)
            }
            if (unreadIds.isNotEmpty()) {
                chatRepository.markMessagesRead(
                    chatId = chatId,
                    messageIds = unreadIds,
                    alsoRecordDeliveredTimestampIds = alsoDeliveredIds
                ).onFailure {
                    inFlightReadMessageIds.removeAll(unreadIds.toSet())
                }
            }
        }
    }

    fun startDirectChat(otherUser: PublicProfile, onOpened: (String) -> Unit) {
        if (blockedUserIds.value.contains(otherUser.userId)) {
            _snackbarEvents.tryEmit("Unblock ${otherUser.displayName} in Privacy & Security to chat.")
            return
        }
        viewModelScope.launch {
            val me = currentPublicProfile()
            val defaultDisappear = privacySettings.value.defaultDisappearingSeconds
            chatRepository.createOrGetDirectChat(me, otherUser, defaultDisappear)
                .onSuccess { chatId ->
                    openChat(chatId)
                    onOpened(chatId)
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Could not open conversation")
                }
        }
    }

    fun startGroupChat(title: String, members: List<PublicProfile>, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val me = currentPublicProfile()
            chatRepository.createGroupChat(title, me, members)
                .onSuccess { chatId ->
                    openChat(chatId)
                    onOpened(chatId)
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Could not create group")
                }
        }
    }

    fun sendTextMessage(chat: ChatRoom, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (isDirectPeerBlocked(chat)) {
            _snackbarEvents.tryEmit("You blocked this user. Unblock to send messages.")
            return
        }

        val settings = privacySettings.value
        val guard = spamGuardState.value
        when (val verdict = spamProtectionEngine.evaluateOutgoingMessage(
            text = clean,
            chatId = chat.id,
            level = settings.spamProtectionLevelEnum,
            persistedRestrictedUntilMs = guard.restrictedUntilEpochMs,
            currentViolationCount = guard.violationCount
        )) {
            is OutgoingSpamVerdict.Restricted -> {
                _snackbarEvents.tryEmit(verdict.reason)
                viewModelScope.launch {
                    securityRepository.saveSpamGuardState(
                        SpamGuardState(
                            restrictedUntilEpochMs = verdict.untilEpochMs,
                            violationCount = verdict.violationCount,
                            lastViolationReason = verdict.reason
                        )
                    )
                }
                return
            }
            is OutgoingSpamVerdict.Warning -> {
                _snackbarEvents.tryEmit("Security notice: ${verdict.reason}")
            }
            OutgoingSpamVerdict.Allowed -> Unit
        }

        val reply = _replyingTo.value
        _replyingTo.value = null
        val hadActiveTyping = clearLocalTypingTracking() == chat.id

        viewModelScope.launch {
            val sent = dispatchEncryptedOrPlainMessage(
                chat = chat,
                type = MessageType.TEXT,
                plainText = clean,
                mediaData = null,
                mediaFileName = null,
                mediaMimeType = null,
                mediaSizeBytes = null,
                durationMs = null,
                waveform = null,
                replyTo = reply
            )
            if (!sent && hadActiveTyping) {
                typingWriteMutex.withLock {
                    chatRepository.setTyping(chat.id, false)
                }
            }
        }
    }

    fun sendMediaMessage(
        chat: ChatRoom,
        type: MessageType,
        caption: String,
        mediaData: String? = null,
        mediaFileName: String? = null,
        mediaMimeType: String? = null,
        mediaSizeBytes: Long? = null,
        durationMs: Long? = null,
        waveform: List<Int>? = null
    ) {
        if (isDirectPeerBlocked(chat)) {
            _snackbarEvents.tryEmit("You blocked this user. Unblock to share media.")
            return
        }
        val cleanCaption = caption.trim()
        val settings = privacySettings.value
        val guard = spamGuardState.value
        val verdict = spamProtectionEngine.evaluateOutgoingMessage(
            text = cleanCaption.ifBlank { type.wireValue },
            chatId = chat.id,
            level = settings.spamProtectionLevelEnum,
            persistedRestrictedUntilMs = guard.restrictedUntilEpochMs,
            currentViolationCount = guard.violationCount
        )
        if (verdict is OutgoingSpamVerdict.Restricted) {
            _snackbarEvents.tryEmit(verdict.reason)
            viewModelScope.launch {
                securityRepository.saveSpamGuardState(
                    SpamGuardState(
                        restrictedUntilEpochMs = verdict.untilEpochMs,
                        violationCount = verdict.violationCount,
                        lastViolationReason = verdict.reason
                    )
                )
            }
            return
        }

        val reply = _replyingTo.value
        _replyingTo.value = null
        val hadActiveTyping = clearLocalTypingTracking() == chat.id

        viewModelScope.launch {
            val sent = dispatchEncryptedOrPlainMessage(
                chat = chat,
                type = type,
                plainText = cleanCaption,
                mediaData = mediaData,
                mediaFileName = mediaFileName,
                mediaMimeType = mediaMimeType,
                mediaSizeBytes = mediaSizeBytes,
                durationMs = durationMs,
                waveform = waveform,
                replyTo = reply
            )
            if (!sent && hadActiveTyping) {
                typingWriteMutex.withLock {
                    chatRepository.setTyping(chat.id, false)
                }
            }
        }
    }

    private suspend fun dispatchEncryptedOrPlainMessage(
        chat: ChatRoom,
        type: MessageType,
        plainText: String,
        mediaData: String?,
        mediaFileName: String?,
        mediaMimeType: String?,
        mediaSizeBytes: Long?,
        durationMs: Long?,
        waveform: List<Int>?,
        replyTo: ChatMessage?
    ): Boolean {
        val messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val expiresAtEpochMs = if (chat.disappearingDurationSeconds > 0L) {
            System.currentTimeMillis() + (chat.disappearingDurationSeconds * 1000L)
        } else {
            0L
        }

        val useE2ee = privacySettings.value.e2eeEnabled
        val result = if (useE2ee) {
            val myMaterial = _localE2eeMaterial.value
                ?: runCatching { ensureLocalE2eeKeysReady() }.getOrNull()
            if (myMaterial == null) {
                _snackbarEvents.tryEmit("Initializing encryption keys. Please try again.")
                return false
            }
            val bundlesMap = keyBundles.value
            val resolvedBundles = chat.memberUids.mapNotNull { uid ->
                if (uid == currentUserId) myMaterial.toPublicKeyBundle()
                else bundlesMap[uid] ?: securityRepository.getKeyBundle(uid)
            }
            val allMembersVerified = chat.memberUids.all { uid ->
                resolvedBundles.any { it.userId == uid && E2eeCryptoManager.verifySignedPreKey(it) }
            }
            if (!allMembersVerified) {
                _snackbarEvents.tryEmit("Recipient encryption keys unavailable yet. Ask them to open PulseChat once to sync keys.")
                return false
            }

            val replyPreview = replyTo?.let { r ->
                when (r.messageType) {
                    MessageType.TEXT -> r.text.ifBlank { "Message" }
                    MessageType.IMAGE -> if (r.text.isNotBlank()) "Photo: ${r.text}" else "Photo"
                    MessageType.AI_IMAGE -> if (r.text.isNotBlank()) "AI Image: ${r.text}" else "AI Image"
                    MessageType.VIDEO -> if (r.text.isNotBlank()) "Video: ${r.text}" else "Video"
                    MessageType.VOICE -> "Voice note"
                    MessageType.DOCUMENT -> if (r.mediaFileName.isNotBlank()) "Document: ${r.mediaFileName}" else "Document"
                    MessageType.CALL -> r.text.ifBlank { "Call" }
                    MessageType.SYSTEM -> r.text.ifBlank { "Security notice" }
                }
            }.orEmpty()

            val envelope = E2eeCryptoManager.encryptForRecipients(
                chatId = chat.id,
                messageId = messageId,
                senderMaterial = myMaterial,
                plaintextText = plainText,
                mediaData = mediaData.orEmpty(),
                recipientBundles = resolvedBundles,
                replyToPreview = replyPreview
            )
            if (envelope.encryptedMediaData.length > 650_000) {
                _snackbarEvents.tryEmit("Encrypted media exceeds maximum allowed size.")
                return false
            }
            if (!envelope.wrappedKeys.keys.containsAll(chat.memberUids)) {
                _snackbarEvents.tryEmit("Could not establish encryption keys for all chat members.")
                return false
            }
            chatRepository.sendMessage(
                chatId = chat.id,
                memberUids = chat.memberUids,
                senderName = currentDisplayName(),
                type = type,
                text = "",
                mediaData = null,
                mediaFileName = mediaFileName,
                mediaMimeType = mediaMimeType,
                mediaSizeBytes = mediaSizeBytes,
                durationMs = durationMs,
                waveform = waveform,
                replyTo = replyTo,
                messageIdOverride = messageId,
                isEncrypted = true,
                encryptionVersion = envelope.encryptionVersion,
                encryptedPayload = envelope.encryptedPayload,
                encryptedMediaData = envelope.encryptedMediaData.takeIf { it.isNotBlank() },
                wrappedKeys = envelope.wrappedKeys,
                expiresAtEpochMs = expiresAtEpochMs,
                chatPreviewOverride = when (type) {
                    MessageType.IMAGE -> "Encrypted photo"
                    MessageType.AI_IMAGE -> "Encrypted AI image"
                    MessageType.VIDEO -> "Encrypted video"
                    MessageType.VOICE -> "Encrypted voice note"
                    MessageType.DOCUMENT -> "Encrypted file"
                    else -> "Encrypted message"
                }
            )
        } else {
            chatRepository.sendMessage(
                chatId = chat.id,
                memberUids = chat.memberUids,
                senderName = currentDisplayName(),
                type = type,
                text = plainText,
                mediaData = mediaData,
                mediaFileName = mediaFileName,
                mediaMimeType = mediaMimeType,
                mediaSizeBytes = mediaSizeBytes,
                durationMs = durationMs,
                waveform = waveform,
                replyTo = replyTo,
                messageIdOverride = messageId,
                expiresAtEpochMs = expiresAtEpochMs
            )
        }

        result.onFailure { e ->
            val fsErr = unwrapFirestoreException(e)
            val msg = if (fsErr?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Message could not be sent. Recipient may be unavailable or blocked."
            } else {
                e.localizedMessage ?: "Failed to send message"
            }
            _snackbarEvents.tryEmit(msg)
        }
        return result.isSuccess
    }

    fun sendBitmapToChat(chat: ChatRoom, bitmap: Bitmap, caption: String, isAiImage: Boolean) {
        val encoded = MediaUtils.encodeBitmapToBase64(bitmap)
        sendMediaMessage(
            chat = chat,
            type = if (isAiImage) MessageType.AI_IMAGE else MessageType.IMAGE,
            caption = caption,
            mediaData = encoded,
            mediaMimeType = "image/jpeg"
        )
    }

    // --- Privacy & Security Actions ---

    fun updatePrivacySettings(transform: (PrivacySettings) -> PrivacySettings) {
        val previous = privacySettings.value
        val next = transform(previous)
        viewModelScope.launch {
            securityRepository.savePrivacySettings(next)
                .onSuccess {
                    if (next.onlineVisibility != previous.onlineVisibility ||
                        next.lastSeenVisibility != previous.lastSeenVisibility
                    ) {
                        chatRepository.updatePresence(
                            isOnline = _isAppForeground.value && networkMonitor.isConnected.value,
                            onlineVisibility = next.onlineVisibility,
                            lastSeenVisibility = next.lastSeenVisibility,
                            deviceId = resolveDeviceId()
                        )
                        reconcilePresenceAndHeartbeat()
                    }
                    _snackbarEvents.tryEmit("Privacy settings saved")
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to save privacy settings")
                }
        }
    }

    fun setChatDisappearingTimer(chat: ChatRoom, durationSeconds: Long) {
        viewModelScope.launch {
            chatRepository.updateDisappearingTimer(
                chatId = chat.id,
                memberUids = chat.memberUids,
                actorName = currentDisplayName(),
                durationSeconds = durationSeconds
            ).onSuccess {
                val label = when (durationSeconds) {
                    86_400L -> "24 Hours"
                    604_800L -> "7 Days"
                    2_592_000L -> "30 Days"
                    else -> "Off"
                }
                _snackbarEvents.tryEmit("Disappearing messages: $label")
            }.onFailure { e ->
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to update disappearing timer")
            }
        }
    }

    fun blockUser(target: PublicProfile, reason: String = "Blocked by user") {
        viewModelScope.launch {
            securityRepository.blockUser(target, reason)
                .onSuccess {
                    _snackbarEvents.tryEmit("${target.displayName} blocked")
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to block user")
                }
        }
    }

    fun unblockUser(targetUid: String, targetDisplayName: String) {
        viewModelScope.launch {
            securityRepository.unblockUser(targetUid, targetDisplayName)
                .onSuccess {
                    _snackbarEvents.tryEmit("$targetDisplayName unblocked")
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to unblock user")
                }
        }
    }

    fun submitReport(
        reportedUserId: String,
        reportedUserName: String,
        category: ReportCategory,
        description: String,
        chatId: String = "",
        messageId: String = "",
        messagePreview: String = "",
        onSubmitted: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            securityRepository.submitReport(
                reporterName = currentDisplayName(),
                reportedUserId = reportedUserId,
                reportedUserName = reportedUserName,
                category = category,
                description = description,
                chatId = chatId,
                messageId = messageId,
                messagePreview = messagePreview
            ).onSuccess { reportId ->
                _snackbarEvents.tryEmit("Report submitted (#${reportId.takeLast(6).uppercase(Locale.US)})")
                onSubmitted(reportId)
            }.onFailure { e ->
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to submit report")
            }
        }
    }

    fun reviewReport(reportId: String, status: ReportStatus, reviewNotes: String) {
        viewModelScope.launch {
            securityRepository.reviewReport(reportId, status, reviewNotes)
                .onSuccess {
                    _snackbarEvents.tryEmit("Report updated to ${status.label}")
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Review action denied")
                }
        }
    }

    fun enableTwoStepVerification(
        pin: String,
        recoveryEmail: String,
        onRecoveryCodeReady: (String) -> Unit
    ) {
        if (!PinCryptoUtils.isValidPinFormat(pin)) {
            _snackbarEvents.tryEmit("Enter a 6-digit PIN")
            return
        }
        val cleanEmail = recoveryEmail.trim()
        if (cleanEmail.isNotBlank() && !cleanEmail.contains("@")) {
            _snackbarEvents.tryEmit("Enter a valid recovery email or leave blank")
            return
        }
        val pinHash = PinCryptoUtils.hashSecret(pin)
        val recoveryCode = PinCryptoUtils.generateRecoveryCode()
        val recoveryHash = PinCryptoUtils.hashSecret(recoveryCode)
        val currentDevId = _deviceId.value

        viewModelScope.launch {
            val devices = if (currentDevId.isNotBlank()) listOf(currentDevId) else emptyList()
            securityRepository.saveTwoStepConfig(
                TwoStepSecurityConfig(
                    enabled = true,
                    pinHash = pinHash.hash,
                    pinSalt = pinHash.salt,
                    recoveryEmail = cleanEmail,
                    recoveryCodeHash = recoveryHash.hash,
                    recoveryCodeSalt = recoveryHash.salt,
                    registeredDeviceIds = devices,
                    failedAttempts = 0,
                    lockedUntilEpochMs = 0L
                )
            ).onSuccess {
                _sessionTwoStepVerified.value = true
                _snackbarEvents.tryEmit("Two-step verification enabled")
                onRecoveryCodeReady(recoveryCode)
            }.onFailure { e ->
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to enable two-step verification")
            }
        }
    }

    fun disableTwoStepVerification(pin: String, onResult: (Boolean) -> Unit = {}) {
        val cfg = twoStepConfig.value ?: return
        if (!PinCryptoUtils.verifySecret(pin, cfg.pinHash, cfg.pinSalt)) {
            _snackbarEvents.tryEmit("Incorrect 6-digit PIN")
            onResult(false)
            return
        }
        viewModelScope.launch {
            securityRepository.saveTwoStepConfig(
                cfg.copy(
                    enabled = false,
                    pinHash = "",
                    pinSalt = "",
                    recoveryCodeHash = "",
                    recoveryCodeSalt = "",
                    failedAttempts = 0,
                    lockedUntilEpochMs = 0L
                )
            ).onSuccess {
                _sessionTwoStepVerified.value = true
                _snackbarEvents.tryEmit("Two-step verification disabled")
                onResult(true)
            }.onFailure { e ->
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to disable two-step verification")
                onResult(false)
            }
        }
    }

    fun verifyTwoStepPin(pin: String, onResult: (Boolean, String?) -> Unit) {
        val cfg = twoStepConfig.value
        if (cfg == null || !cfg.enabled) {
            _sessionTwoStepVerified.value = true
            onResult(true, null)
            return
        }
        val now = System.currentTimeMillis()
        if (cfg.lockedUntilEpochMs > now) {
            val waitSec = ((cfg.lockedUntilEpochMs - now) / 1000L).coerceAtLeast(1L)
            onResult(false, "Too many failed attempts. Try again in ${waitSec}s or use your recovery code.")
            return
        }

        if (PinCryptoUtils.verifySecret(pin, cfg.pinHash, cfg.pinSalt)) {
            _sessionTwoStepVerified.value = true
            val devId = _deviceId.value
            val updatedDevices = (cfg.registeredDeviceIds + devId).filter { it.isNotBlank() }.distinct().take(50)
            viewModelScope.launch {
                securityRepository.saveTwoStepConfig(
                    cfg.copy(
                        registeredDeviceIds = updatedDevices,
                        failedAttempts = 0,
                        lockedUntilEpochMs = 0L
                    )
                )
            }
            onResult(true, null)
        } else {
            val attempts = cfg.failedAttempts + 1
            val lockUntil = if (attempts >= 5) now + 60_000L else 0L
            viewModelScope.launch {
                securityRepository.saveTwoStepConfig(
                    cfg.copy(failedAttempts = attempts, lockedUntilEpochMs = lockUntil)
                )
            }
            val msg = if (attempts >= 5) {
                "Incorrect PIN. Locked for 60s due to repeated attempts."
            } else {
                "Incorrect PIN (${5 - attempts} attempts remaining)"
            }
            onResult(false, msg)
        }
    }

    fun recoverTwoStepWithCode(
        recoveryCode: String,
        newPin: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val cfg = twoStepConfig.value
        if (cfg == null || !cfg.enabled) {
            onResult(false, "Two-step verification is not active")
            return
        }
        if (!PinCryptoUtils.verifySecret(recoveryCode, cfg.recoveryCodeHash, cfg.recoveryCodeSalt)) {
            onResult(false, "Invalid backup recovery code")
            return
        }
        if (!PinCryptoUtils.isValidPinFormat(newPin)) {
            onResult(false, "Enter a new 6-digit PIN")
            return
        }
        val nextHash = PinCryptoUtils.hashSecret(newPin)
        val devId = _deviceId.value
        val updatedDevices = (cfg.registeredDeviceIds + devId).filter { it.isNotBlank() }.distinct().take(50)
        viewModelScope.launch {
            securityRepository.saveTwoStepConfig(
                cfg.copy(
                    pinHash = nextHash.hash,
                    pinSalt = nextHash.salt,
                    registeredDeviceIds = updatedDevices,
                    failedAttempts = 0,
                    lockedUntilEpochMs = 0L
                )
            ).onSuccess {
                _sessionTwoStepVerified.value = true
                _snackbarEvents.tryEmit("PIN reset and device verified")
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.localizedMessage ?: "Recovery failed")
            }
        }
    }

    // --- Chat Lock Actions ---

    fun setChatLockPin(pin: String) {
        if (!PinCryptoUtils.isValidPinFormat(pin)) {
            _snackbarEvents.tryEmit("Chat Lock PIN must be 6 digits")
            return
        }
        val hashed = PinCryptoUtils.hashSecret(pin)
        val current = chatLockConfig.value
        viewModelScope.launch {
            securityRepository.saveChatLockConfig(
                current.copy(lockPinHash = hashed.hash, lockPinSalt = hashed.salt)
            ).onSuccess {
                _snackbarEvents.tryEmit("Chat Lock PIN updated")
            }.onFailure { e ->
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to save Chat Lock PIN")
            }
        }
    }

    fun setChatLockBiometricEnabled(enabled: Boolean) {
        val current = chatLockConfig.value
        viewModelScope.launch {
            securityRepository.saveChatLockConfig(current.copy(biometricEnabled = enabled))
        }
    }

    fun verifyChatLockPin(pin: String): Boolean {
        val cfg = chatLockConfig.value
        if (cfg.lockPinHash.isNotBlank() && PinCryptoUtils.verifySecret(pin, cfg.lockPinHash, cfg.lockPinSalt)) {
            return true
        }
        val twoStep = twoStepConfig.value
        if (twoStep != null && twoStep.enabled && twoStep.pinHash.isNotBlank()) {
            return PinCryptoUtils.verifySecret(pin, twoStep.pinHash, twoStep.pinSalt)
        }
        return false
    }

    fun hasAnySecurityPinConfigured(): Boolean =
        chatLockConfig.value.lockPinHash.isNotBlank() ||
            (twoStepConfig.value?.enabled == true && twoStepConfig.value?.pinHash?.isNotBlank() == true)

    fun toggleChatLocked(chatId: String) {
        val current = chatLockConfig.value
        val existing = current.lockedChatIds.toMutableList()
        val nowLocked = if (existing.contains(chatId)) {
            existing.remove(chatId)
            false
        } else {
            existing.add(chatId)
            true
        }
        if (nowLocked) {
            _unlockedChatIds.update { it - chatId }
        }
        viewModelScope.launch {
            securityRepository.saveChatLockConfig(current.copy(lockedChatIds = existing))
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (nowLocked) "Chat moved to Locked Chats"
                        else "Chat removed from Locked Chats"
                    )
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Unable to update Chat Lock")
                }
        }
    }

    fun markChatUnlockedForSession(chatId: String) {
        _unlockedChatIds.update { it + chatId }
    }

    fun setLockedVaultUnlocked(unlocked: Boolean) {
        _lockedVaultUnlocked.value = unlocked
        if (!unlocked) {
            _unlockedChatIds.value = emptySet()
        }
    }

    // --- E2EE Key Rotation & Safety Number ---

    fun rotateE2eeKeys() {
        viewModelScope.launch {
            e2eeKeyInitMutex.withLock {
                val nextVersion = (_localE2eeMaterial.value?.keyVersion ?: 0) + 1
                val fresh = E2eeCryptoManager.generateLocalKeyMaterial(currentUserId, keyVersion = nextVersion)
                deviceSecurityStore.saveE2eeMaterial(fresh)
                val allMaterials = deviceSecurityStore.loadAllE2eeMaterials(currentUserId).toMutableMap()
                allMaterials[fresh.keyFingerprint] = fresh
                _e2eeMaterialsByFp.value = allMaterials
                _localE2eeMaterial.value = fresh
                messageSecurityBridge.clearCache()
                securityRepository.publishKeyBundle(fresh.toPublicKeyBundle())
                    .onSuccess {
                        _snackbarEvents.tryEmit("Encryption keys rotated (v$nextVersion)")
                    }
                    .onFailure { e ->
                        _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to publish rotated keys")
                    }
            }
        }
    }

    fun computeSafetyNumberForPeer(peerUid: String): String {
        val myIk = _localE2eeMaterial.value?.identityPublicKey ?: return ""
        val peerIk = keyBundles.value[peerUid]?.identityPublicKey ?: return ""
        return E2eeCryptoManager.computeSafetyNumber(myIk, peerIk)
    }

    private fun isDirectPeerBlocked(chat: ChatRoom): Boolean {
        val other = chat.otherMemberUid(currentUserId) ?: return false
        return blockedUserIds.value.contains(other)
    }

    // --- Typing, Reactions, Themes, AI Studio, Calling (Existing Features Preserved) ---

    fun onComposerTextChanged(chatId: String, text: String) {
        if (text.isBlank()) {
            stopTyping(chatId)
            return
        }

        typingIdleJob?.cancel()
        typingIdleJob = viewModelScope.launch {
            delay(TYPING_IDLE_TIMEOUT_MS)
            if (activeTypingChatId == chatId) {
                activeTypingChatId = null
                lastTypingPingAtMs = 0L
                typingWriteMutex.withLock {
                    chatRepository.setTyping(chatId, false)
                }
            }
        }

        val now = System.currentTimeMillis()
        val previousChatId = activeTypingChatId
        if (previousChatId != null && previousChatId != chatId) {
            viewModelScope.launch {
                typingWriteMutex.withLock {
                    chatRepository.setTyping(previousChatId, false)
                }
            }
        }

        if (previousChatId != chatId || (now - lastTypingPingAtMs) >= TYPING_HEARTBEAT_INTERVAL_MS) {
            activeTypingChatId = chatId
            lastTypingPingAtMs = now
            viewModelScope.launch {
                typingWriteMutex.withLock {
                    chatRepository.setTyping(chatId, true)
                }
            }
        }
    }

    fun stopTyping(chatId: String? = activeTypingChatId) {
        val targetChatId = chatId ?: return
        typingIdleJob?.cancel()
        typingIdleJob = null
        if (activeTypingChatId == targetChatId) {
            activeTypingChatId = null
            lastTypingPingAtMs = 0L
            viewModelScope.launch {
                typingWriteMutex.withLock {
                    chatRepository.setTyping(targetChatId, false)
                }
            }
        }
    }

    private fun clearLocalTypingTracking(): String? {
        val previous = activeTypingChatId
        typingIdleJob?.cancel()
        typingIdleJob = null
        activeTypingChatId = null
        lastTypingPingAtMs = 0L
        return previous
    }

    fun setReplyingTo(message: ChatMessage?) {
        _replyingTo.value = message
    }

    fun setForwardingMessage(message: ChatMessage?) {
        _forwardingMessage.value = message
    }

    fun forwardMessageTo(targetChat: ChatRoom) {
        val msg = _forwardingMessage.value ?: return
        _forwardingMessage.value = null
        if (msg.decryptionError.isNotBlank()) {
            _snackbarEvents.tryEmit("Cannot forward a message that could not be decrypted")
            return
        }
        if (isDirectPeerBlocked(targetChat)) {
            _snackbarEvents.tryEmit("Cannot forward to a blocked user")
            return
        }
        viewModelScope.launch {
            dispatchEncryptedOrPlainMessage(
                chat = targetChat,
                type = msg.messageType,
                plainText = msg.text,
                mediaData = msg.mediaData.takeIf { it.isNotBlank() },
                mediaFileName = msg.mediaFileName.takeIf { it.isNotBlank() },
                mediaMimeType = msg.mediaMimeType.takeIf { it.isNotBlank() },
                mediaSizeBytes = msg.mediaSizeBytes.takeIf { it > 0L },
                durationMs = msg.durationMs.takeIf { it > 0L },
                waveform = msg.waveform.takeIf { it.isNotEmpty() },
                replyTo = null
            )
            _snackbarEvents.tryEmit("Forwarded to ${targetChat.displayTitleFor(currentUserId)}")
        }
    }

    fun toggleReaction(chatId: String, message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            chatRepository.toggleReaction(chatId, message, emoji)
        }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(chatId, messageId)
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to delete message")
                }
        }
    }

    fun togglePinChat(chat: ChatRoom) {
        viewModelScope.launch {
            chatRepository.togglePinChat(chat.id, chat.isPinnedBy(currentUserId))
        }
    }

    fun toggleArchiveChat(chat: ChatRoom) {
        val currentlyArchived = chat.isArchivedBy(currentUserId)
        val title = chat.displayTitleFor(currentUserId)
        viewModelScope.launch {
            chatRepository.toggleArchiveChat(chat.id, currentlyArchived)
                .onSuccess {
                    val msg = if (currentlyArchived) {
                        "Moved \"$title\" to Inbox"
                    } else {
                        "Archived \"$title\""
                    }
                    _archiveUndoEvents.tryEmit(
                        ArchiveUndoPrompt(
                            message = msg,
                            chatIds = listOf(chat.id),
                            restoreArchivedState = currentlyArchived
                        )
                    )
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to update archive status")
                }
        }
    }

    fun archiveChats(chatIds: List<String>) {
        if (chatIds.isEmpty()) return
        viewModelScope.launch {
            chatRepository.archiveChats(chatIds, archive = true)
                .onSuccess { count ->
                    if (count > 0) {
                        val label = if (count == 1) "1 conversation archived" else "$count conversations archived"
                        _archiveUndoEvents.tryEmit(
                            ArchiveUndoPrompt(
                                message = label,
                                chatIds = chatIds,
                                restoreArchivedState = false
                            )
                        )
                    }
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to archive conversations")
                }
        }
    }

    fun unarchiveChats(chatIds: List<String>) {
        if (chatIds.isEmpty()) return
        viewModelScope.launch {
            chatRepository.archiveChats(chatIds, archive = false)
                .onSuccess { count ->
                    if (count > 0) {
                        val label = if (count == 1) "Moved 1 conversation to Inbox" else "Moved $count conversations to Inbox"
                        _archiveUndoEvents.tryEmit(
                            ArchiveUndoPrompt(
                                message = label,
                                chatIds = chatIds,
                                restoreArchivedState = true
                            )
                        )
                    }
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to unarchive conversations")
                }
        }
    }

    fun restoreArchiveState(chatIds: List<String>, archived: Boolean) {
        if (chatIds.isEmpty()) return
        viewModelScope.launch {
            chatRepository.archiveChats(chatIds, archive = archived)
                .onSuccess {
                    _snackbarEvents.tryEmit(if (archived) "Restored to Archive" else "Restored to Inbox")
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Could not undo archive change")
                }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            if (_selectedChatId.value == chatId) {
                _selectedChatId.value = null
            }
            chatRepository.deleteChat(chatId)
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to delete chat")
                }
        }
    }

    fun updateProfile(displayName: String, bio: String, avatarBitmap: Bitmap? = null) {
        viewModelScope.launch {
            val avatarB64 = avatarBitmap?.let { MediaUtils.encodeBitmapToBase64(it, maxDimension = 320, quality = 70) }
            chatRepository.updateProfile(displayName, bio, avatarB64)
                .onSuccess { _snackbarEvents.tryEmit("Profile updated") }
                .onFailure { e -> _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to update profile") }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.updatePreferences(darkModeEnabled = enabled)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.updatePreferences(notificationsEnabled = enabled)
        }
    }

    fun updateChatTheme(chatId: String, theme: ChatThemePreference?) {
        if (chatId.isBlank()) return
        val wireSpec = theme?.takeUnless { it.isDefault }?.toWireString()
        viewModelScope.launch {
            chatRepository.updateChatTheme(chatId, wireSpec)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (wireSpec.isNullOrBlank()) "Reset to default theme"
                        else "Chat theme updated"
                    )
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Failed to update chat theme")
                }
        }
    }

    fun setAiSourceBitmap(bitmap: Bitmap?, suggestedPrompt: String = "") {
        _aiSourceBitmap.value = bitmap
        if (suggestedPrompt.isNotBlank()) {
            _aiSuggestedPrompt.value = suggestedPrompt
        }
    }

    fun resetAiState() {
        _aiUiState.value = AiStudioUiState.Idle
    }

    fun generateAiImage(prompt: String, aspectRatio: AiAspectRatio) {
        viewModelScope.launch {
            _aiUiState.value = AiStudioUiState.Loading(isEditing = false)
            val result = aiImageRepository.generateImage(prompt, aspectRatio)
            result.onSuccess { bitmap ->
                _aiUiState.value = AiStudioUiState.Success(
                    bitmap = bitmap,
                    prompt = prompt.trim(),
                    isEdited = false
                )
            }.onFailure { error ->
                _aiUiState.value = AiStudioUiState.Error(
                    error.localizedMessage ?: "Failed to generate image."
                )
            }
        }
    }

    fun editAiImage(inputBitmap: Bitmap, instruction: String, aspectRatio: AiAspectRatio) {
        viewModelScope.launch {
            _aiUiState.value = AiStudioUiState.Loading(isEditing = true)
            val result = aiImageRepository.editImage(inputBitmap, instruction, aspectRatio)
            result.onSuccess { bitmap ->
                _aiUiState.value = AiStudioUiState.Success(
                    bitmap = bitmap,
                    prompt = instruction.trim(),
                    isEdited = true
                )
            }.onFailure { error ->
                _aiUiState.value = AiStudioUiState.Error(
                    error.localizedMessage ?: "Failed to edit image."
                )
            }
        }
    }

    fun notifyMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    fun startDirectCall(
        peerProfile: PublicProfile,
        callType: CallType,
        existingChatRoom: ChatRoom? = null
    ) {
        if (blockedUserIds.value.contains(peerProfile.userId)) {
            _snackbarEvents.tryEmit("Unblock ${peerProfile.displayName} before placing a call")
            return
        }
        if (existingChatRoom != null) {
            callCoordinator.startOutgoingCall(existingChatRoom, peerProfile, callType)
            return
        }
        viewModelScope.launch {
            val me = currentPublicProfile()
            chatRepository.createOrGetDirectChat(me, peerProfile)
                .onSuccess { chatId ->
                    val room = ChatRoom(
                        id = chatId,
                        type = "direct",
                        title = peerProfile.displayName,
                        memberUids = listOf(currentUserId, peerProfile.userId)
                    )
                    callCoordinator.startOutgoingCall(room, peerProfile, callType)
                }
                .onFailure { e ->
                    _snackbarEvents.tryEmit(e.localizedMessage ?: "Unable to start call")
                }
        }
    }

    fun acceptActiveCall() = callCoordinator.acceptCall()
    fun rejectActiveCall() = callCoordinator.rejectCall()
    fun endActiveCall() = callCoordinator.endCall()
    fun reconnectActiveCall() = callCoordinator.reconnectCall()
    fun toggleCallMute() = callCoordinator.toggleMute()
    fun toggleCallSpeaker() = callCoordinator.toggleSpeaker()
    fun toggleCallCamera() = callCoordinator.toggleCamera()
    fun switchCallCamera() = callCoordinator.switchCamera()
    fun toggleCallFullScreen() = callCoordinator.toggleFullScreenVideo()
    fun setCallMinimized(minimized: Boolean) = callCoordinator.setMinimized(minimized)
    fun deleteCallHistoryEntry(callId: String) = callCoordinator.deleteCallHistory(callId)
    fun handleCallNotificationAction(callId: String, action: CallActionType) =
        callCoordinator.handleNotificationAction(callId, action)
    fun attachLocalVideoRenderer(renderer: SurfaceViewRenderer) = callCoordinator.attachLocalRenderer(renderer)
    fun detachLocalVideoRenderer(renderer: SurfaceViewRenderer) = callCoordinator.detachLocalRenderer(renderer)
    fun attachRemoteVideoRenderer(renderer: SurfaceViewRenderer) = callCoordinator.attachRemoteRenderer(renderer)
    fun detachRemoteVideoRenderer(renderer: SurfaceViewRenderer) = callCoordinator.detachRemoteRenderer(renderer)

    override fun onCleared() {
        presenceHeartbeatJob?.cancel()
        networkMonitor.dispose()
        clearLocalTypingTracking()
        callCoordinator.dispose()
        messageSecurityBridge.clearCache()
        super.onCleared()
    }

    private fun currentDisplayName(): String {
        val priv = privateUser.value?.displayName
        val fb = Firebase.auth.currentUser?.displayName
        return priv?.takeIf { it.isNotBlank() } ?: fb?.takeIf { it.isNotBlank() } ?: "Pulse User"
    }

    private fun currentPublicProfile(): PublicProfile {
        val priv = privateUser.value
        val name = currentDisplayName()
        return PublicProfile(
            userId = currentUserId,
            displayName = name,
            handle = name.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "user" },
            bio = priv?.bio.orEmpty(),
            avatarData = priv?.avatarData.orEmpty(),
            photoUrl = priv?.photoUrl.orEmpty()
        )
    }

    private fun maybeNotifyIncomingMessages(
        rooms: List<ChatRoom>,
        lockedChatIds: Set<String>,
        blockedIds: Set<String>
    ) {
        val enabled = privateUser.value?.notificationsEnabled ?: true
        if (!enabled) return
        val currentlyViewingChat = _selectedChatId.value
        for (room in rooms) {
            val msgTime = room.lastMessageAt ?: continue
            val msgMillis = msgTime.toDate().time
            val sessionMillis = sessionStartedAt.toDate().time
            if (msgMillis <= sessionMillis) continue
            if (room.lastMessageSenderId.isBlank() || room.lastMessageSenderId == currentUserId) continue
            if (blockedIds.contains(room.lastMessageSenderId)) continue
            if (room.id == currentlyViewingChat) continue

            val lastNotified = notifiedMessageTimestamps[room.id] ?: 0L
            if (msgMillis > lastNotified) {
                notifiedMessageTimestamps[room.id] = msgMillis
                val isLocked = lockedChatIds.contains(room.id)
                val sender = room.memberNames[room.lastMessageSenderId] ?: room.displayTitleFor(currentUserId)
                if (room.lastMessageType == MessageType.SYSTEM.wireValue &&
                    room.lastMessageText.contains("took a screenshot", ignoreCase = true)
                ) {
                    val timeLabel = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(msgMillis))
                    NotificationHelper.showScreenshotAlertNotification(
                        context = appContext,
                        chatId = room.id,
                        actorName = sender,
                        formattedTime = timeLabel,
                        isLockedChat = isLocked
                    )
                } else {
                    NotificationHelper.showIncomingMessageNotification(
                        context = appContext,
                        chatId = room.id,
                        senderName = sender,
                        preview = room.lastMessageText,
                        isLockedChat = isLocked
                    )
                }
            }
        }
    }

    private fun acknowledgeIncomingDeliveries(
        rooms: List<ChatRoom>,
        blockedIds: Set<String>
    ) {
        for (room in rooms) {
            if (room.id.isBlank()) continue
            if (room.lastMessageSenderId.isBlank() || room.lastMessageSenderId == currentUserId) continue
            if (blockedIds.contains(room.lastMessageSenderId)) continue
            if (!room.hasUndeliveredLatestMessageFor(currentUserId)) continue

            val msgTimeMs = room.lastMessageAt?.toDate()?.time ?: 0L
            val key = "${room.id}:${room.lastMessageSenderId}:$msgTimeMs:${room.lastMessageText.hashCode()}"
            if (inFlightDeliveredChatKeys.add(key)) {
                viewModelScope.launch {
                    chatRepository.markUndeliveredMessagesInChat(room.id)
                        .onFailure { inFlightDeliveredChatKeys.remove(key) }
                }
            }
        }
    }

    fun changeAccountPassword(
        currentPassword: String,
        newPassword: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = authService.changePassword(currentPassword, newPassword)
            res.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Password updated successfully")
                    onResult(Result.success(Unit))
                },
                onFailure = { err ->
                    onResult(Result.failure(err))
                }
            )
        }
    }

    fun linkUsernameCredentials(
        username: String,
        password: String,
        recoveryEmail: String = "",
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val effectiveEmail = recoveryEmail.trim().ifBlank { privateUser.value?.email.orEmpty() }
            val res = authService.linkUsernameCredentials(
                username = username,
                password = password,
                recoveryEmail = effectiveEmail
            )
            res.fold(
                onSuccess = { linked ->
                    val store = AuthSessionStore(appContext)
                    store.saveLoginPreference(store.isRememberMeEnabled(), linked.username)
                    _snackbarEvents.tryEmit("Username @${linked.username} linked to your account")
                    onResult(Result.success(Unit))
                },
                onFailure = { err ->
                    onResult(Result.failure(err))
                }
            )
        }
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    companion object {
        private const val TAG = "PulseChatVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        private const val TYPING_IDLE_TIMEOUT_MS = 3000L
        private const val TYPING_HEARTBEAT_INTERVAL_MS = 3500L

        fun provideFactory(
            context: Context,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appCtx = context.applicationContext
                PulseChatViewModel(
                    chatRepository = ChatRepository(appCtx),
                    callRepository = CallRepository(appCtx),
                    securityRepository = SecurityRepository(appCtx),
                    aiImageRepository = FirebaseAiImageRepository(),
                    deviceSecurityStore = DeviceSecurityStore(appCtx),
                    currentUserId = currentUserId,
                    appContext = appCtx
                )
            }
        }
    }
}
