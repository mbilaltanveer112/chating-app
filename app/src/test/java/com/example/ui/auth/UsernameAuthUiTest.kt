package com.example.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.auth.PulseAuthService
import com.example.data.auth.SparkAuthResult
import com.example.data.auth.UsernameAvailabilityResult
import com.example.ui.theme.PulseAuthTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class UsernameAuthUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakePulseAuthService : PulseAuthService {
        var lastLoginUsername: String = ""
        var lastResetUsername: String = ""
        var lastResetEmail: String = ""
        var lastResetCode: String = ""

        override suspend fun checkUsernameAvailability(rawUsername: String): Result<UsernameAvailabilityResult> =
            Result.success(
                UsernameAvailabilityResult(
                    available = true,
                    valid = true,
                    normalizedUsername = rawUsername.trim().lowercase(),
                    reason = ""
                )
            )

        override suspend fun register(
            username: String,
            password: String,
            displayName: String,
            recoveryEmail: String
        ): Result<SparkAuthResult> = Result.success(
            SparkAuthResult(
                uid = "pc_new_1",
                username = username.trim().lowercase(),
                displayName = displayName.trim(),
                email = recoveryEmail.trim().lowercase(),
                emailVerified = false,
                message = "Verification email sent to ${recoveryEmail.trim().lowercase()}."
            )
        )

        override suspend fun loginWithUsername(username: String, password: String): Result<SparkAuthResult> {
            lastLoginUsername = username.trim().lowercase()
            return Result.success(
                SparkAuthResult(
                    uid = "pc_new_1",
                    username = lastLoginUsername,
                    displayName = "New User",
                    email = "user@example.com",
                    emailVerified = true
                )
            )
        }

        override suspend fun sendPasswordReset(username: String, recoveryEmail: String): Result<String> {
            lastResetUsername = username.trim().lowercase()
            lastResetEmail = recoveryEmail.trim().lowercase()
            return Result.success(
                "Verification code sent to $lastResetEmail."
            )
        }

        override suspend fun confirmPasswordReset(
            username: String,
            recoveryEmail: String,
            resetCode: String,
            newPassword: String
        ): Result<String> {
            lastResetCode = resetCode
            return Result.success("Password reset successful. Sign in with your new password.")
        }

        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun linkUsernameCredentials(
            username: String,
            password: String,
            recoveryEmail: String
        ): Result<SparkAuthResult> = Result.success(
            SparkAuthResult(
                uid = "pc_new_1",
                username = username.trim().lowercase(),
                displayName = "Linked User",
                email = recoveryEmail.trim().lowercase(),
                emailVerified = true
            )
        )
    }

    private lateinit var fakeAuth: FakePulseAuthService
    private var loginSucceeded = false

    @Before
    fun setUp() {
        fakeAuth = FakePulseAuthService()
        loginSucceeded = false
    }

    @Test
    fun loginScreen_rendersBlueBlackWhiteElementsAndSubmitsCredentials() {
        composeTestRule.setContent {
            PulseAuthTheme(darkTheme = true) {
                AuthScreen(
                    authService = fakeAuth,
                    enableAutoGoogleSignIn = false,
                    onAuthSuccess = { loginSucceeded = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_username_input").performScrollTo().performTextInput("New_User_1")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("Strong#Pass2026")
        composeTestRule.onNodeWithTag("login_password_visibility_toggle").performClick()
        composeTestRule.onNodeWithTag("remember_me_checkbox").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            loginSucceeded
        }
        assertEquals("new_user_1", fakeAuth.lastLoginUsername)
        assertTrue(loginSucceeded)
    }

    @Test
    fun signUpAndForgotPasswordFlows_renderLightThemeAndValidateInputs() {
        composeTestRule.setContent {
            PulseAuthTheme(darkTheme = false) {
                AuthScreen(
                    authService = fakeAuth,
                    enableAutoGoogleSignIn = false,
                    onAuthSuccess = { loginSucceeded = true }
                )
            }
        }

        // Switch to Create Account screen
        composeTestRule.onNodeWithTag("go_to_signup_button").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Reserve your unique username on PulseChat").assertIsDisplayed()

        composeTestRule.onNodeWithTag("signup_fullname_input").performScrollTo().performTextInput("New User")
        composeTestRule.onNodeWithTag("signup_username_input").performScrollTo().performTextInput("new_user_1")
        composeTestRule.onNodeWithTag("signup_recovery_email_input").performScrollTo().performTextInput("user@example.com")
        composeTestRule.onNodeWithTag("signup_password_input").performScrollTo().performTextInput("VeryStrong#2026")
        composeTestRule.onNodeWithTag("password_strength_label").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("Mismatch123!")
        composeTestRule.onNodeWithTag("signup_submit_button").performScrollTo().performClick()

        // Confirm password mismatch error is shown
        composeTestRule.onNodeWithTag("auth_error_banner").performScrollTo().assertIsDisplayed()

        // Navigate back to login, then to Forgot Password
        composeTestRule.onNodeWithTag("signup_back_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("forgot_password_button").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Reset Password").assertIsDisplayed()

        // Verify Spark verified recovery email reset flow
        composeTestRule.onNodeWithTag("forgot_username_input").performScrollTo().performTextInput("new_user_1")
        composeTestRule.onNodeWithTag("forgot_email_input").performScrollTo().performTextInput("user@example.com")
        composeTestRule.onNodeWithTag("forgot_send_reset_email_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("forgot_info_banner"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("forgot_info_banner").performScrollTo().assertIsDisplayed()
        assertEquals("new_user_1", fakeAuth.lastResetUsername)
        assertEquals("user@example.com", fakeAuth.lastResetEmail)

        composeTestRule.onNodeWithTag("forgot_reset_code_input").performScrollTo().performTextInput("654321")
        composeTestRule.onNodeWithTag("forgot_new_password_input").performScrollTo().performTextInput("Updated#2026")
        composeTestRule.onNodeWithTag("forgot_confirm_password_input").performScrollTo().performTextInput("Updated#2026")
        composeTestRule.onNodeWithTag("forgot_confirm_reset_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            fakeAuth.lastResetCode == "654321"
        }
        composeTestRule.onNodeWithTag("auth_info_banner").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun accountPasswordSecurityCard_switchesBetweenChangeAndLinkModes() {
        var clickedChange = false
        var clickedLink = false
        composeTestRule.setContent {
            PulseAuthTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Column {
                    AccountPasswordSecurityCard(
                        username = "alex_dev",
                        isProcessing = false,
                        onOpenChangePassword = { clickedChange = true },
                        onOpenLinkUsername = { }
                    )
                    AccountPasswordSecurityCard(
                        username = "",
                        isProcessing = false,
                        onOpenChangePassword = { },
                        onOpenLinkUsername = { clickedLink = true }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Signed username: @alex_dev").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_change_password_dialog").performClick()
        assertTrue(clickedChange)

        composeTestRule.onNodeWithTag("open_link_username_dialog").performClick()
        assertTrue(clickedLink)
    }
}
