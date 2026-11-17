package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ForgotPasswordCard(
    username: String,
    email: String,
    resetCode: String = "",
    newPassword: String = "",
    confirmPassword: String = "",
    isCodeSent: Boolean = false,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onResetCodeChange: (String) -> Unit = {},
    onNewPasswordChange: (String) -> Unit = {},
    onConfirmPasswordChange: (String) -> Unit = {},
    onSendResetEmail: () -> Unit,
    onConfirmResetPassword: () -> Unit = {},
    onBack: () -> Unit
) {
    var newPwVisible by remember { mutableStateOf(false) }
    var confirmPwVisible by remember { mutableStateOf(false) }
    val strength = remember(newPassword) { AuthValidators.evaluatePasswordStrength(newPassword) }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("forgot_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Login"
                    )
                }
                Column {
                    Text(
                        text = "Reset Password",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isCodeSent) {
                            "Enter the 6-digit verification code sent to your recovery email"
                        } else {
                            "Receive a verification code at your recovery email"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                singleLine = true,
                enabled = !isLoading && !isCodeSent,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_username_input")
            )

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Recovery Email") },
                placeholder = { Text("you@example.com") },
                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                singleLine = true,
                enabled = !isLoading && !isCodeSent,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = if (isCodeSent) ImeAction.Next else ImeAction.Done
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_email_input")
            )

            AnimatedVisibility(visible = isCodeSent) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = resetCode,
                        onValueChange = { raw ->
                            onResetCodeChange(raw.filter { it.isDigit() }.take(6))
                        },
                        label = { Text("6-Digit Verification Code") },
                        leadingIcon = { Icon(Icons.Rounded.Password, contentDescription = null) },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_reset_code_input")
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = onNewPasswordChange,
                        label = { Text("New Password") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { newPwVisible = !newPwVisible },
                                modifier = Modifier.testTag("forgot_new_password_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (newPwVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (newPwVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = if (newPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_new_password_input")
                    )

                    if (newPassword.isNotEmpty()) {
                        PasswordStrengthMeter(evaluation = strength)
                    }

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        label = { Text("Confirm New Password") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { confirmPwVisible = !confirmPwVisible },
                                modifier = Modifier.testTag("forgot_confirm_password_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (confirmPwVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (confirmPwVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = if (confirmPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_confirm_password_input")
                    )
                }
            }

            AuthBannerMessage(
                message = infoMessage,
                isError = false,
                tag = "forgot_info_banner"
            )

            AuthBannerMessage(
                message = errorMessage,
                isError = true,
                tag = "forgot_error_banner"
            )

            if (isCodeSent) {
                Button(
                    onClick = onConfirmResetPassword,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("forgot_confirm_reset_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Updating Password...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Reset Password", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!isCodeSent) {
                Button(
                    onClick = onSendResetEmail,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("forgot_send_reset_email_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Sending Reset Code...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Send Reset Verification Code", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                TextButton(
                    onClick = onSendResetEmail,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forgot_resend_code_button")
                ) {
                    Text("Resend Verification Code", fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedButton(
                onClick = onBack,
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("forgot_return_login_button")
            ) {
                Text("Back to Login", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AccountPasswordSecurityCard(
    username: String,
    isProcessing: Boolean,
    onOpenChangePassword: () -> Unit,
    onOpenLinkUsername: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "Account Password Security",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Username & Password",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (username.isNotBlank()) {
                            "Signed username: @$username"
                        } else {
                            "Add username & password login without changing your UID"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (username.isNotBlank()) {
                OutlinedButton(
                    onClick = onOpenChangePassword,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_change_password_dialog")
                ) {
                    Text("Change Password", fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                    onClick = onOpenLinkUsername,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_link_username_dialog")
                ) {
                    Text("Set Username & Password", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ChangePasswordDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (currentPassword: String, newPassword: String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val strength = remember(newPassword) { AuthValidators.evaluatePasswordStrength(newPassword) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        localError = null
                    },
                    label = { Text("Current Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_current_password_input")
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        localError = null
                    },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_new_password_input")
                )
                if (newPassword.isNotEmpty()) {
                    PasswordStrengthMeter(evaluation = strength)
                }
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_confirm_password_input")
                )
                val shownError = localError ?: errorMessage
                if (!shownError.isNullOrBlank()) {
                    Text(
                        text = shownError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val confirmCheck = AuthValidators.validateConfirmPassword(newPassword, confirmPassword)
                    localError = when {
                        currentPassword.isEmpty() -> "Enter your current password."
                        !strength.isValid -> strength.error
                        !confirmCheck.valid -> confirmCheck.error
                        else -> null
                    }
                    if (localError == null) {
                        onConfirm(currentPassword, newPassword)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.testTag("dialog_change_password_confirm")
            ) {
                Text(if (isLoading) "Saving..." else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LinkUsernameDialog(
    initialEmail: String = "",
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (username: String, password: String, recoveryEmail: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var recoveryEmail by remember(initialEmail) { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val strength = remember(password) { AuthValidators.evaluatePasswordStrength(password) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Set Username & Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Your existing chats, calls, and E2EE keys stay intact under your current account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        localError = null
                    },
                    label = { Text("Unique Username") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_link_username_input")
                )
                if (initialEmail.isBlank()) {
                    OutlinedTextField(
                        value = recoveryEmail,
                        onValueChange = {
                            recoveryEmail = it
                            localError = null
                        },
                        label = { Text("Recovery Email") },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_link_email_input")
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_link_password_input")
                )
                if (password.isNotEmpty()) {
                    PasswordStrengthMeter(evaluation = strength)
                }
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_link_confirm_input")
                )
                val shownError = localError ?: errorMessage
                if (!shownError.isNullOrBlank()) {
                    Text(
                        text = shownError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val userCheck = AuthValidators.validateUsername(username)
                    val emailCheck = AuthValidators.validateEmail(recoveryEmail, required = true)
                    val confirmCheck = AuthValidators.validateConfirmPassword(password, confirmPassword)
                    localError = when {
                        !userCheck.valid -> userCheck.error
                        !emailCheck.valid -> emailCheck.error
                        !strength.isValid -> strength.error
                        !confirmCheck.valid -> confirmCheck.error
                        else -> null
                    }
                    if (localError == null) {
                        onConfirm(userCheck.normalizedValue, password, emailCheck.normalizedValue)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.testTag("dialog_link_confirm")
            ) {
                Text(if (isLoading) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}
