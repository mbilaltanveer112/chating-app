package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.model.DisappearingTimerOption
import com.example.data.model.ReportCategory
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricCapability
import com.example.security.PinCryptoUtils
import java.util.Locale

fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun UnlockSecurityDialog(
    title: String,
    subtitle: String,
    biometricEnabled: Boolean,
    hasPinConfigured: Boolean,
    onVerifyPin: (String) -> Boolean,
    onCreatePin: ((String) -> Unit)? = null,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricCapability = remember(context) { BiometricAuthHelper.checkCapability(context) }
    val canUseBiometric = biometricEnabled &&
        biometricCapability == BiometricCapability.AVAILABLE &&
        activity != null

    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun launchBiometricPrompt() {
        val host = activity ?: return
        BiometricAuthHelper.authenticate(
            activity = host,
            title = title,
            subtitle = subtitle,
            onSuccess = onUnlocked,
            onError = { message ->
                errorMsg = message
            }
        )
    }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) {
            launchBiometricPrompt()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (canUseBiometric) Icons.Rounded.Fingerprint else Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (!hasPinConfigured && !canUseBiometric) "Set Chat Lock PIN" else title
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (!hasPinConfigured && !canUseBiometric) {
                        "Create a 6-digit PIN to protect your Locked Chats on devices without enrolled biometrics."
                    } else {
                        subtitle
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (canUseBiometric) {
                    OutlinedButton(
                        onClick = { launchBiometricPrompt() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("biometric_retry_button")
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Fingerprint / Biometrics")
                    }
                }

                if (hasPinConfigured || onCreatePin != null) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { raw ->
                            pinInput = raw.filter { it.isDigit() }.take(6)
                            errorMsg = null
                        },
                        label = {
                            Text(if (!hasPinConfigured) "Create 6-digit PIN" else "Enter 6-digit PIN")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_pin_input")
                    )
                }

                if (!hasPinConfigured && onCreatePin != null) {
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { raw ->
                            confirmPinInput = raw.filter { it.isDigit() }.take(6)
                            errorMsg = null
                        },
                        label = { Text("Confirm 6-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_confirm_pin_input")
                    )
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (hasPinConfigured || onCreatePin != null) {
                Button(
                    onClick = {
                        if (!hasPinConfigured && onCreatePin != null) {
                            when {
                                !PinCryptoUtils.isValidPinFormat(pinInput) ->
                                    errorMsg = "PIN must be exactly 6 digits"
                                pinInput != confirmPinInput ->
                                    errorMsg = "PINs do not match"
                                else -> {
                                    onCreatePin(pinInput)
                                    onUnlocked()
                                }
                            }
                        } else {
                            if (!PinCryptoUtils.isValidPinFormat(pinInput)) {
                                errorMsg = "Enter your 6-digit PIN"
                            } else if (onVerifyPin(pinInput)) {
                                onUnlocked()
                            } else {
                                errorMsg = "Incorrect PIN"
                            }
                        }
                    },
                    modifier = Modifier.testTag("unlock_confirm_button")
                ) {
                    Text(if (!hasPinConfigured) "Save & Unlock" else "Unlock")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TwoStepChallengeOverlay(
    recoveryEmail: String,
    onVerifyPin: (String, (Boolean, String?) -> Unit) -> Unit,
    onRecoverWithCode: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var useRecoveryMode by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = "Two-Step Verification",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Text(
                        text = "Two-Step Verification",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (useRecoveryMode) {
                            "Enter your backup recovery code to reset your PIN on this device."
                        } else {
                            "This new device requires your 6-digit Two-Step Verification PIN to access encrypted chats."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (!useRecoveryMode) {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { raw ->
                                pin = raw.filter { it.isDigit() }.take(6)
                                errorMessage = null
                            },
                            label = { Text("6-digit Security PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("two_step_challenge_pin_input")
                        )

                        Button(
                            onClick = {
                                onVerifyPin(pin) { ok, err ->
                                    if (!ok) errorMessage = err ?: "Verification failed"
                                }
                            },
                            enabled = pin.length == 6,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("two_step_challenge_verify_button")
                        ) {
                            Text("Verify Device")
                        }
                    } else {
                        if (recoveryEmail.isNotBlank()) {
                            Text(
                                text = "Recovery email on file: $recoveryEmail",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedTextField(
                            value = recoveryCode,
                            onValueChange = {
                                recoveryCode = it.uppercase(Locale.US)
                                errorMessage = null
                            },
                            label = { Text("Backup Recovery Code (e.g. ABCD-EFGH-JKLM)") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("two_step_recovery_code_input")
                        )
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { raw ->
                                newPin = raw.filter { it.isDigit() }.take(6)
                                errorMessage = null
                            },
                            label = { Text("New 6-digit PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("two_step_recovery_new_pin_input")
                        )
                        Button(
                            onClick = {
                                onRecoverWithCode(recoveryCode, newPin) { ok, err ->
                                    if (!ok) errorMessage = err ?: "Recovery failed"
                                }
                            },
                            enabled = recoveryCode.isNotBlank() && newPin.length == 6,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("two_step_recovery_submit_button")
                        ) {
                            Text("Reset PIN & Verify")
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                useRecoveryMode = !useRecoveryMode
                                errorMessage = null
                            }
                        ) {
                            Text(if (useRecoveryMode) "Use PIN instead" else "Forgot PIN? Use Recovery Code")
                        }
                        TextButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Sign Out")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TwoStepSetupDialog(
    initialRecoveryEmail: String,
    onSubmit: (pin: String, recoveryEmail: String, onRecoveryCodeReady: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var recoveryEmail by remember { mutableStateOf(initialRecoveryEmail) }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (generatedCode == null) onDismiss() else onDismiss() },
        icon = {
            Icon(
                imageVector = if (generatedCode != null) Icons.Rounded.VerifiedUser else Icons.Rounded.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                if (generatedCode != null) "Save Your Recovery Code"
                else "Enable Two-Step Verification"
            )
        },
        text = {
            if (generatedCode != null) {
                val code = generatedCode.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Store this one-time backup recovery code in a safe place. If you lose your PIN on a new device, this code lets you recover your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
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
                                text = code,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("PulseChat Recovery Code", code))
                                }
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy recovery code", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy")
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Require a 6-digit PIN whenever signing in on a new device. Stored using salted PBKDF2 hashing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { raw ->
                            pin = raw.filter { it.isDigit() }.take(6)
                            errorMsg = null
                        },
                        label = { Text("6-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("two_step_setup_pin_input")
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { raw ->
                            confirmPin = raw.filter { it.isDigit() }.take(6)
                            errorMsg = null
                        },
                        label = { Text("Confirm 6-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("two_step_setup_confirm_input")
                    )
                    OutlinedTextField(
                        value = recoveryEmail,
                        onValueChange = {
                            recoveryEmail = it
                            errorMsg = null
                        },
                        label = { Text("Recovery email (optional)") },
                        placeholder = { Text("name@example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!errorMsg.isNullOrBlank()) {
                        Text(
                            text = errorMsg.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (generatedCode != null) {
                Button(onClick = onDismiss) {
                    Text("I Saved It")
                }
            } else {
                Button(
                    onClick = {
                        when {
                            !PinCryptoUtils.isValidPinFormat(pin) -> errorMsg = "PIN must be 6 digits"
                            pin != confirmPin -> errorMsg = "PINs do not match"
                            recoveryEmail.isNotBlank() && !recoveryEmail.contains("@") ->
                                errorMsg = "Enter a valid recovery email"
                            else -> onSubmit(pin, recoveryEmail) { code ->
                                generatedCode = code
                            }
                        }
                    },
                    modifier = Modifier.testTag("two_step_setup_confirm_button")
                ) {
                    Text("Enable PIN")
                }
            }
        },
        dismissButton = {
            if (generatedCode == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDialog(
    title: String,
    targetUserName: String,
    messagePreview: String = "",
    initialCategory: ReportCategory = ReportCategory.SPAM,
    onSubmit: (category: ReportCategory, description: String, onDone: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var description by remember { mutableStateOf("") }
    var submittedReportId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (submittedReportId != null) Icons.Rounded.CheckCircle else Icons.Rounded.Report,
                contentDescription = null,
                tint = if (submittedReportId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(if (submittedReportId != null) "Report Submitted" else title)
        },
        text = {
            if (submittedReportId != null) {
                val ticket = submittedReportId.orEmpty().takeLast(6).uppercase(Locale.US)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Thank you. Your report (#$ticket) has been securely logged for moderator review.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "You can track its review status anytime in Privacy & Security → Reports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (targetUserName.isNotBlank()) {
                        Text(
                            text = "Reporting: $targetUserName",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (messagePreview.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "\"$messagePreview\"",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Text(
                        text = "Select reason",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ReportCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { if (it.length <= 1500) description = it },
                        label = { Text("Additional details (optional)") },
                        placeholder = { Text("Describe what happened...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("report_description_input")
                    )
                }
            }
        },
        confirmButton = {
            if (submittedReportId != null) {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            } else {
                Button(
                    onClick = {
                        onSubmit(selectedCategory, description) { reportId ->
                            submittedReportId = reportId
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("submit_report_button")
                ) {
                    Text("Submit Report")
                }
            }
        },
        dismissButton = {
            if (submittedReportId == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun SafetyNumberDialog(
    peerName: String,
    safetyNumber: String,
    myKeyFingerprint: String,
    myKeyVersion: Int,
    peerHasKeyBundle: Boolean,
    onRotateKeys: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.EnhancedEncryption,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("End-to-End Encryption") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Messages, images, videos, voice notes, and documents in private chats are encrypted end-to-end with ECDH P-256 + HKDF-SHA256 + AES-256-GCM before leaving your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (peerHasKeyBundle && safetyNumber.isNotBlank()) {
                    Text(
                        text = "Safety number with $peerName",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = safetyNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Peer key bundle will sync automatically once $peerName comes online with encryption enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (myKeyFingerprint.isNotBlank()) {
                    Text(
                        text = "Your identity key (v$myKeyVersion): $myKeyFingerprint",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedButton(
                    onClick = onRotateKeys,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rotate Encryption Keys")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun DisappearingTimerDialog(
    title: String,
    subtitle: String,
    currentDurationSeconds: Long,
    onSelectDuration: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DisappearingTimerOption.entries.forEach { option ->
                    val selected = option.seconds == currentDurationSeconds
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectDuration(option.seconds)
                                onDismiss()
                            }
                            .testTag("disappear_option_${option.seconds}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleSmall
                            )
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onSelectDuration(option.seconds)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun BlockUserConfirmDialog(
    displayName: String,
    onConfirmBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Block $displayName?") },
        text = {
            Text(
                text = "Blocked users cannot send you private messages or call you, and your online status and last seen will be hidden from them. You can unblock them anytime in Privacy & Security.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmBlock()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_block_button")
            ) {
                Text("Block")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
