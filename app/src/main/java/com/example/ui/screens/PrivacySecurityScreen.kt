package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BlockHistoryEvent
import com.example.data.model.BlockedUserRecord
import com.example.data.model.ChatLockConfig
import com.example.data.model.ChatRoom
import com.example.data.model.OnlineVisibility
import com.example.data.model.PrivacySettings
import com.example.data.model.PrivacyVisibility
import com.example.data.model.ReportCategory
import com.example.data.model.ReportStatus
import com.example.data.model.ScreenshotProtectionMode
import com.example.data.model.SpamGuardState
import com.example.data.model.SpamProtectionLevel
import com.example.data.model.TwoStepSecurityConfig
import com.example.data.model.UserReport
import com.example.security.LocalE2eeKeyMaterial
import com.example.security.PinCryptoUtils
import com.example.ui.components.DisappearingTimerDialog
import com.example.ui.components.ReportDialog
import com.example.ui.components.SafetyNumberDialog
import com.example.ui.components.TwoStepSetupDialog
import com.example.ui.components.UnlockSecurityDialog
import java.text.SimpleDateFormat
import java.util.Locale

private enum class VisibilityTarget {
    LAST_SEEN,
    ONLINE_STATUS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecurityScreen(
    currentUserId: String,
    privacySettings: PrivacySettings,
    twoStepConfig: TwoStepSecurityConfig?,
    chatLockConfig: ChatLockConfig,
    spamGuardState: SpamGuardState,
    blockedUsers: List<BlockedUserRecord>,
    blockHistory: List<BlockHistoryEvent>,
    localE2eeMaterial: LocalE2eeKeyMaterial?,
    allChats: List<ChatRoom>,
    myReports: List<UserReport>,
    isAdmin: Boolean,
    adminReports: List<UserReport>,
    lockedVaultUnlocked: Boolean,
    hasAnySecurityPin: Boolean,
    initialSection: String? = null,
    onSensitiveScreenChanged: (Boolean) -> Unit,
    onUpdatePrivacySettings: ((PrivacySettings) -> PrivacySettings) -> Unit,
    onEnableTwoStep: (String, String, (String) -> Unit) -> Unit,
    onDisableTwoStep: (String, (Boolean) -> Unit) -> Unit,
    onSetChatLockPin: (String) -> Unit,
    onSetChatLockBiometric: (Boolean) -> Unit,
    onVerifyChatLockPin: (String) -> Boolean,
    onSetLockedVaultUnlocked: (Boolean) -> Unit,
    onToggleChatLocked: (String) -> Unit,
    onOpenLockedChat: (ChatRoom) -> Unit,
    onUnblockUser: (String, String) -> Unit,
    onRotateE2eeKeys: () -> Unit,
    onSubmitProblemReport: (ReportCategory, String, (String) -> Unit) -> Unit,
    onAdminReviewReport: (String, ReportStatus, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    DisposableEffect(Unit) {
        onSensitiveScreenChanged(true)
        onDispose {
            onSensitiveScreenChanged(false)
        }
    }

    var showE2eeDialog by remember { mutableStateOf(false) }
    var showBlockedSheet by remember { mutableStateOf(false) }
    var showReportsSheet by remember { mutableStateOf(false) }
    var showProblemReportDialog by remember { mutableStateOf(false) }
    var showTwoStepSetup by remember { mutableStateOf(false) }
    var showTwoStepDisableDialog by remember { mutableStateOf(false) }
    var showDefaultDisappearDialog by remember { mutableStateOf(false) }
    var showLockedVaultGate by remember { mutableStateOf(initialSection == "locked_chats" && !lockedVaultUnlocked) }
    var showLockedVaultSheet by remember { mutableStateOf(initialSection == "locked_chats" && lockedVaultUnlocked) }
    var showChatLockPinDialog by remember { mutableStateOf(false) }
    var visibilityTarget by remember { mutableStateOf<VisibilityTarget?>(null) }
    var showScreenshotSheet by remember { mutableStateOf(false) }
    var showSpamSheet by remember { mutableStateOf(false) }
    var reviewingReport by remember { mutableStateOf<UserReport?>(null) }

    val lockedChats = remember(allChats, chatLockConfig.lockedChatIds) {
        val ids = chatLockConfig.lockedChatIds.toSet()
        allChats.filter { ids.contains(it.id) }
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
                            .testTag("privacy_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text("Privacy & Security", fontWeight = FontWeight.Bold)
                        Text(
                            text = "End-to-end encryption, locks & protection",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Security Overview Banner
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (privacySettings.e2eeEnabled) "Security Shield Active" else "Custom Privacy Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${lockedChats.size} locked • ${blockedUsers.size} blocked • 2FA ${if (twoStepConfig?.enabled == true) "On" else "Off"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1. End-to-End Encryption
            SecuritySectionCard(title = "Encryption & Keys") {
                SecurityToggleRow(
                    icon = Icons.Rounded.EnhancedEncryption,
                    title = "End-to-End Encryption",
                    subtitle = "ECDH P-256 + HKDF-SHA256 + AES-256-GCM for private texts & media",
                    checked = privacySettings.e2eeEnabled,
                    onCheckedChange = { enabled ->
                        onUpdatePrivacySettings { it.copy(e2eeEnabled = enabled) }
                    },
                    testTag = "toggle_e2ee"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SecurityNavRow(
                    icon = Icons.Rounded.Key,
                    title = "Encryption Key & Safety Info",
                    subtitle = localE2eeMaterial?.let {
                        "Key v${it.keyVersion} • ${it.keyFingerprint}"
                    } ?: "Initializing keystore...",
                    onClick = { showE2eeDialog = true },
                    testTag = "row_e2ee_details"
                )
            }

            // 2. Chat Privacy & Presence
            SecuritySectionCard(title = "Messaging & Presence Privacy") {
                SecurityNavRow(
                    icon = Icons.Rounded.Visibility,
                    title = "Last Seen",
                    subtitle = privacySettings.lastSeenVisibilityEnum.label,
                    onClick = { visibilityTarget = VisibilityTarget.LAST_SEEN },
                    testTag = "row_last_seen_visibility"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SecurityNavRow(
                    icon = Icons.Rounded.NotificationsOff,
                    title = "Online Status",
                    subtitle = privacySettings.onlineVisibilitySetting.label,
                    onClick = { visibilityTarget = VisibilityTarget.ONLINE_STATUS },
                    testTag = "row_online_visibility"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SecurityNavRow(
                    icon = Icons.Rounded.Timer,
                    title = "Disappearing Messages",
                    subtitle = "Default for new chats: ${privacySettings.defaultDisappearingOption.label}",
                    onClick = { showDefaultDisappearDialog = true },
                    testTag = "row_default_disappearing"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SecurityNavRow(
                    icon = Icons.Rounded.Screenshot,
                    title = "Screenshot Protection",
                    subtitle = privacySettings.screenshotModeEnum.label,
                    onClick = { showScreenshotSheet = true },
                    testTag = "row_screenshot_protection"
                )
            }

            // 3. Chat Lock & Two-Step Verification
            SecuritySectionCard(title = "Locks & Account Security") {
                SecurityNavRow(
                    icon = Icons.Rounded.Lock,
                    title = "Locked Chats",
                    subtitle = "${lockedChats.size} hidden conversation(s) protected by biometrics or PIN",
                    onClick = {
                        if (lockedVaultUnlocked) {
                            showLockedVaultSheet = true
                        } else {
                            showLockedVaultGate = true
                        }
                    },
                    testTag = "row_locked_chats"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SecurityNavRow(
                    icon = Icons.Rounded.PhonelinkLock,
                    title = "Two-Step Verification",
                    subtitle = if (twoStepConfig?.enabled == true) {
                        "Enabled • New-device PIN & recovery code active"
                    } else {
                        "Off • Add a 6-digit PIN for new device sign-ins"
                    },
                    onClick = {
                        if (twoStepConfig?.enabled == true) {
                            showTwoStepDisableDialog = true
                        } else {
                            showTwoStepSetup = true
                        }
                    },
                    testTag = "row_two_step_verification"
                )
            }

            // 4. Blocking, Spam & Reporting
            SecuritySectionCard(title = "Safety, Spam & Reporting") {
                SecurityNavRow(
                    icon = Icons.Rounded.Block,
                    title = "Blocked Contacts",
                    subtitle = "${blockedUsers.size} blocked user(s) • History logged",
                    onClick = { showBlockedSheet = true },
                    testTag = "row_blocked_contacts"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SecurityNavRow(
                    icon = Icons.Rounded.Security,
                    title = "Spam Protection",
                    subtitle = buildString {
                        append(privacySettings.spamProtectionLevelEnum.label)
                        val now = System.currentTimeMillis()
                        if (spamGuardState.restrictedUntilEpochMs > now) {
                            val sec = ((spamGuardState.restrictedUntilEpochMs - now) / 1000L).coerceAtLeast(1L)
                            append(" • Cooldown active (${sec}s)")
                        }
                    },
                    onClick = { showSpamSheet = true },
                    testTag = "row_spam_protection"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SecurityNavRow(
                    icon = Icons.Rounded.ReportProblem,
                    title = "Report a Problem",
                    subtitle = "Submit security, abuse, or bug reports (${myReports.size} submitted)",
                    onClick = { showProblemReportDialog = true },
                    testTag = "row_report_problem"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SecurityNavRow(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = if (isAdmin) "Report Center & Admin Review" else "My Submitted Reports",
                    subtitle = if (isAdmin) {
                        "${adminReports.count { it.statusEnum == ReportStatus.PENDING }} pending review"
                    } else {
                        "Track status updates from our moderation team"
                    },
                    onClick = { showReportsSheet = true },
                    testTag = "row_reports_center"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showE2eeDialog) {
        SafetyNumberDialog(
            peerName = "Your Account",
            safetyNumber = "",
            myKeyFingerprint = localE2eeMaterial?.keyFingerprint.orEmpty(),
            myKeyVersion = localE2eeMaterial?.keyVersion ?: 1,
            peerHasKeyBundle = false,
            onRotateKeys = onRotateE2eeKeys,
            onDismiss = { showE2eeDialog = false }
        )
    }

    val activeVisibilityTarget = visibilityTarget
    if (activeVisibilityTarget == VisibilityTarget.LAST_SEEN) {
        VisibilityPickerDialog(
            title = "Who can see my Last Seen",
            selected = privacySettings.lastSeenVisibilityEnum,
            onSelect = { choice ->
                onUpdatePrivacySettings { prev ->
                    prev.copy(lastSeenVisibility = choice.wireValue)
                }
                visibilityTarget = null
            },
            onDismiss = { visibilityTarget = null }
        )
    } else if (activeVisibilityTarget == VisibilityTarget.ONLINE_STATUS) {
        OnlineVisibilityPickerDialog(
            title = "Who can see when I'm Online",
            selected = privacySettings.onlineVisibilitySetting,
            onSelect = { choice ->
                onUpdatePrivacySettings { prev ->
                    prev.copy(onlineVisibility = choice.wireValue)
                }
                visibilityTarget = null
            },
            onDismiss = { visibilityTarget = null }
        )
    }

    if (showDefaultDisappearDialog) {
        DisappearingTimerDialog(
            title = "Default Disappearing Timer",
            subtitle = "Automatically apply this message expiration timer when starting new direct chats.",
            currentDurationSeconds = privacySettings.defaultDisappearingSeconds,
            onSelectDuration = { seconds ->
                onUpdatePrivacySettings { it.copy(defaultDisappearingSeconds = seconds) }
            },
            onDismiss = { showDefaultDisappearDialog = false }
        )
    }

    if (showScreenshotSheet) {
        ScreenshotProtectionSheet(
            currentMode = privacySettings.screenshotModeEnum,
            onSelectMode = { mode ->
                onUpdatePrivacySettings { it.copy(screenshotMode = mode.wireValue) }
            },
            onDismiss = { showScreenshotSheet = false }
        )
    }

    if (showSpamSheet) {
        SpamProtectionSheet(
            currentLevel = privacySettings.spamProtectionLevelEnum,
            spamGuardState = spamGuardState,
            onSelectLevel = { level ->
                onUpdatePrivacySettings { it.copy(spamProtectionLevel = level.wireValue) }
            },
            onDismiss = { showSpamSheet = false }
        )
    }

    if (showTwoStepSetup) {
        TwoStepSetupDialog(
            initialRecoveryEmail = twoStepConfig?.recoveryEmail.orEmpty(),
            onSubmit = onEnableTwoStep,
            onDismiss = { showTwoStepSetup = false }
        )
    }

    if (showTwoStepDisableDialog) {
        DisableTwoStepDialog(
            onConfirmDisable = { pin, done ->
                onDisableTwoStep(pin) { ok ->
                    done(ok)
                    if (ok) showTwoStepDisableDialog = false
                }
            },
            onDismiss = { showTwoStepDisableDialog = false }
        )
    }

    if (showLockedVaultGate) {
        UnlockSecurityDialog(
            title = "Unlock Locked Chats",
            subtitle = "Authenticate with biometrics or your security PIN to open hidden conversations.",
            biometricEnabled = chatLockConfig.biometricEnabled,
            hasPinConfigured = hasAnySecurityPin,
            onVerifyPin = onVerifyChatLockPin,
            onCreatePin = onSetChatLockPin,
            onUnlocked = {
                onSetLockedVaultUnlocked(true)
                showLockedVaultGate = false
                showLockedVaultSheet = true
            },
            onDismiss = { showLockedVaultGate = false }
        )
    }

    if (showLockedVaultSheet) {
        LockedChatsVaultSheet(
            currentUserId = currentUserId,
            lockedChats = lockedChats,
            biometricEnabled = chatLockConfig.biometricEnabled,
            hasCustomPin = chatLockConfig.lockPinHash.isNotBlank(),
            onToggleBiometric = onSetChatLockBiometric,
            onChangePinClick = { showChatLockPinDialog = true },
            onOpenChat = { room ->
                showLockedVaultSheet = false
                onOpenLockedChat(room)
            },
            onUnlockChat = onToggleChatLocked,
            onLockVault = {
                onSetLockedVaultUnlocked(false)
                showLockedVaultSheet = false
            },
            onDismiss = { showLockedVaultSheet = false }
        )
    }

    if (showChatLockPinDialog) {
        SetChatLockPinDialog(
            onSavePin = { pin ->
                onSetChatLockPin(pin)
                showChatLockPinDialog = false
            },
            onDismiss = { showChatLockPinDialog = false }
        )
    }

    if (showBlockedSheet) {
        BlockedContactsSheet(
            blockedUsers = blockedUsers,
            blockHistory = blockHistory,
            onUnblock = onUnblockUser,
            onDismiss = { showBlockedSheet = false }
        )
    }

    if (showProblemReportDialog) {
        ReportDialog(
            title = "Report a Problem",
            targetUserName = "",
            initialCategory = ReportCategory.PROBLEM,
            onSubmit = { cat, desc, done ->
                onSubmitProblemReport(cat, desc, done)
            },
            onDismiss = { showProblemReportDialog = false }
        )
    }

    if (showReportsSheet) {
        ReportsCenterSheet(
            isAdmin = isAdmin,
            myReports = myReports,
            adminReports = adminReports,
            onInspectForReview = { report -> reviewingReport = report },
            onDismiss = { showReportsSheet = false }
        )
    }

    val targetReview = reviewingReport
    if (targetReview != null && isAdmin) {
        AdminReviewDialog(
            report = targetReview,
            onSubmitReview = { status, notes ->
                onAdminReviewReport(targetReview.id, status, notes)
                reviewingReport = null
            },
            onDismiss = { reviewingReport = null }
        )
    }
}

@Composable
private fun SecuritySectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SecurityNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Open $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SecurityToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun VisibilityPickerDialog(
    title: String,
    selected: PrivacyVisibility,
    onSelect: (PrivacyVisibility) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrivacyVisibility.entries.forEach { option ->
                    val isSelected = option == selected
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .testTag("visibility_option_${option.wireValue}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(selected = isSelected, onClick = { onSelect(option) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun OnlineVisibilityPickerDialog(
    title: String,
    selected: OnlineVisibility,
    onSelect: (OnlineVisibility) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OnlineVisibility.entries.forEach { option ->
                    val isSelected = option == selected
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .testTag("online_visibility_option_${option.wireValue}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(selected = isSelected, onClick = { onSelect(option) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotProtectionSheet(
    currentMode: ScreenshotProtectionMode,
    onSelectMode: (ScreenshotProtectionMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Screenshot Protection",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            ScreenshotProtectionMode.entries.forEach { mode ->
                val selected = mode == currentMode
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectMode(mode)
                            onDismiss()
                        }
                        .testTag("screenshot_mode_${mode.wireValue}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(selected = selected, onClick = {
                            onSelectMode(mode)
                            onDismiss()
                        })
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "OS Limitations: Screenshot notifications use Android 14+ ScreenCaptureCallback strictly inside active PulseChat chats and never monitor other apps. Older Android versions or external cameras cannot be detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpamProtectionSheet(
    currentLevel: SpamProtectionLevel,
    spamGuardState: SpamGuardState,
    onSelectLevel: (SpamProtectionLevel) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Spam & Abuse Protection",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            SpamProtectionLevel.entries.forEach { level ->
                val selected = level == currentLevel
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectLevel(level)
                            onDismiss()
                        }
                        .testTag("spam_level_${level.wireValue}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(level.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                level.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(selected = selected, onClick = {
                            onSelectLevel(level)
                            onDismiss()
                        })
                    }
                }
            }
            if (spamGuardState.violationCount > 0 || spamGuardState.lastViolationReason.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Recent Spam Guard Triggers: ${spamGuardState.violationCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (spamGuardState.lastViolationReason.isNotBlank()) {
                            Text(
                                text = spamGuardState.lastViolationReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedContactsSheet(
    blockedUsers: List<BlockedUserRecord>,
    blockHistory: List<BlockHistoryEvent>,
    onUnblock: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Blocked Contacts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (blockedUsers.isEmpty()) {
                Text(
                    text = "You haven't blocked anyone. Blocked users cannot send you messages, place calls, or view your online status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(210.dp)
                ) {
                    items(blockedUsers, key = { it.blockedUid }) { record ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.blockedDisplayName.ifBlank { record.blockedUid },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (record.blockedHandle.isNotBlank()) {
                                        Text(
                                            text = "@${record.blockedHandle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onUnblock(record.blockedUid, record.blockedDisplayName) },
                                    modifier = Modifier.testTag("unblock_user_${record.blockedUid}")
                                ) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            }

            if (blockHistory.isNotEmpty()) {
                Text(
                    text = "Block History",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(140.dp)
                ) {
                    items(blockHistory, key = { it.id }) { event ->
                        val timeStr = event.createdAt?.toDate()?.let { dateFmt.format(it) }.orEmpty()
                        Text(
                            text = "• ${event.action.replaceFirstChar { it.uppercase() }} ${event.targetDisplayName} ($timeStr)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockedChatsVaultSheet(
    currentUserId: String,
    lockedChats: List<ChatRoom>,
    biometricEnabled: Boolean,
    hasCustomPin: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    onChangePinClick: () -> Unit,
    onOpenChat: (ChatRoom) -> Unit,
    onUnlockChat: (String) -> Unit,
    onLockVault: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Locked Chats Vault",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onLockVault) {
                    Text("Lock Now")
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Biometric / Device Lock", style = MaterialTheme.typography.titleSmall)
                        }
                        Switch(checked = biometricEnabled, onCheckedChange = onToggleBiometric)
                    }
                    OutlinedButton(
                        onClick = onChangePinClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (hasCustomPin) "Change Chat Lock PIN" else "Set Dedicated Chat Lock PIN")
                    }
                }
            }

            if (lockedChats.isEmpty()) {
                Text(
                    text = "No locked chats yet. Open any conversation menu or long-press a chat to lock it. Notification previews for locked chats are automatically hidden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(lockedChats, key = { it.id }) { room ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChat(room) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = room.displayTitleFor(currentUserId),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Notification previews masked",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onUnlockChat(room.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LockOpen,
                                        contentDescription = "Remove chat from Locked Chats",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisableTwoStepDialog(
    onConfirmDisable: (String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable Two-Step Verification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter your current 6-digit Two-Step PIN to turn off new-device verification.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(6)
                        errorMsg = null
                    },
                    label = { Text("Current 6-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDisable(pin) { ok ->
                        if (!ok) errorMsg = "Incorrect PIN"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Disable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SetChatLockPinDialog(
    onSavePin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat Lock PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(6)
                        errorMsg = null
                    },
                    label = { Text("New 6-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it.filter { c -> c.isDigit() }.take(6)
                        errorMsg = null
                    },
                    label = { Text("Confirm 6-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        !PinCryptoUtils.isValidPinFormat(pin) -> errorMsg = "PIN must be 6 digits"
                        pin != confirmPin -> errorMsg = "PINs do not match"
                        else -> onSavePin(pin)
                    }
                }
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsCenterSheet(
    isAdmin: Boolean,
    myReports: List<UserReport>,
    adminReports: List<UserReport>,
    onInspectForReview: (UserReport) -> Unit,
    onDismiss: () -> Unit
) {
    var showAdminTab by remember(isAdmin) { mutableStateOf(isAdmin) }
    val reports = if (showAdminTab && isAdmin) adminReports else myReports
    val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isAdmin) "Report & Moderation Center" else "My Submitted Reports",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showAdminTab,
                        onClick = { showAdminTab = true },
                        label = { Text("Admin Queue (${adminReports.size})") }
                    )
                    FilterChip(
                        selected = !showAdminTab,
                        onClick = { showAdminTab = false },
                        label = { Text("My Reports (${myReports.size})") }
                    )
                }
            }

            if (reports.isEmpty()) {
                Text(
                    text = "No reports recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(reports, key = { it.id }) { report ->
                        val dateStr = report.createdAt?.toDate()?.let { dateFmt.format(it) }.orEmpty()
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = showAdminTab && isAdmin) {
                                    onInspectForReview(report)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${report.categoryEnum.label} • #${report.id.takeLast(6).uppercase(Locale.US)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = report.statusEnum.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (report.reportedUserName.isNotBlank()) {
                                    Text(
                                        text = "Target: ${report.reportedUserName}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (report.description.isNotBlank()) {
                                    Text(
                                        text = report.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (report.reviewNotes.isNotBlank()) {
                                    Text(
                                        text = "Moderator note: ${report.reviewNotes}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (dateStr.isNotBlank()) {
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminReviewDialog(
    report: UserReport,
    onSubmitReview: (ReportStatus, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(report.statusEnum) }
    var notes by remember { mutableStateOf(report.reviewNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review Report #${report.id.takeLast(6).uppercase(Locale.US)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Reporter: ${report.reporterName.ifBlank { report.reporterId }}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (report.reportedUserName.isNotBlank()) {
                    Text(
                        text = "Reported account: ${report.reportedUserName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (report.description.isNotBlank()) {
                    Text(
                        text = report.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Decision status", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ReportStatus.UNDER_REVIEW, ReportStatus.RESOLVED, ReportStatus.DISMISSED).forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Review notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmitReview(selectedStatus, notes) }) {
                Text("Save Decision")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
