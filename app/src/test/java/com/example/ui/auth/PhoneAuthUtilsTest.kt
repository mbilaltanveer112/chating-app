package com.example.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAuthUtilsTest {

    @Test
    fun validate_pakistanNumberWithAndWithoutLeadingZero_formatsE164() {
        val pk = SupportedCountries.all.first { it.isoCode == "PK" }

        val withoutZero = PhoneNumberValidator.validate(pk, "300 1234567")
        assertTrue(withoutZero is PhoneValidationResult.Valid)
        assertEquals("+923001234567", (withoutZero as PhoneValidationResult.Valid).e164)

        val withLeadingZero = PhoneNumberValidator.validate(pk, "0300-1234567")
        assertTrue(withLeadingZero is PhoneValidationResult.Valid)
        assertEquals("+923001234567", (withLeadingZero as PhoneValidationResult.Valid).e164)

        val pastedFull = PhoneNumberValidator.validate(pk, "+92 300 1234567")
        assertTrue(pastedFull is PhoneValidationResult.Valid)
        assertEquals("+923001234567", (pastedFull as PhoneValidationResult.Valid).e164)
    }

    @Test
    fun validate_invalidNumbers_returnsDescriptiveErrors() {
        val pk = SupportedCountries.all.first { it.isoCode == "PK" }
        val us = SupportedCountries.all.first { it.isoCode == "US" }

        assertTrue(PhoneNumberValidator.validate(pk, "") is PhoneValidationResult.Invalid)
        assertTrue(PhoneNumberValidator.validate(pk, "30012") is PhoneValidationResult.Invalid)
        assertTrue(PhoneNumberValidator.validate(pk, "3001234567890") is PhoneValidationResult.Invalid)
        assertTrue(PhoneNumberValidator.validate(us, "650abc1234") is PhoneValidationResult.Invalid)

        val validUs = PhoneNumberValidator.validate(us, "(650) 555-0100")
        assertTrue(validUs is PhoneValidationResult.Valid)
        assertEquals("+16505550100", (validUs as PhoneValidationResult.Valid).e164)
    }

    @Test
    fun rateLimiter_enforcesCooldownWindowAndFailedAttemptLockout() {
        val limiter = OtpRateLimiter(
            maxRequestsPerWindow = 5,
            windowMillis = 15 * 60_000L,
            baseCooldownSeconds = 60,
            maxFailedAttemptsPerSession = 5
        )
        val phone = "+923001234567"
        var now = 1_000_000L

        assertTrue(limiter.checkCanRequestOtp(phone, now) is RateLimitDecision.Allowed)
        limiter.recordOtpSent(phone, now)

        // Immediate resend within 60s is blocked
        val blockedImmediate = limiter.checkCanRequestOtp(phone, now + 10_000L)
        assertTrue(blockedImmediate is RateLimitDecision.Blocked)
        assertEquals(50, (blockedImmediate as RateLimitDecision.Blocked).waitSeconds)

        // Failed attempts lock after 5 tries
        repeat(4) {
            limiter.recordFailedOtpVerification()
            assertFalse(limiter.isSessionLockedByFailedAttempts())
        }
        assertEquals(0, limiter.recordFailedOtpVerification())
        assertTrue(limiter.isSessionLockedByFailedAttempts())

        // After cooldown, resend is allowed and resets failed attempts
        now += 61_000L
        assertTrue(limiter.checkCanRequestOtp(phone, now) is RateLimitDecision.Allowed)
        limiter.recordOtpSent(phone, now)
        assertFalse(limiter.isSessionLockedByFailedAttempts())

        // Exhaust max 5 requests inside 15-minute window
        now += 61_000L
        limiter.recordOtpSent(phone, now)
        now += 91_000L
        limiter.recordOtpSent(phone, now)
        now += 121_000L
        limiter.recordOtpSent(phone, now)
        now += 121_000L

        val blockedQuota = limiter.checkCanRequestOtp(phone, now)
        assertTrue(blockedQuota is RateLimitDecision.Blocked)
    }
}
