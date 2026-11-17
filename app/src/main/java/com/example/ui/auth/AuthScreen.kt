package com.example.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.auth.AuthSessionStore
import com.example.data.auth.MongoAuthRepository
import com.example.data.auth.PulseAuthService
import com.example.data.repository.ChatRepository
import com.example.util.MediaUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

internal sealed interface AuthStep {
    data object UsernameLogin : AuthStep
    data object UsernameSignUp : AuthStep
    data object ForgotPassword : AuthStep
    data object PhoneEntry : AuthStep

    data class OtpVerification(
        val e164Phone: String,
        val displayPhone: String,
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken,
        val sentAtMillis: Long,
        val cooldownSeconds: Int
    ) : AuthStep

    data class ProfileSetup(
        val uid: String,
        val e164Phone: String
    ) : AuthStep

    data class VerifiedSuccess(
        val title: String,
        val subtitle: String
    ) : AuthStep
}

@Composable
fun AuthScreen(
    initialProfileSetupUid: String? = null,
    initialProfileSetupPhone: String? = null,
    authService: PulseAuthService? = null,
    enableAutoGoogleSignIn: Boolean = true,
    onHoldAuthNavigation: (Boolean) -> Unit = {},
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val chatRepositoryLazy = remember(context) { lazy { ChatRepository(context) } }
    val sessionStore = remember(context) { AuthSessionStore(context) }
    val sparkAuth = remember(authService, context) { authService ?: MongoAuthRepository(context) }
    val rateLimiter = remember { OtpRateLimiter() }

    var authStep by remember(initialProfileSetupUid) {
        mutableStateOf<AuthStep>(
            if (!initialProfileSetupUid.isNullOrBlank()) {
                AuthStep.ProfileSetup(
                    uid = initialProfileSetupUid,
                    e164Phone = initialProfileSetupPhone.orEmpty()
                )
            } else {
                AuthStep.UsernameLogin
            }
        )
    }

    var isCheckingAutoSignIn by remember(initialProfileSetupUid, enableAutoGoogleSignIn) {
        mutableStateOf(enableAutoGoogleSignIn && initialProfileSetupUid.isNullOrBlank())
    }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var isUsernameAuthLoading by remember { mutableStateOf(false) }
    var isPhoneSending by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }

    // Username Login state
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Sign Up state
    var signUpFullName by remember { mutableStateOf("") }
    var signUpUsername by remember { mutableStateOf("") }
    var signUpRecoveryEmail by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpConfirmPassword by remember { mutableStateOf("") }
    var usernameAvailability by remember { mutableStateOf(UsernameAvailabilityState.Idle) }
    var usernameHint by remember { mutableStateOf("") }

    // Forgot Password state (MongoDB recovery email + 6-digit reset code)
    var forgotUsername by remember { mutableStateOf("") }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotResetCode by remember { mutableStateOf("") }
    var forgotNewPassword by remember { mutableStateOf("") }
    var forgotConfirmPassword by remember { mutableStateOf("") }
    var forgotCodeSent by remember { mutableStateOf(false) }
    var forgotInfoMessage by remember { mutableStateOf<String?>(null) }

    // Phone auth state
    var selectedCountry by remember { mutableStateOf(SupportedCountries.defaultCountry) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var phoneInput by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var cooldownRemainingSec by remember { mutableIntStateOf(0) }
    var isOtpExpired by remember { mutableStateOf(false) }

    LaunchedEffect(initialProfileSetupUid) {
        if (initialProfileSetupUid.isNullOrBlank()) {
            rememberMe = sessionStore.isRememberMeEnabled()
            val savedUsername = sessionStore.getRememberedUsername()
            if (savedUsername.isNotBlank() && loginUsername.isBlank()) {
                loginUsername = savedUsername
            }
        }
    }

    LaunchedEffect(initialProfileSetupUid, enableAutoGoogleSignIn) {
        if (enableAutoGoogleSignIn && initialProfileSetupUid.isNullOrBlank()) {
            val savedRememberMe = sessionStore.isRememberMeEnabled()
            if (!savedRememberMe && Firebase.auth.currentUser == null) {
                isCheckingAutoSignIn = false
            } else {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                        AuthSessionStore.markSessionActiveInProcess()
                        isCheckingAutoSignIn = false
                        onHoldAuthNavigation(false)
                        onAuthSuccess()
                    },
                    onUnauthenticated = {
                        isCheckingAutoSignIn = false
                    },
                    scope = scope
                )
            }
        } else {
            isCheckingAutoSignIn = false
        }
    }

    // Debounced live username availability check during Sign Up
    LaunchedEffect(authStep, signUpUsername) {
        if (authStep != AuthStep.UsernameSignUp) return@LaunchedEffect
        val raw = signUpUsername
        if (raw.isBlank()) {
            usernameAvailability = UsernameAvailabilityState.Idle
            usernameHint = ""
            return@LaunchedEffect
        }
        val localCheck = AuthValidators.validateUsername(raw)
        if (!localCheck.valid) {
            usernameAvailability = UsernameAvailabilityState.Invalid
            usernameHint = localCheck.error.orEmpty()
            return@LaunchedEffect
        }
        usernameAvailability = UsernameAvailabilityState.Checking
        usernameHint = "Checking availability..."
        delay(350L)
        val res = sparkAuth.checkUsernameAvailability(localCheck.normalizedValue)
        res.onSuccess { status ->
            if (!status.valid) {
                usernameAvailability = UsernameAvailabilityState.Invalid
                usernameHint = status.reason.ifBlank { "Invalid username format." }
            } else if (status.available) {
                usernameAvailability = UsernameAvailabilityState.Available
                usernameHint = "@${status.normalizedUsername} is available"
            } else {
                usernameAvailability = UsernameAvailabilityState.Taken
                usernameHint = status.reason.ifBlank { "This username is already taken." }
            }
        }.onFailure {
            usernameAvailability = UsernameAvailabilityState.Idle
            usernameHint = ""
        }
    }

    LaunchedEffect(authStep) {
        val otpStep = authStep as? AuthStep.OtpVerification ?: return@LaunchedEffect
        cooldownRemainingSec = otpStep.cooldownSeconds
        isOtpExpired = false
        while (true) {
            val elapsedMs = System.currentTimeMillis() - otpStep.sentAtMillis
            if (elapsedMs >= OtpRateLimiter.OTP_EXPIRY_MILLIS) {
                isOtpExpired = true
            }
            if (cooldownRemainingSec > 0) {
                cooldownRemainingSec -= 1
            }
            delay(1000L)
        }
    }

    LaunchedEffect(authStep) {
        if (authStep is AuthStep.VerifiedSuccess) {
            delay(950L)
            onHoldAuthNavigation(false)
            onAuthSuccess()
        }
    }

    suspend fun handleAuthenticatedPhoneUser(uid: String, phoneNumber: String) {
        AuthSessionStore.markSessionActiveInProcess()
        val profileExists = chatRepositoryLazy.value.doesUserProfileExist(uid)
        if (profileExists) {
            chatRepositoryLazy.value.syncExistingPhoneUser(phoneNumber)
            authStep = AuthStep.VerifiedSuccess(
                title = "Welcome back!",
                subtitle = "Phone number verified. Restoring your chats and contacts..."
            )
        } else {
            authStep = AuthStep.ProfileSetup(uid = uid, e164Phone = phoneNumber)
        }
    }

    fun submitUsernameLogin() {
        errorMessage = null
        infoMessage = null
        val normalized = AuthValidators.normalizeUsername(loginUsername)
        if (normalized.isEmpty()) {
            errorMessage = "Enter your username."
            return
        }
        if (loginPassword.isEmpty()) {
            errorMessage = "Enter your password."
            return
        }
        val formatCheck = AuthValidators.validateUsername(normalized)
        if (!formatCheck.valid) {
            errorMessage = formatCheck.error
            return
        }

        isUsernameAuthLoading = true
        onHoldAuthNavigation(true)
        scope.launch {
            sparkAuth.loginWithUsername(normalized, loginPassword).fold(
                onSuccess = { res ->
                    AuthSessionStore.markSessionActiveInProcess()
                    sessionStore.saveLoginPreference(rememberMe, res.username)
                    loginPassword = ""
                    isUsernameAuthLoading = false
                    authStep = AuthStep.VerifiedSuccess(
                        title = "Welcome back, ${res.displayName}!",
                        subtitle = "Signed in as @${res.username}. Opening your encrypted chats..."
                    )
                },
                onFailure = { err ->
                    AuthSessionStore.clearInProcessSession()
                    onHoldAuthNavigation(false)
                    isUsernameAuthLoading = false
                    errorMessage = err.message ?: "Invalid username or password."
                }
            )
        }
    }

    fun submitUsernameSignUp() {
        errorMessage = null
        val nameCheck = AuthValidators.validateFullName(signUpFullName)
        if (!nameCheck.valid) {
            errorMessage = nameCheck.error
            return
        }
        val userCheck = AuthValidators.validateUsername(signUpUsername)
        if (!userCheck.valid) {
            errorMessage = userCheck.error
            return
        }
        if (usernameAvailability == UsernameAvailabilityState.Taken) {
            errorMessage = "This username is already taken. Choose another one."
            return
        }
        val emailCheck = AuthValidators.validateEmail(signUpRecoveryEmail, required = true)
        if (!emailCheck.valid) {
            errorMessage = emailCheck.error
            return
        }
        val pwCheck = AuthValidators.evaluatePasswordStrength(signUpPassword)
        if (!pwCheck.isValid) {
            errorMessage = pwCheck.error
            return
        }
        val confirmCheck = AuthValidators.validateConfirmPassword(signUpPassword, signUpConfirmPassword)
        if (!confirmCheck.valid) {
            errorMessage = confirmCheck.error
            return
        }

        isUsernameAuthLoading = true
        onHoldAuthNavigation(true)
        scope.launch {
            sparkAuth.register(
                username = userCheck.normalizedValue,
                password = signUpPassword,
                displayName = nameCheck.normalizedValue,
                recoveryEmail = emailCheck.normalizedValue
            ).fold(
                onSuccess = { regRes ->
                    AuthSessionStore.markSessionActiveInProcess()
                    sessionStore.saveLoginPreference(rememberMe, regRes.username)
                    signUpPassword = ""
                    signUpConfirmPassword = ""
                    isUsernameAuthLoading = false
                    authStep = AuthStep.VerifiedSuccess(
                        title = "Account Created!",
                        subtitle = "Welcome to PulseChat, ${regRes.displayName} (@${regRes.username})."
                    )
                },
                onFailure = { err ->
                    AuthSessionStore.clearInProcessSession()
                    onHoldAuthNavigation(false)
                    isUsernameAuthLoading = false
                    errorMessage = err.message ?: "Unable to create account."
                }
            )
        }
    }

    fun sendPasswordResetEmail() {
        errorMessage = null
        forgotInfoMessage = null
        val userCheck = AuthValidators.validateUsername(forgotUsername)
        if (!userCheck.valid) {
            errorMessage = userCheck.error
            return
        }
        val emailCheck = AuthValidators.validateEmail(forgotEmail, required = true)
        if (!emailCheck.valid) {
            errorMessage = emailCheck.error
            return
        }
        isUsernameAuthLoading = true
        scope.launch {
            sparkAuth.sendPasswordReset(
                username = userCheck.normalizedValue,
                recoveryEmail = emailCheck.normalizedValue
            ).fold(
                onSuccess = { msg ->
                    isUsernameAuthLoading = false
                    forgotCodeSent = true
                    forgotInfoMessage = msg
                },
                onFailure = { err ->
                    isUsernameAuthLoading = false
                    errorMessage = err.message ?: "Unable to send password reset code."
                }
            )
        }
    }

    fun confirmPasswordResetWithCode() {
        errorMessage = null
        val userCheck = AuthValidators.validateUsername(forgotUsername)
        if (!userCheck.valid) {
            errorMessage = userCheck.error
            return
        }
        val emailCheck = AuthValidators.validateEmail(forgotEmail, required = true)
        if (!emailCheck.valid) {
            errorMessage = emailCheck.error
            return
        }
        if (!forgotResetCode.trim().matches(Regex("^\\d{6}$"))) {
            errorMessage = "Enter the 6-digit verification code."
            return
        }
        val pwCheck = AuthValidators.evaluatePasswordStrength(forgotNewPassword)
        if (!pwCheck.isValid) {
            errorMessage = pwCheck.error
            return
        }
        val confirmCheck = AuthValidators.validateConfirmPassword(forgotNewPassword, forgotConfirmPassword)
        if (!confirmCheck.valid) {
            errorMessage = confirmCheck.error
            return
        }
        isUsernameAuthLoading = true
        scope.launch {
            sparkAuth.confirmPasswordReset(
                username = userCheck.normalizedValue,
                recoveryEmail = emailCheck.normalizedValue,
                resetCode = forgotResetCode.trim(),
                newPassword = forgotNewPassword
            ).fold(
                onSuccess = { msg ->
                    isUsernameAuthLoading = false
                    forgotCodeSent = false
                    forgotResetCode = ""
                    forgotNewPassword = ""
                    forgotConfirmPassword = ""
                    forgotInfoMessage = null
                    loginUsername = userCheck.normalizedValue
                    loginPassword = ""
                    infoMessage = msg
                    authStep = AuthStep.UsernameLogin
                },
                onFailure = { err ->
                    isUsernameAuthLoading = false
                    errorMessage = err.message ?: "Unable to reset password."
                }
            )
        }
    }

    fun requestLoginPhoneOtp(isResend: Boolean) {
        errorMessage = null
        val validation = PhoneNumberValidator.validate(selectedCountry, phoneInput)
        if (validation is PhoneValidationResult.Invalid) {
            errorMessage = validation.errorMessage
            return
        }
        val validPhone = validation as PhoneValidationResult.Valid

        when (val limit = rateLimiter.checkCanRequestOtp(validPhone.e164)) {
            is RateLimitDecision.Blocked -> {
                errorMessage = limit.reason
                return
            }
            is RateLimitDecision.Allowed -> Unit
        }

        val currentOtpStep = authStep as? AuthStep.OtpVerification
        val tokenToUse = if (isResend) currentOtpStep?.resendToken else null

        isPhoneSending = true
        onHoldAuthNavigation(true)

        PhoneAuthHelper.sendOtpCode(
            context = context,
            e164PhoneNumber = validPhone.e164,
            resendToken = tokenToUse
        ) { outcome ->
            isPhoneSending = false
            when (outcome) {
                is PhoneOtpSendOutcome.CodeSent -> {
                    val cooldown = rateLimiter.recordOtpSent(validPhone.e164)
                    otpCode = ""
                    isOtpExpired = false
                    authStep = AuthStep.OtpVerification(
                        e164Phone = validPhone.e164,
                        displayPhone = validPhone.displayFormatted,
                        verificationId = outcome.verificationId,
                        resendToken = outcome.resendToken,
                        sentAtMillis = System.currentTimeMillis(),
                        cooldownSeconds = cooldown
                    )
                }
                is PhoneOtpSendOutcome.AutoVerified -> {
                    rateLimiter.recordOtpSent(validPhone.e164)
                    scope.launch {
                        handleAuthenticatedPhoneUser(outcome.uid, outcome.phoneNumber)
                    }
                }
                is PhoneOtpSendOutcome.Failure -> {
                    if (authStep is AuthStep.PhoneEntry) onHoldAuthNavigation(false)
                    errorMessage = outcome.message
                }
            }
        }
    }

    fun submitLoginPhoneOtp() {
        val step = authStep as? AuthStep.OtpVerification ?: return
        if (isVerifyingOtp) return
        if (isOtpExpired) {
            errorMessage = "Your verification code has expired. Tap Resend OTP."
            return
        }
        if (rateLimiter.isSessionLockedByFailedAttempts()) {
            errorMessage = "Too many incorrect attempts. Request a fresh OTP code."
            return
        }
        if (otpCode.length != OtpRateLimiter.OTP_LENGTH) {
            errorMessage = "Enter the complete 6-digit verification code."
            return
        }

        errorMessage = null
        isVerifyingOtp = true
        onHoldAuthNavigation(true)
        scope.launch {
            when (
                val outcome = PhoneAuthHelper.verifyOtpCode(
                    verificationId = step.verificationId,
                    smsCode = otpCode,
                    fallbackPhoneNumber = step.e164Phone
                )
            ) {
                is PhoneOtpVerifyOutcome.Success -> {
                    rateLimiter.resetSessionAttempts()
                    isVerifyingOtp = false
                    handleAuthenticatedPhoneUser(outcome.uid, outcome.phoneNumber)
                }
                is PhoneOtpVerifyOutcome.Failure -> {
                    isVerifyingOtp = false
                    if (outcome.isExpired) isOtpExpired = true
                    val remaining = rateLimiter.recordFailedOtpVerification()
                    errorMessage = if (remaining == 0) {
                        "Too many incorrect OTP attempts. Request a new code."
                    } else if (!outcome.isExpired && !outcome.isRateLimited) {
                        "${outcome.message} ($remaining attempts remaining)"
                    } else {
                        outcome.message
                    }
                }
            }
        }
    }

    if (authStep != AuthStep.UsernameLogin && authStep !is AuthStep.VerifiedSuccess && authStep !is AuthStep.ProfileSetup) {
        BackHandler(enabled = !isUsernameAuthLoading && !isPhoneSending && !isVerifyingOtp) {
            errorMessage = null
            forgotInfoMessage = null
            onHoldAuthNavigation(false)
            authStep = when (authStep) {
                is AuthStep.OtpVerification -> AuthStep.PhoneEntry
                else -> AuthStep.UsernameLogin
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // App Hero Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(26.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_welcome_hero),
                    contentDescription = "PulseChat brand artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.90f)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "PulseChat logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "PulseChat",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "End-to-End Encrypted Messaging",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = authStep,
                label = "auth_step_transition"
            ) { step ->
                when (step) {
                    AuthStep.UsernameLogin -> {
                        val anyBusy = isUsernameAuthLoading || isGoogleLoading || isPhoneSending || isCheckingAutoSignIn
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            UsernameLoginCard(
                                username = loginUsername,
                                password = loginPassword,
                                rememberMe = rememberMe,
                                isLoading = isUsernameAuthLoading,
                                errorMessage = errorMessage,
                                infoMessage = infoMessage,
                                onUsernameChange = {
                                    loginUsername = it
                                    errorMessage = null
                                },
                                onPasswordChange = {
                                    loginPassword = it
                                    errorMessage = null
                                },
                                onRememberMeChange = { rememberMe = it },
                                onLoginClick = { submitUsernameLogin() },
                                onForgotPasswordClick = {
                                    errorMessage = null
                                    infoMessage = null
                                    forgotUsername = loginUsername
                                    authStep = AuthStep.ForgotPassword
                                },
                                onCreateAccountClick = {
                                    errorMessage = null
                                    infoMessage = null
                                    authStep = AuthStep.UsernameSignUp
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                )
                                Text(
                                    text = "or continue with",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        errorMessage = null
                                        authStep = AuthStep.PhoneEntry
                                    },
                                    enabled = !anyBusy,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("phone_auth_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Phone", fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        errorMessage = null
                                        isGoogleLoading = true
                                        onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = credentialManager,
                                            onAuthSuccess = {
                                                AuthSessionStore.markSessionActiveInProcess()
                                                isGoogleLoading = false
                                                onHoldAuthNavigation(false)
                                                onAuthSuccess()
                                            },
                                            onAuthError = { msg ->
                                                isGoogleLoading = false
                                                errorMessage = msg
                                            },
                                            scope = scope,
                                            onAuthCancelled = {
                                                isGoogleLoading = false
                                            }
                                        )
                                    },
                                    enabled = !anyBusy,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("google_sign_in_button")
                                ) {
                                    if (isGoogleLoading || isCheckingAutoSignIn) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isCheckingAutoSignIn) "Checking..." else "Google...",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    } else {
                                        Text("Google", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    AuthStep.UsernameSignUp -> {
                        UsernameSignUpCard(
                            fullName = signUpFullName,
                            username = signUpUsername,
                            recoveryEmail = signUpRecoveryEmail,
                            password = signUpPassword,
                            confirmPassword = signUpConfirmPassword,
                            usernameAvailability = usernameAvailability,
                            usernameHint = usernameHint,
                            isLoading = isUsernameAuthLoading,
                            errorMessage = errorMessage,
                            onFullNameChange = {
                                signUpFullName = it
                                errorMessage = null
                            },
                            onUsernameChange = {
                                signUpUsername = it
                                errorMessage = null
                            },
                            onRecoveryEmailChange = {
                                signUpRecoveryEmail = it
                                errorMessage = null
                            },
                            onPasswordChange = {
                                signUpPassword = it
                                errorMessage = null
                            },
                            onConfirmPasswordChange = {
                                signUpConfirmPassword = it
                                errorMessage = null
                            },
                            onSubmitSignUp = { submitUsernameSignUp() },
                            onBackToLogin = {
                                errorMessage = null
                                authStep = AuthStep.UsernameLogin
                            }
                        )
                    }

                    AuthStep.ForgotPassword -> {
                        ForgotPasswordCard(
                            username = forgotUsername,
                            email = forgotEmail,
                            resetCode = forgotResetCode,
                            newPassword = forgotNewPassword,
                            confirmPassword = forgotConfirmPassword,
                            isCodeSent = forgotCodeSent,
                            isLoading = isUsernameAuthLoading,
                            errorMessage = errorMessage,
                            infoMessage = forgotInfoMessage,
                            onUsernameChange = {
                                forgotUsername = it
                                errorMessage = null
                            },
                            onEmailChange = {
                                forgotEmail = it
                                errorMessage = null
                            },
                            onResetCodeChange = {
                                forgotResetCode = it
                                errorMessage = null
                            },
                            onNewPasswordChange = {
                                forgotNewPassword = it
                                errorMessage = null
                            },
                            onConfirmPasswordChange = {
                                forgotConfirmPassword = it
                                errorMessage = null
                            },
                            onSendResetEmail = { sendPasswordResetEmail() },
                            onConfirmResetPassword = { confirmPasswordResetWithCode() },
                            onBack = {
                                errorMessage = null
                                forgotInfoMessage = null
                                forgotCodeSent = false
                                forgotResetCode = ""
                                forgotNewPassword = ""
                                forgotConfirmPassword = ""
                                onHoldAuthNavigation(false)
                                authStep = AuthStep.UsernameLogin
                            }
                        )
                    }

                    AuthStep.PhoneEntry -> {
                        PhoneEntryCard(
                            selectedCountry = selectedCountry,
                            phoneInput = phoneInput,
                            isPhoneSending = isPhoneSending,
                            isOtherBusy = isGoogleLoading || isCheckingAutoSignIn,
                            errorMessage = errorMessage,
                            onBackToLogin = {
                                errorMessage = null
                                authStep = AuthStep.UsernameLogin
                            },
                            onOpenCountryPicker = { showCountryPicker = true },
                            onPhoneInputChange = { raw ->
                                phoneInput = PhoneNumberValidator.normalizeInputDigits(raw).take(16)
                                errorMessage = null
                            },
                            onSendOtpClick = { requestLoginPhoneOtp(isResend = false) }
                        )
                    }

                    is AuthStep.OtpVerification -> {
                        PhoneOtpVerificationCard(
                            displayPhone = step.displayPhone,
                            otpCode = otpCode,
                            isOtpExpired = isOtpExpired,
                            isVerifyingOtp = isVerifyingOtp,
                            isPhoneSending = isPhoneSending,
                            cooldownRemainingSec = cooldownRemainingSec,
                            isSessionLocked = rateLimiter.isSessionLockedByFailedAttempts(),
                            errorMessage = errorMessage,
                            onBackToPhoneEntry = {
                                errorMessage = null
                                otpCode = ""
                                onHoldAuthNavigation(false)
                                authStep = AuthStep.PhoneEntry
                            },
                            onOtpChange = {
                                otpCode = it
                                errorMessage = null
                            },
                            onVerifyClick = { submitLoginPhoneOtp() },
                            onResendClick = { requestLoginPhoneOtp(isResend = true) }
                        )
                    }

                    is AuthStep.ProfileSetup -> {
                        PhoneProfileSetupCard(
                            verifiedPhoneNumber = step.e164Phone.ifBlank { "Phone Number" },
                            isSubmitting = isSavingProfile,
                            errorMessage = errorMessage,
                            onCompleteRegistration = { name, bio, avatarBitmap ->
                                errorMessage = null
                                isSavingProfile = true
                                onHoldAuthNavigation(true)
                                scope.launch {
                                    try {
                                        val avatarB64 = avatarBitmap?.let {
                                            MediaUtils.encodeBitmapToBase64(it, maxDimension = 320, quality = 70)
                                        }
                                        val user = Firebase.auth.currentUser
                                        if (user != null) {
                                            val profileReq = UserProfileChangeRequest.Builder()
                                                .setDisplayName(name)
                                                .build()
                                            runCatching { user.updateProfile(profileReq).await() }
                                        }
                                        chatRepositoryLazy.value.registerPhoneUserProfile(
                                            displayName = name,
                                            phoneNumber = step.e164Phone,
                                            bio = bio,
                                            avatarData = avatarB64
                                        ).getOrThrow()

                                        isSavingProfile = false
                                        authStep = AuthStep.VerifiedSuccess(
                                            title = "Account Created!",
                                            subtitle = "Welcome to PulseChat, $name. Opening your chats..."
                                        )
                                    } catch (e: Exception) {
                                        isSavingProfile = false
                                        errorMessage = e.localizedMessage ?: "Failed to create profile. Please try again."
                                    }
                                }
                            }
                        )
                    }

                    is AuthStep.VerifiedSuccess -> {
                        AuthVerificationSuccessCard(
                            title = step.title,
                            subtitle = step.subtitle
                        )
                    }
                }
            }
        }
    }

    if (showCountryPicker) {
        CountryCodePickerSheet(
            selectedCountry = selectedCountry,
            onSelect = { country ->
                selectedCountry = country
                errorMessage = null
            },
            onDismiss = { showCountryPicker = false }
        )
    }
}
