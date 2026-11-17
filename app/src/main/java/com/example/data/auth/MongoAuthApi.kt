package com.example.data.auth

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class UsernameAvailabilityResponseDto(
    @field:Json(name = "available") val available: Boolean = false,
    @field:Json(name = "valid") val valid: Boolean = false,
    @field:Json(name = "normalizedUsername") val normalizedUsername: String = "",
    @field:Json(name = "reason") val reason: String = ""
)

data class RegisterRequestDto(
    @field:Json(name = "username") val username: String,
    @field:Json(name = "password") val password: String,
    @field:Json(name = "displayName") val displayName: String,
    @field:Json(name = "recoveryEmail") val recoveryEmail: String
)

data class LoginRequestDto(
    @field:Json(name = "username") val username: String,
    @field:Json(name = "password") val password: String
)

data class LinkUsernameRequestDto(
    @field:Json(name = "username") val username: String,
    @field:Json(name = "password") val password: String,
    @field:Json(name = "recoveryEmail") val recoveryEmail: String
)

data class ChangePasswordRequestDto(
    @field:Json(name = "currentPassword") val currentPassword: String,
    @field:Json(name = "newPassword") val newPassword: String
)

data class ForgotPasswordRequestDto(
    @field:Json(name = "username") val username: String,
    @field:Json(name = "recoveryEmail") val recoveryEmail: String
)

data class ResetPasswordRequestDto(
    @field:Json(name = "username") val username: String,
    @field:Json(name = "recoveryEmail") val recoveryEmail: String,
    @field:Json(name = "resetCode") val resetCode: String,
    @field:Json(name = "newPassword") val newPassword: String
)

data class AuthSessionResponseDto(
    @field:Json(name = "customToken") val customToken: String? = null,
    @field:Json(name = "uid") val uid: String = "",
    @field:Json(name = "username") val username: String = "",
    @field:Json(name = "displayName") val displayName: String = "",
    @field:Json(name = "email") val email: String = "",
    @field:Json(name = "emailVerified") val emailVerified: Boolean = false,
    @field:Json(name = "message") val message: String = ""
)

data class MessageResponseDto(
    @field:Json(name = "message") val message: String = ""
)

data class ApiErrorDto(
    @field:Json(name = "error") val error: String? = null,
    @field:Json(name = "message") val message: String? = null
)

interface MongoAuthApi {
    @GET("auth/username-available")
    suspend fun checkUsernameAvailability(
        @Query("username") username: String
    ): UsernameAvailabilityResponseDto

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): AuthSessionResponseDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): AuthSessionResponseDto

    @POST("auth/link-username")
    suspend fun linkUsername(
        @Header("Authorization") bearerToken: String,
        @Body body: LinkUsernameRequestDto
    ): AuthSessionResponseDto

    @POST("auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") bearerToken: String,
        @Body body: ChangePasswordRequestDto
    ): MessageResponseDto

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequestDto): MessageResponseDto

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequestDto): MessageResponseDto
}
