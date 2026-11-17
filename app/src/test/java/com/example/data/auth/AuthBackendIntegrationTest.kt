package com.example.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.auth.AuthValidators
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthBackendIntegrationTest {

    @After
    fun tearDown() {
        AuthSessionStore.clearInProcessSession()
    }

    @Test
    fun authValidators_normalizeUsernamesAndEnforceEmailAndPasswordRules() {
        val normalized = AuthValidators.validateUsername("  Alice_Dev.99 ")
        assertTrue(normalized.valid)
        assertEquals("alice_dev.99", normalized.normalizedValue)

        assertFalse(AuthValidators.validateUsername("ab").valid)
        assertFalse(AuthValidators.validateUsername("bad user!").valid)
        assertFalse(AuthValidators.validateUsername("_leading").valid)
        assertFalse(AuthValidators.validateUsername("admin").valid)

        val validEmail = AuthValidators.validateEmail("  Alice@Example.COM ", required = true)
        assertTrue(validEmail.valid)
        assertEquals("alice@example.com", validEmail.normalizedValue)
        assertFalse(AuthValidators.validateEmail("", required = true).valid)
        assertFalse(AuthValidators.validateEmail("not-an-email", required = true).valid)

        assertTrue(AuthValidators.evaluatePasswordStrength("Strong#Pass2026").isValid)
        assertFalse(AuthValidators.evaluatePasswordStrength("weak").isValid)
    }

    @Test
    fun authSessionStore_persistsRememberMeAndClearsUnrememberedUsername() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AuthSessionStore(context)

        store.saveLoginPreference(rememberMe = true, username = "  Alice_Dev ")
        assertTrue(store.isRememberMeEnabled())
        assertEquals("alice_dev", store.getRememberedUsername())

        store.saveLoginPreference(rememberMe = false, username = "alice_dev")
        assertFalse(store.isRememberMeEnabled())
        assertEquals("", store.getRememberedUsername())

        AuthSessionStore.markSessionActiveInProcess()
        assertTrue(AuthSessionStore.isSessionActiveInProcess())
        store.clearOnSignOut()
        assertFalse(AuthSessionStore.isSessionActiveInProcess())
    }
}
