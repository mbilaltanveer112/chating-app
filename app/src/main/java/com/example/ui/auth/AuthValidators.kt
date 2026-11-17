package com.example.ui.auth

data class FieldValidationResult(
    val valid: Boolean,
    val error: String? = null,
    val normalizedValue: String = ""
)

data class PasswordStrengthEvaluation(
    val score: Int,
    val label: String,
    val hasMinLength: Boolean,
    val hasLowercase: Boolean,
    val hasUppercase: Boolean,
    val hasDigit: Boolean,
    val hasSpecialChar: Boolean,
    val hasNoEdgeSpaces: Boolean,
    val isValid: Boolean,
    val error: String?
)

object AuthValidators {

    private val USERNAME_REGEX = Regex("^[a-z0-9._]{3,30}$")
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    private val E164_PHONE_REGEX = Regex("^\\+[1-9][0-9]{6,14}$")

    private val RESERVED_USERNAMES = setOf(
        "admin",
        "administrator",
        "auth",
        "help",
        "moderator",
        "null",
        "pulsechat",
        "root",
        "security",
        "support",
        "system",
        "undefined"
    )

    fun normalizeUsername(raw: String): String = raw.trim().lowercase()

    fun validateUsername(raw: String): FieldValidationResult {
        val normalized = normalizeUsername(raw)
        if (normalized.length < 3 || normalized.length > 30) {
            return FieldValidationResult(
                valid = false,
                error = "Username must be between 3 and 30 characters.",
                normalizedValue = normalized
            )
        }
        if (!USERNAME_REGEX.matches(normalized)) {
            return FieldValidationResult(
                valid = false,
                error = "Use only lowercase letters, numbers, dots, or underscores.",
                normalizedValue = normalized
            )
        }
        if (normalized.startsWith(".") ||
            normalized.startsWith("_") ||
            normalized.endsWith(".") ||
            normalized.endsWith("_") ||
            normalized.contains("..") ||
            normalized.contains("__")
        ) {
            return FieldValidationResult(
                valid = false,
                error = "Username cannot start/end with or repeat dots or underscores.",
                normalizedValue = normalized
            )
        }
        if (RESERVED_USERNAMES.contains(normalized)) {
            return FieldValidationResult(
                valid = false,
                error = "This username is reserved.",
                normalizedValue = normalized
            )
        }
        return FieldValidationResult(valid = true, normalizedValue = normalized)
    }

    fun validateFullName(raw: String): FieldValidationResult {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> FieldValidationResult(false, "Full name is required.")
            trimmed.length > 50 -> FieldValidationResult(false, "Full name must be 50 characters or fewer.")
            else -> FieldValidationResult(true, normalizedValue = trimmed)
        }
    }

    fun evaluatePasswordStrength(password: String): PasswordStrengthEvaluation {
        val hasMinLength = password.length in 8..128
        val hasLowercase = password.any { it.isLowerCase() }
        val hasUppercase = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        val hasNoEdgeSpaces = password == password.trim()

        val error = when {
            password.isEmpty() -> "Password is required."
            password.length < 8 -> "Password must be at least 8 characters."
            password.length > 128 -> "Password must be 128 characters or fewer."
            !hasNoEdgeSpaces -> "Password cannot start or end with spaces."
            !hasLowercase -> "Include at least one lowercase letter."
            !hasUppercase -> "Include at least one uppercase letter."
            !hasDigit -> "Include at least one number."
            !hasSpecial -> "Include at least one special character."
            else -> null
        }

        val criteriaCount = listOf(hasMinLength, hasLowercase, hasUppercase, hasDigit, hasSpecial).count { it }
        val bonus = if (password.length >= 12) 1 else 0
        val rawScore = if (error == null) (3 + bonus).coerceAtMost(4) else (criteriaCount - 1).coerceIn(0, 2)
        val label = when {
            password.isEmpty() -> ""
            rawScore <= 1 -> "Weak"
            rawScore == 2 -> "Fair"
            rawScore == 3 -> "Good"
            else -> "Strong"
        }

        return PasswordStrengthEvaluation(
            score = rawScore,
            label = label,
            hasMinLength = hasMinLength,
            hasLowercase = hasLowercase,
            hasUppercase = hasUppercase,
            hasDigit = hasDigit,
            hasSpecialChar = hasSpecial,
            hasNoEdgeSpaces = hasNoEdgeSpaces,
            isValid = error == null,
            error = error
        )
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): FieldValidationResult {
        return when {
            confirmPassword.isEmpty() -> FieldValidationResult(false, "Please confirm your password.")
            password != confirmPassword -> FieldValidationResult(false, "Passwords do not match.")
            else -> FieldValidationResult(true)
        }
    }

    fun validateEmail(raw: String, required: Boolean): FieldValidationResult {
        val normalized = raw.trim().lowercase()
        if (normalized.isEmpty()) {
            return if (required) {
                FieldValidationResult(false, "Verified recovery email is required.")
            } else {
                FieldValidationResult(true, normalizedValue = "")
            }
        }
        return if (normalized.length > 200 || !EMAIL_REGEX.matches(normalized)) {
            FieldValidationResult(false, "Enter a valid email address.", normalizedValue = normalized)
        } else {
            FieldValidationResult(true, normalizedValue = normalized)
        }
    }

    fun validateE164Phone(raw: String, required: Boolean): FieldValidationResult {
        val normalized = raw.trim().replace(Regex("[\\s()-]"), "")
        if (normalized.isEmpty()) {
            return if (required) {
                FieldValidationResult(false, "Enter phone number in E.164 format (e.g. +14155551234).")
            } else {
                FieldValidationResult(true, normalizedValue = "")
            }
        }
        return if (!E164_PHONE_REGEX.matches(normalized)) {
            FieldValidationResult(false, "Use E.164 format starting with + and country code.", normalizedValue = normalized)
        } else {
            FieldValidationResult(true, normalizedValue = normalized)
        }
    }
}
