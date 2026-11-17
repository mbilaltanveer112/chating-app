package com.example.ui.auth

import kotlin.math.max

data class CountryDialCode(
    val isoCode: String,
    val name: String,
    val dialCode: String,
    val flag: String,
    val minDigits: Int,
    val maxDigits: Int,
    val stripLeadingZero: Boolean = true,
    val exampleHint: String
)

object SupportedCountries {
    val all: List<CountryDialCode> = listOf(
        CountryDialCode("PK", "Pakistan", "+92", "\uD83C\uDDF5\uD83C\uDDF0", 10, 10, true, "300 1234567"),
        CountryDialCode("US", "United States", "+1", "\uD83C\uDDFA\uD83C\uDDF8", 10, 10, false, "650 555 0100"),
        CountryDialCode("GB", "United Kingdom", "+44", "\uD83C\uDDEC\uD83C\uDDE7", 10, 10, true, "7700 900123"),
        CountryDialCode("IN", "India", "+91", "\uD83C\uDDEE\uD83C\uDDF3", 10, 10, true, "98765 43210"),
        CountryDialCode("AE", "United Arab Emirates", "+971", "\uD83C\uDDE6\uD83C\uDDEA", 9, 9, true, "50 123 4567"),
        CountryDialCode("SA", "Saudi Arabia", "+966", "\uD83C\uDDF8\uD83C\uDDE6", 9, 9, true, "50 123 4567"),
        CountryDialCode("CA", "Canada", "+1", "\uD83C\uDDE8\uD83C\uDDE6", 10, 10, false, "416 555 0199"),
        CountryDialCode("AU", "Australia", "+61", "\uD83C\uDDE6\uD83C\uDDFA", 9, 9, true, "412 345 678"),
        CountryDialCode("TR", "Turkey", "+90", "\uD83C\uDDF9\uD83C\uDDF7", 10, 10, true, "501 234 5678"),
        CountryDialCode("DE", "Germany", "+49", "\uD83C\uDDE9\uD83C\uDDEA", 10, 11, true, "151 23456789"),
        CountryDialCode("FR", "France", "+33", "\uD83C\uDDEB\uD83C\uDDF7", 9, 9, true, "6 12 34 56 78"),
        CountryDialCode("BD", "Bangladesh", "+880", "\uD83C\uDDE7\uD83C\uDDE9", 10, 10, true, "1712 345678"),
        CountryDialCode("ID", "Indonesia", "+62", "\uD83C\uDDEE\uD83C\uDDE9", 9, 12, true, "812 3456 7890"),
        CountryDialCode("MY", "Malaysia", "+60", "\uD83C\uDDF2\uD83C\uDDFE", 9, 10, true, "12 345 6789"),
        CountryDialCode("EG", "Egypt", "+20", "\uD83C\uDDEA\uD83C\uDDEC", 10, 10, true, "100 123 4567"),
        CountryDialCode("NG", "Nigeria", "+234", "\uD83C\uDDF3\uD83C\uDDEC", 10, 10, true, "802 123 4567"),
        CountryDialCode("ZA", "South Africa", "+27", "\uD83C\uDDFF\uD83C\uDDE6", 9, 9, true, "71 123 4567"),
        CountryDialCode("SG", "Singapore", "+65", "\uD83C\uDDF8\uD83C\uDDEC", 8, 8, false, "8123 4567"),
        CountryDialCode("QA", "Qatar", "+974", "\uD83C\uDDF6\uD83C\uDDE6", 8, 8, false, "3312 3456"),
        CountryDialCode("KW", "Kuwait", "+965", "\uD83C\uDDF0\uD83C\uDDFC", 8, 8, false, "5001 2345"),
        CountryDialCode("OM", "Oman", "+968", "\uD83C\uDDF4\uD83C\uDDF2", 8, 8, false, "9212 3456"),
        CountryDialCode("BH", "Bahrain", "+973", "\uD83C\uDDE7\uD83C\uDDED", 8, 8, false, "3612 3456"),
        CountryDialCode("IT", "Italy", "+39", "\uD83C\uDDEE\uD83C\uDDF9", 9, 10, false, "312 345 6789"),
        CountryDialCode("ES", "Spain", "+34", "\uD83C\uDDEA\uD83C\uDDF8", 9, 9, false, "612 34 56 78"),
        CountryDialCode("NL", "Netherlands", "+31", "\uD83C\uDDF3\uD83C\uDDF1", 9, 9, true, "6 12345678"),
        CountryDialCode("BR", "Brazil", "+55", "\uD83C\uDDE7\uD83C\uDDF7", 10, 11, true, "11 91234 5678"),
        CountryDialCode("MX", "Mexico", "+52", "\uD83C\uDDF2\uD83C\uDDFD", 10, 10, false, "55 1234 5678"),
        CountryDialCode("JP", "Japan", "+81", "\uD83C\uDDEF\uD83C\uDDF5", 10, 10, true, "90 1234 5678"),
        CountryDialCode("KR", "South Korea", "+82", "\uD83C\uDDF0\uD83C\uDDF7", 9, 10, true, "10 1234 5678"),
        CountryDialCode("CN", "China", "+86", "\uD83C\uDDE8\uD83C\uDDF3", 11, 11, false, "138 0013 8000")
    )

