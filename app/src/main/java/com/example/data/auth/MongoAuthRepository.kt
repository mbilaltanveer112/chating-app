package com.example.data.auth

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.ui.auth.AuthValidators
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Option A Authentication Repository:
 * - Authenticates Username/Password, Sign Up, Forgot/Reset Password, Change Password, and Link Username
 *   against the Node.js + MongoDB Atlas backend (`BuildConfig.AUTH_BACKEND_BASE_URL`).
 * - Exchanges MongoDB-verified logins/registrations for Firebase Custom Tokens (`signInWithCustomToken`)
 *   so all Cloud Firestore chats, WebRTC calls, E2EE key bundles, and Online/Last Seen presence keep working
 *   without requiring Firebase Console Email/Password provider to be enabled.
 */
class MongoAuthRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val baseUrl: String = BuildConfig.AUTH_BACKEND_BASE_URL,
    private val apiOverride: MongoAuthApi? = null
) : PulseAuthService {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        baseUrl = BuildConfig.AUTH_BACKEND_BASE_URL
    )

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val errorAdapter by lazy {
        moshi.adapter(ApiErrorDto::class.java)
    }

    private val apiLazy: Result<MongoAuthApi> by lazy {
        apiOverride?.let { return@lazy Result.success(it) }
        val cleanUrl = normalizeBaseUrl(baseUrl)
            ?: return@lazy Result.failure(
                IllegalStateException(
                    "MongoDB Auth Backend URL is not configured. Deploy auth-backend-mongodb and set AUTH_BACKEND_BASE_URL in AI Studio Secrets."
                )
            )
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        }
                    )
                }
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        Result.success(retrofit.create(MongoAuthApi::class.java))
    }

    override suspend fun checkUsernameAvailability(rawUsername: String): Result<UsernameAvailabilityResult> {
        val check = AuthValidators.validateUsername(rawUsername)
        if (!check.valid) {
            return Result.success(
                UsernameAvailabilityResult(
                    available = false,
                    valid = false,
                    normalizedUsername = check.normalizedValue,
                    reason = check.error.orEmpty()
                )
            )
        }
        val api = apiLazy.getOrElse { return Result.failure(it) }
        return try {
            val dto = api.checkUsernameAvailability(check.normalizedValue)
            Result.success(
                UsernameAvailabilityResult(
                    available = dto.available,
                    valid = dto.valid,
                    normalizedUsername = dto.normalizedUsername.ifBlank { check.normalizedValue },
                    reason = dto.reason
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapBackendError(e, "Unable to verify username availability.")))
        }
    }

    override suspend fun register(
        username: String,
        password: String,
        displayName: String,
        recoveryEmail: String
    ): Result<SparkAuthResult> {
        val nameCheck = AuthValidators.validateFullName(displayName)
        if (!nameCheck.valid) return Result.failure(IllegalArgumentException(nameCheck.error))

        val userCheck = AuthValidators.validateUsername(username)
        if (!userCheck.valid) return Result.failure(IllegalArgumentException(userCheck.error))

        val pwCheck = AuthValidators.evaluatePasswordStrength(password)
        if (!pwCheck.isValid) return Result.failure(IllegalArgumentException(pwCheck.error))

        val emailCheck = AuthValidators.validateEmail(recoveryEmail, required = true)
        if (!emailCheck.valid) return Result.failure(IllegalArgumentException(emailCheck.error))

        val api = apiLazy.getOrElse { return Result.failure(it) }
        return try {
            val res = api.register(
                RegisterRequestDto(
                    username = userCheck.normalizedValue,
                    password = password,
                    displayName = nameCheck.normalizedValue,
                    recoveryEmail = emailCheck.normalizedValue
                )
            )
            val token = res.customToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("Server did not return a Firebase session token."))

            val authResult = auth.signInWithCustomToken(token).await()
            val uid = authResult.user?.uid ?: res.uid
            val resolvedUsername = res.username.ifBlank { userCheck.normalizedValue }
            val resolvedName = res.displayName.ifBlank { nameCheck.normalizedValue }
            val resolvedEmail = res.email.ifBlank { emailCheck.normalizedValue }

            runCatching {
                syncFirestoreUserProfile(
                    uid = uid,
                    username = resolvedUsername,
                    displayName = resolvedName,
                    email = resolvedEmail
                )
            }

            Result.success(
                SparkAuthResult(
                    uid = uid,
                    username = resolvedUsername,
                    displayName = resolvedName,
                    email = resolvedEmail,
                    emailVerified = res.emailVerified,
                    message = res.message
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapBackendError(e, "Unable to create account.")))
        }
    }

    override suspend fun loginWithUsername(username: String, password: String): Result<SparkAuthResult> {
        val userCheck = AuthValidators.validateUsername(username)
        if (!userCheck.valid) return Result.failure(IllegalArgumentException(userCheck.error))
        if (password.isEmpty()) return Result.failure(IllegalArgumentException("Enter your password."))

        val api = apiLazy.getOrElse { return Result.failure(it) }
        return try {
            val res = api.login(
                LoginRequestDto(
                    username = userCheck.normalizedValue,
                    password = password
                )
            )
            val token = res.customToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("Server did not return a Firebase session token."))

            val authResult = auth.signInWithCustomToken(token).await()
            val uid = authResult.user?.uid ?: res.uid
            val resolvedUsername = res.username.ifBlank { userCheck.normalizedValue }
            val resolvedName = res.displayName.ifBlank { resolvedUsername }
            val resolvedEmail = res.email

            runCatching {
                syncFirestoreUserProfile(
                    uid = uid,
                    username = resolvedUsername,
                    displayName = resolvedName,
                    email = resolvedEmail
                )
            }

            Result.success(
                SparkAuthResult(
                    uid = uid,
                    username = resolvedUsername,
                    displayName = resolvedName,
                    email = resolvedEmail,
                    emailVerified = res.emailVerified,
                    message = res.message
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapBackendError(e, "Invalid username or password.")))
        }
    }

    override suspend fun sendPasswordReset(username: String, recoveryEmail: String): Result<String> {
        val userCheck = AuthValidators.validateUsername(username)
        if (!userCheck.valid) return Result.failure(IllegalArgumentException(userCheck.error))

        val emailCheck = AuthValidators.validateEmail(recoveryEmail, required = true)
        if (!emailCheck.valid) return Result.failure(IllegalArgumentException(emailCheck.error))

        val api = apiLazy.getOrElse { return Result.failure(it) }
        return try {
            val res = api.forgotPassword(
                ForgotPasswordRequestDto(
                    username = userCheck.normalizedValue,
                    recoveryEmail = emailCheck.normalizedValue
                )
            )
            Result.success(
                res.message.ifBlank {
                    "Verification code sent to ${emailCheck.normalizedValue}."
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapBackendError(e, "Unable to send password reset code.")))
        }
    }

    override suspend fun confirmPasswordReset(
        username: String,
        recoveryEmail: String,
        resetCode: String,
        newPassword: String
    ): Result<String> {
        val userCheck = AuthValidators.validateUsername(username)
        if (!userCheck.valid) return Result.failure(IllegalArgumentException(userCheck.error))

        val emailCheck = AuthValidators.validateEmail(recoveryEmail, required = true)
        if (!emailCheck.valid) return Result.failure(IllegalArgumentException(emailCheck.error))

        val cleanCode = resetCode.trim()
        if (!cleanCode.matches(Regex("^\\d{6}$"))) {
            return Result.failure(IllegalArgumentException("Enter the 6-digit verification code."))
        }

        val pwCheck = AuthValidators.evaluatePasswordStrength(newPassword)
        if (!pwCheck.isValid) return Result.failure(IllegalArgumentException(pwCheck.error))

        val api = apiLazy.getOrElse { return Result.failure(it) }
        return try {
            val res = api.resetPassword(
                ResetPasswordRequestDto(
                    username = userCheck.normalizedValue,
                    recoveryEmail = emailCheck.normalizedValue,
                    resetCode = cleanCode,
                    newPassword = newPassword
                )
            )
            Result.success(
                res.message.ifBlank { "Password updated successfully. You can now log in." }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapBackendError(e, "Unable to reset password.")))
        }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        if (currentPassword.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your current password."))
        }
        val pwCheck = AuthValidators.evaluatePasswordStrength(newPassword)
        if (!pwCheck.isValid) {
            return Result.failure(IllegalArgumentException(pwCheck.error))
        }

        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in first before changing your password."))
        val api = apiLazy.getOrElse { return Result.failure(it) }

        return try {
            val idToken = user.getIdToken(false).await().token
                ?: return Result.failure(IllegalStateException("Authentication session expired. Please sign in again."))
            api.changePassword(
                bearerToken = "Bearer $idToken",
                body = ChangePasswordRequestDto(
                    currentPassword = currentPassword,
                    newPassword = newPassword
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapBackendError(e, "Failed to update password.")))
        }
    }

    override suspend fun linkUsernameCredentials(
        username: String,
        password: String,
        recoveryEmail: String
    ): Result<SparkAuthResult> {
        val userCheck = AuthValidators.validateUsername(username)
        if (!userCheck.valid) return Result.failure(IllegalArgumentException(userCheck.error))

        val pwCheck = AuthValidators.evaluatePasswordStrength(password)
        if (!pwCheck.isValid) return Result.failure(IllegalArgumentException(pwCheck.error))

        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("You must be signed in to link a username."))

        val effectiveEmail = user.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: recoveryEmail.trim().lowercase()
        val emailCheck = AuthValidators.validateEmail(effectiveEmail, required = true)
        if (!emailCheck.valid) {
            return Result.failure(
                IllegalArgumentException("Enter a valid recovery email to enable password sign-in and reset.")
            )
        }

        val api = apiLazy.getOrElse { return Result.failure(it) }
        return try {
            val idToken = user.getIdToken(false).await().token
                ?: return Result.failure(IllegalStateException("Authentication session expired. Please sign in again."))

            val res = api.linkUsername(
                bearerToken = "Bearer $idToken",
                body = LinkUsernameRequestDto(
                    username = userCheck.normalizedValue,
                    password = password,
                    recoveryEmail = emailCheck.normalizedValue
                )
            )

            val resolvedUsername = res.username.ifBlank { userCheck.normalizedValue }
            val resolvedEmail = res.email.ifBlank { emailCheck.normalizedValue }
            val resolvedName = res.displayName.ifBlank {
                user.displayName?.takeIf { it.isNotBlank() } ?: resolvedUsername
            }

            syncFirestoreUserProfile(
                uid = user.uid,
                username = resolvedUsername,
                displayName = resolvedName,
                email = resolvedEmail
            )

            Result.success(
                SparkAuthResult(
                    uid = user.uid,
                    username = resolvedUsername,
                    displayName = resolvedName,
                    email = resolvedEmail,
                    emailVerified = res.emailVerified,
                    message = res.message
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapBackendError(e, "Failed to link username credentials.")))
        }
    }

    private suspend fun syncFirestoreUserProfile(
        uid: String,
        username: String,
        displayName: String,
        email: String
    ) {
        val cleanName = displayName.trim().ifBlank { username }.take(50)
        val cleanEmail = email.trim().lowercase().take(200)
        val nowServer = FieldValue.serverTimestamp()

        val userRef = db.collection("users").document(uid)
        val publicRef = db.collection("public_profiles").document(uid)

        val userSnap = runCatching { userRef.get().await() }.getOrNull()
        val publicSnap = runCatching { publicRef.get().await() }.getOrNull()

        val batch = db.batch()
        if (userSnap != null && userSnap.exists()) {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to nowServer
            )
            if (cleanEmail.isNotBlank() && userSnap.getString("email").isNullOrBlank()) {
                updates["email"] = cleanEmail
            }
            if (userSnap.getString("username").isNullOrBlank()) {
                updates["username"] = username
            }
            batch.update(userRef, updates)
        } else {
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "username" to username,
                "displayName" to cleanName,
                "bio" to "Available on PulseChat",
                "darkModeEnabled" to true,
                "notificationsEnabled" to true,
                "createdAt" to nowServer,
                "updatedAt" to nowServer
            )
            if (cleanEmail.isNotBlank()) {
                payload["email"] = cleanEmail
            }
            batch.set(userRef, payload)
        }

        if (publicSnap != null && publicSnap.exists()) {
            batch.update(
                publicRef,
                mapOf(
                    "handle" to username,
                    "updatedAt" to nowServer
                )
            )
        } else {
            batch.set(
                publicRef,
                mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "handle" to username,
                    "bio" to "Available on PulseChat",
                    "isOnline" to true,
                    "discoverable" to true,
                    "lastSeen" to nowServer,
                    "lastHeartbeatAt" to nowServer,
                    "activeSessions" to mapOf("primary" to nowServer),
                    "createdAt" to nowServer,
                    "updatedAt" to nowServer
                )
            )
        }
        batch.commit().await()
    }

    private fun mapBackendError(error: Throwable, fallback: String): String {
        if (error is HttpException) {
            val bodyStr = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
            if (!bodyStr.isNullOrBlank()) {
                val parsed = runCatching { errorAdapter.fromJson(bodyStr) }.getOrNull()
                val msg = parsed?.error?.takeIf { it.isNotBlank() }
                    ?: parsed?.message?.takeIf { it.isNotBlank() }
                if (!msg.isNullOrBlank()) return msg
            }
            return "Server returned HTTP ${error.code()}."
        }
        if (error is IOException) {
            return "Cannot reach MongoDB Auth server. Check your internet connection or AUTH_BACKEND_BASE_URL."
        }
        return error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }

    companion object {
        fun normalizeBaseUrl(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isBlank()) return null
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
