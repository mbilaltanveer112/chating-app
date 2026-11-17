package com.example.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorsTest {

    @Test
    fun validateUsername_normalizesAndRejectsMalformedOrReserved() {
        val ok = AuthValidators.validateUsername("  Alice_Dev.99  ")
        assertTrue(ok.valid)
        assertEquals("alice_dev.99", ok.normalizedValue)

        assertFalse(AuthValidators.validateUsername("ab").valid)
        assertFalse(AuthValidators.validateUsername("a".repeat(31)).valid)
        assertFalse(AuthValidators.validateUsername("bad user!").valid)
        assertFalse(AuthValidators.validateUsername(".leadingdot").valid)
        assertFalse(AuthValidators.validateUsername("trailing_").valid)
        assertFalse(AuthValidators.validateUsername("double..dot").valid)
        assertFalse(AuthValidators.validateUsername("admin").valid)
        assertFalse(AuthValidators.validateUsername("PulseChat").valid)
    }

    @Test
    fun evaluatePasswordStrength_enforcesPolicyAndScoresStrength() {
        assertFalse(AuthValidators.evaluatePasswordStrength("").isValid)
        assertFalse(AuthValidators.evaluatePasswordStrength("Short1!").isValid)
        assertFalse(AuthValidators.evaluatePasswordStrength("alllowercase1!").isValid)
        assertFalse(AuthValidators.evaluatePasswordStrength("ALLUPPERCASE1!").isValid)
        assertFalse(AuthValidators.evaluatePasswordStrength("NoDigitsHere!").isValid)
        assertFalse(AuthValidators.evaluatePasswordStrength("NoSpecial123").isValid)
        assertFalse(AuthValidators.evaluatePasswordStrength(" ValidPass1!").isValid)

        val good = AuthValidators.evaluatePasswordStrength("ValidPass1!")
        assertTrue(good.isValid)
        assertEquals("Good", good.label)

        val strong = AuthValidators.evaluatePasswordStrength("VeryStrongPass#2026")
        assertTrue(strong.isValid)
        assertEquals("Strong", strong.label)
    }

    @Test
    fun validateConfirmPassword_email_phone_workAccurately() {
        assertTrue(AuthValidators.validateConfirmPassword("Secret#123", "Secret#123").valid)
        assertFalse(AuthValidators.validateConfirmPassword("Secret#123", "Secret#124").valid)

        assertTrue(AuthValidators.validateEmail("", required = false).valid)
        assertFalse(AuthValidators.validateEmail("", required = true).valid)
        assertTrue(AuthValidators.validateEmail("USER@Example.COM", required = true).valid)
        assertEquals("user@example.com", AuthValidators.validateEmail("USER@Example.COM", required = true).normalizedValue)
        assertFalse(AuthValidators.validateEmail("not-an-email", required = true).valid)

        assertTrue(AuthValidators.validateE164Phone("", required = false).valid)
        assertTrue(AuthValidators.validateE164Phone("+1 415-555-1234", required = true).valid)
        assertEquals("+14155551234", AuthValidators.validateE164Phone("+1 415-555-1234", required = true).normalizedValue)
        assertFalse(AuthValidators.validateE164Phone("0300123", required = true).valid)
    }
}
