package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

enum class UsernameAvailabilityState {
    Idle,
    Checking,
    Available,
    Taken,
    Invalid
}

@Composable
fun AuthBannerMessage(
    message: String?,
    isError: Boolean,
    tag: String
) {
    AnimatedVisibility(visible = !message.isNullOrBlank()) {
        val bg = if (isError) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        }
        val fg = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
        Surface(
            color = bg,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = message.orEmpty(),
                    color = fg,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun UsernameLoginCard(
    username: String,
    password: String,
    rememberMe: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onCreateAccountClick: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Welcome Back",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sign in with your PulseChat username and password",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                placeholder = { Text("e.g. alex_dev") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AlternateEmail,
                        contentDescription = null
                    )
                },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("login_password_visibility_toggle")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !isLoading) { onRememberMeChange(!rememberMe) }
                        .heightIn(min = 48.dp)
                        .padding(end = 8.dp)
                        .testTag("remember_me_checkbox")
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = if (!isLoading) onRememberMeChange else null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    Text(
                        text = "Remember Me",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = onForgotPasswordClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("forgot_password_button")
                ) {
                    Text(
                        text = "Forgot Password?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AuthBannerMessage(
                message = infoMessage,
                isError = false,
                tag = "auth_info_banner"
            )

            AuthBannerMessage(
                message = errorMessage,
                isError = true,
                tag = "auth_error_banner"
            )

            Button(
                onClick = onLoginClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("login_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Signing In...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Log In", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = onCreateAccountClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("go_to_signup_button")
            ) {
                Text(
                    text = "Create New Account",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun UsernameSignUpCard(
    fullName: String,
    username: String,
    recoveryEmail: String,
    password: String,
    confirmPassword: String,
    usernameAvailability: UsernameAvailabilityState,
    usernameHint: String,
    isLoading: Boolean,
    errorMessage: String?,
    onFullNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onRecoveryEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmitSignUp: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    val nameCheck = remember(fullName) {
        if (fullName.isEmpty()) null else AuthValidators.validateFullName(fullName)
    }
    val emailCheck = remember(recoveryEmail) {
        if (recoveryEmail.isEmpty()) null else AuthValidators.validateEmail(recoveryEmail, required = true)
    }
    val strength = remember(password) { AuthValidators.evaluatePasswordStrength(password) }
    val confirmCheck = remember(password, confirmPassword) {
        if (confirmPassword.isEmpty()) null else AuthValidators.validateConfirmPassword(password, confirmPassword)
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBackToLogin,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("signup_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Login"
                    )
                }
                Column {
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Reserve your unique username on PulseChat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = fullName,
                onValueChange = onFullNameChange,
                label = { Text("Full Name") },
                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                isError = nameCheck != null && !nameCheck.valid,
                supportingText = nameCheck?.takeIf { !it.valid }?.error?.let { err -> { Text(err) } },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_fullname_input")
            )

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Unique Username") },
                placeholder = { Text("lowercase, numbers, . or _") },
                leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                trailingIcon = {
                    when (usernameAvailability) {
                        UsernameAvailabilityState.Checking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        UsernameAvailabilityState.Available -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Username available",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        UsernameAvailabilityState.Taken,
                        UsernameAvailabilityState.Invalid -> {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = "Username unavailable",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        UsernameAvailabilityState.Idle -> Unit
                    }
                },
                isError = usernameAvailability == UsernameAvailabilityState.Taken ||
                    usernameAvailability == UsernameAvailabilityState.Invalid,
                supportingText = {
                    val statusColor = when (usernameAvailability) {
                        UsernameAvailabilityState.Available -> MaterialTheme.colorScheme.tertiary
                        UsernameAvailabilityState.Taken,
                        UsernameAvailabilityState.Invalid -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    if (usernameHint.isNotBlank()) {
                        Text(
                            text = usernameHint,
                            color = statusColor,
                            modifier = Modifier.testTag("username_availability_status")
                        )
                    }
                },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_username_input")
            )

            OutlinedTextField(
                value = recoveryEmail,
                onValueChange = onRecoveryEmailChange,
                label = { Text("Recovery Email") },
                placeholder = { Text("you@example.com") },
                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                isError = emailCheck != null && !emailCheck.valid,
                supportingText = {
                    val err = emailCheck?.takeIf { !it.valid }?.error
                    Text(
                        text = err ?: "Used only to verify your account and reset forgotten passwords."
                    )
                },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_recovery_email_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("signup_password_visibility_toggle")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                isError = password.isNotEmpty() && !strength.isValid,
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_password_input")
            )

            AnimatedVisibility(visible = password.isNotEmpty()) {
                PasswordStrengthMeter(evaluation = strength)
            }

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Confirm Password") },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { confirmVisible = !confirmVisible },
                        modifier = Modifier.testTag("signup_confirm_visibility_toggle")
                    ) {
                        Icon(
                            imageVector = if (confirmVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (confirmVisible) "Hide password" else "Show password"
                        )
                    }
                },
                isError = confirmCheck != null && !confirmCheck.valid,
                supportingText = confirmCheck?.takeIf { !it.valid }?.error?.let { err -> { Text(err) } },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmitSignUp() }),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_confirm_password_input")
            )

            AuthBannerMessage(
                message = errorMessage,
                isError = true,
                tag = "auth_error_banner"
            )

            Button(
                onClick = onSubmitSignUp,
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("signup_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Creating Account...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Create Account", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PasswordStrengthMeter(evaluation: PasswordStrengthEvaluation) {
    val progress by animateFloatAsState(
        targetValue = (evaluation.score / 4f).coerceIn(0.12f, 1f),
        label = "pw_strength_progress"
    )
    val barColor by animateColorAsState(
        targetValue = when {
            evaluation.score <= 1 -> MaterialTheme.colorScheme.error
            evaluation.score == 2 -> MaterialTheme.colorScheme.secondary
            evaluation.score == 3 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.tertiary
        },
        label = "pw_strength_color"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Password strength",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = evaluation.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = barColor,
                modifier = Modifier.testTag("password_strength_label")
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        if (!evaluation.isValid && !evaluation.error.isNullOrBlank()) {
            Text(
                text = evaluation.error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
