package com.example.data.auth

data class UsernameAvailabilityResult(
    val available: Boolean,
    val valid: Boolean,
    val normalizedUsername: String,
    val reason: String = ""
)

data class SparkAuthResult(
    val uid: String,
    val username: String,
    val displayName: String,
    val email: String,
    val emailVerified: Boolean,
    val message: String = ""
)

interface PulseAuthService {
    suspend fun checkUsernameAvailability(rawUsername: String): Result<UsernameAvailabilityResult>
    suspend fun register(
        username: String,
        password: String,
        displayName: String,
        recoveryEmail: String
    ): Result<SparkAuthResult>
    suspend fun loginWithUsername(username: String, password: String): Result<SparkAuthResult>
    suspend fun sendPasswordReset(username: String, recoveryEmail: String): Result<String>
    suspend fun confirmPasswordReset(
        username: String,
        recoveryEmail: String,
        resetCode: String,
        newPassword: String
    ): Result<String> = Result.failure(
        UnsupportedOperationException("Two-step code password reset is handled by the MongoDB auth backend.")
    )
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    suspend fun linkUsernameCredentials(
        username: String,
        password: String,
        recoveryEmail: String
    ): Result<SparkAuthResult>
}