    val defaultCountry: CountryDialCode = all.first() // Pakistan (+92)
}

sealed interface PhoneValidationResult {
    data class Valid(
        val e164: String,
        val displayFormatted: String,
        val nationalDigits: String
    ) : PhoneValidationResult

    data class Invalid(val errorMessage: String) : PhoneValidationResult
}

object PhoneNumberValidator {
    private val E164_REGEX = Regex("^\\+[1-9][0-9]{6,14}$")

    fun normalizeInputDigits(rawInput: String): String =
        rawInput.filter { it.isDigit() || it == '+' }

    fun validate(country: CountryDialCode, rawInput: String): PhoneValidationResult {
        val compact = rawInput.trim().replace(Regex("[\\s\\-().]"), "")
        if (compact.isEmpty()) {
            return PhoneValidationResult.Invalid("Please enter your mobile phone number.")
        }

        val codeDigits = country.dialCode.removePrefix("+")
        var digits = when {
            compact.startsWith("+") -> {
                val stripped = compact.removePrefix("+")
                if (!stripped.all { it.isDigit() }) {
                    return PhoneValidationResult.Invalid("Phone number must contain only digits.")
                }
                if (stripped.startsWith(codeDigits)) stripped.removePrefix(codeDigits) else stripped
            }
            compact.startsWith("00$codeDigits") -> {
                val stripped = compact.removePrefix("00$codeDigits")
                if (!stripped.all { it.isDigit() }) {
                    return PhoneValidationResult.Invalid("Phone number must contain only digits.")
                }
                stripped
            }
            else -> {
                if (!compact.all { it.isDigit() }) {
                    return PhoneValidationResult.Invalid("Phone number must contain only digits.")
                }
                compact
            }
        }

        if (country.stripLeadingZero && digits.startsWith("0") && digits.length > country.minDigits) {
            digits = digits.dropWhile { it == '0' }
        }

        if (digits.isEmpty()) {
            return PhoneValidationResult.Invalid("Enter a valid ${country.name} phone number (e.g., ${country.exampleHint}).")
        }

        if (country.isoCode == "PK" && digits.startsWith("0")) {
            digits = digits.drop(1)
        }

        if (digits.length < country.minDigits) {
            return PhoneValidationResult.Invalid(
                "Phone number is too short for ${country.name} (expected ${country.minDigits} digits, e.g., ${country.exampleHint})."
            )
        }
        if (digits.length > country.maxDigits) {
            return PhoneValidationResult.Invalid(
                "Phone number is too long for ${country.name} (max ${country.maxDigits} digits)."
            )
        }

        val e164 = "${country.dialCode}$digits"
        if (!E164_REGEX.matches(e164)) {
            return PhoneValidationResult.Invalid("Enter a valid international phone number.")
        }

        return PhoneValidationResult.Valid(
            e164 = e164,
            displayFormatted = "${country.dialCode} $digits",
            nationalDigits = digits
        )
    }
}

