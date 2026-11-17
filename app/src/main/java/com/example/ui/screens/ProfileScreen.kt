package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import com.example.data.model.UserPrivate
import com.example.ui.auth.AccountPasswordSecurityCard
import com.example.ui.auth.ChangePasswordDialog
import com.example.ui.auth.LinkUsernameDialog
import com.example.ui.auth.signOutUser
import com.example.ui.components.UserAvatar
import com.example.util.MediaUtils
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    privateUser: UserPrivate?,
    twoStepEnabled: Boolean = false,
    lockedChatsCount: Int = 0,
    blockedUsersCount: Int = 0,
    onOpenPrivacySecurity: () -> Unit = {},
    onSaveProfile: (String, String, Bitmap?) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onChangePassword: (String, String, (Result<Unit>) -> Unit) -> Unit = { _, _, cb ->
        cb(Result.failure(IllegalStateException("Password change unavailable")))
    },
    onLinkUsername: (String, String, String, (Result<Unit>) -> Unit) -> Unit = { _, _, _, cb ->
        cb(Result.failure(IllegalStateException("Username linking unavailable")))
    },
    onBeforeSignOut: suspend () -> Unit = {},
    onSignedOut: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var displayName by remember { mutableStateOf(privateUser?.displayName.orEmpty()) }
    var bio by remember { mutableStateOf(privateUser?.bio.orEmpty()) }
    var pendingAvatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showLinkUsernameDialog by remember { mutableStateOf(false) }
    var isPasswordActionLoading by remember { mutableStateOf(false) }
    var passwordActionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(privateUser?.displayName, privateUser?.bio) {
        if (privateUser != null) {
            if (displayName.isBlank()) displayName = privateUser.displayName
            if (bio.isBlank()) bio = privateUser.bio
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = MediaUtils.uriToBitmap(context, uri, maxDimension = 400)
                if (bmp != null) {
                    pendingAvatarBitmap = bmp
                    onSaveProfile(displayName, bio, bmp)
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onToggleNotifications(granted)
        if (!granted) {
            onNotify("Notification permission denied")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Profile & Settings",
            style = MaterialTheme.typography.headlineSmall
        )

        // Avatar & Account Summary
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.clickable {
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    UserAvatar(
                        name = displayName.ifBlank { privateUser?.displayName ?: "Pulse User" },
                        avatarData = privateUser?.avatarData.orEmpty(),
                        photoUrl = privateUser?.photoUrl.orEmpty(),
                        isOnline = true,
                        size = 86.dp
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Change profile photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Text(
                    text = privateUser?.displayName?.ifBlank { "Pulse User" } ?: "Pulse User",
                    style = MaterialTheme.typography.titleLarge
                )
                if (!privateUser?.username.isNullOrBlank()) {
                    Text(
                        text = "@${privateUser?.username.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!privateUser?.phoneNumber.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneAndroid,
                            contentDescription = "Verified phone number",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = privateUser?.phoneNumber.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!privateUser?.email.isNullOrBlank()) {
                    Text(
                        text = privateUser?.email.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Editable Profile Fields
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio / Status") },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_bio_input")
                )
                Button(
                    onClick = { onSaveProfile(displayName, bio, pendingAvatarBitmap) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_profile_button")
                ) {
                    Text("Save Profile")
                }
            }
        }

        // Privacy & Security Hub Entry
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPrivacySecurity)
                .testTag("open_privacy_security_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Privacy and Security",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy & Security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "E2EE • 2FA ${if (twoStepEnabled) "On" else "Off"} • $lockedChatsCount locked • $blockedUsersCount blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Open Privacy & Security"
                )
            }
        }

        // Username & Password Credentials Management
        AccountPasswordSecurityCard(
            username = privateUser?.username.orEmpty(),
            isProcessing = isPasswordActionLoading,
            onOpenChangePassword = {
                passwordActionError = null
                showChangePasswordDialog = true
            },
            onOpenLinkUsername = {
                passwordActionError = null
                showLinkUsernameDialog = true
            }
        )

        // Preferences
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Dark Theme", style = MaterialTheme.typography.titleSmall)
                            Text("Midnight Electric & Teal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = privateUser?.darkModeEnabled ?: true,
                        onCheckedChange = onToggleDarkMode,
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Message Alerts", style = MaterialTheme.typography.titleSmall)
                            Text("Local alerts for incoming chats", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = privateUser?.notificationsEnabled ?: true,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onToggleNotifications(true)
                                } else {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                onToggleNotifications(enabled)
                            }
                        }
                    )
                }
            }
        }

        // Sign Out
        OutlinedButton(
            onClick = {
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope,
                    onBeforeSignOut = onBeforeSignOut,
                    onSignOutComplete = onSignedOut
                )
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            isLoading = isPasswordActionLoading,
            errorMessage = passwordActionError,
            onDismiss = {
                showChangePasswordDialog = false
                passwordActionError = null
            },
            onConfirm = { currentPw, newPw ->
                passwordActionError = null
                isPasswordActionLoading = true
                onChangePassword(currentPw, newPw) { result ->
                    isPasswordActionLoading = false
                    result.fold(
                        onSuccess = { showChangePasswordDialog = false },
                        onFailure = { err -> passwordActionError = err.message ?: "Unable to update password." }
                    )
                }
            }
        )
    }

    if (showLinkUsernameDialog) {
        LinkUsernameDialog(
            initialEmail = privateUser?.email.orEmpty(),
            isLoading = isPasswordActionLoading,
            errorMessage = passwordActionError,
            onDismiss = {
                showLinkUsernameDialog = false
                passwordActionError = null
            },
            onConfirm = { newUsername, newPw, recEmail ->
                passwordActionError = null
                isPasswordActionLoading = true
                onLinkUsername(newUsername, newPw, recEmail) { result ->
                    isPasswordActionLoading = false
                    result.fold(
                        onSuccess = { showLinkUsernameDialog = false },
                        onFailure = { err -> passwordActionError = err.message ?: "Unable to link username." }
                    )
                }
            }
        )
    }
}