sealed interface RateLimitDecision {
    data class Allowed(val nextCooldownSeconds: Int) : RateLimitDecision
    data class Blocked(val waitSeconds: Int, val reason: String) : RateLimitDecision
}

/**
 * Client-side OTP request & attempt rate limiter that complements Firebase Auth's
 * server-side SMS quota and Play Integrity / reCAPTCHA protections.
 * Contains zero OTP secrets or verification tokens.
 */
class OtpRateLimiter(
    private val maxRequestsPerWindow: Int = 5,
    private val windowMillis: Long = 15 * 60_000L,
    private val baseCooldownSeconds: Int = 60,
    private val maxFailedAttemptsPerSession: Int = 5
) {
    private val requestTimestampsByPhone = mutableMapOf<String, MutableList<Long>>()
    private val lastRequestAtByPhone = mutableMapOf<String, Long>()
    private var failedAttemptsInCurrentSession = 0

    fun checkCanRequestOtp(
        e164Phone: String,
        nowMillis: Long = System.currentTimeMillis()
    ): RateLimitDecision {
        val history = requestTimestampsByPhone
            .getOrPut(e164Phone) { mutableListOf() }
        history.removeAll { nowMillis - it > windowMillis }

        val lastSent = lastRequestAtByPhone[e164Phone]
        if (lastSent != null) {
            val requiredCooldownSec = cooldownForAttemptCount(history.size)
            val elapsedSec = ((nowMillis - lastSent) / 1000L).toInt()
            val remaining = requiredCooldownSec - elapsedSec
            if (remaining > 0) {
                return RateLimitDecision.Blocked(
                    waitSeconds = remaining,
                    reason = "Please wait ${remaining}s before requesting another code."
                )
            }
        }

        if (history.size >= maxRequestsPerWindow) {
            val oldest = history.minOrNull() ?: nowMillis
            val waitSec = max(1, (((oldest + windowMillis) - nowMillis) / 1000L).toInt())
            val waitMins = max(1, (waitSec + 59) / 60)
            return RateLimitDecision.Blocked(
                waitSeconds = waitSec,
                reason = "Too many OTP requests for this number. Try again in about $waitMins min."
            )
        }

        val nextCooldown = cooldownForAttemptCount(history.size + 1)
        return RateLimitDecision.Allowed(nextCooldownSeconds = nextCooldown)
    }

    fun recordOtpSent(
        e164Phone: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val history = requestTimestampsByPhone.getOrPut(e164Phone) { mutableListOf() }
        history.removeAll { nowMillis - it > windowMillis }
        history.add(nowMillis)
        lastRequestAtByPhone[e164Phone] = nowMillis
        failedAttemptsInCurrentSession = 0
        return cooldownForAttemptCount(history.size)
    }

    fun recordFailedOtpVerification(): Int {
        failedAttemptsInCurrentSession += 1
        return max(0, maxFailedAttemptsPerSession - failedAttemptsInCurrentSession)
    }

    fun isSessionLockedByFailedAttempts(): Boolean =
        failedAttemptsInCurrentSession >= maxFailedAttemptsPerSession

    fun resetSessionAttempts() {
        failedAttemptsInCurrentSession = 0
    }

    private fun cooldownForAttemptCount(attemptCount: Int): Int = when {
        attemptCount <= 2 -> baseCooldownSeconds
        attemptCount == 3 -> 90
        else -> 120
    }

    companion object {
        const val OTP_LENGTH = 6
        const val OTP_EXPIRY_MILLIS = 5 * 60_000L
    }
}
