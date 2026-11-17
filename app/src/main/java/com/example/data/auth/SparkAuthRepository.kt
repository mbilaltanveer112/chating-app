package com.example.data.auth

import android.content.Context
import com.example.R
import com.example.ui.auth.AuthValidators
import com.google.firebase.Firebase
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Pure Firebase Spark Authentication & Firestore username index.
 * - Uses Firebase Authentication Email/Password provider internally while exposing only Username/Password UI.
 * - Normalizes usernames to lowercase and enforces uniqueness via `/usernames/{username}`.
 * - Never stores plaintext passwords or password hashes anywhere in Firestore or device storage.
 */
class SparkAuthRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : PulseAuthService {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

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
        return try {
            val snap = db.collection("usernames").document(check.normalizedValue).get().await()
            val available = !snap.exists()
            Result.success(
                UsernameAvailabilityResult(
                    available = available,
                    valid = true,
                    normalizedUsername = check.normalizedValue,
                    reason = if (available) "" else "This username is already taken."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e, "Unable to verify username availability.")))
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

        val normalizedUsername = userCheck.normalizedValue
        val normalizedEmail = emailCheck.normalizedValue
        val cleanName = nameCheck.normalizedValue

        return try {
            val existingUsername = db.collection("usernames").document(normalizedUsername).get().await()
            if (existingUsername.exists()) {
                return Result.failure(IllegalStateException("This username is already taken."))
            }

            val authResult = auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
            val user = authResult.user
                ?: return Result.failure(IllegalStateException("Account creation failed."))
            val uid = user.uid

            runCatching {
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(cleanName)
                    .build()
                user.updateProfile(profileUpdate).await()
            }
            // Best-effort verification email so password recovery is backed by a verified address
            runCatching { user.sendEmailVerification().await() }

            try {
                val nowServer = FieldValue.serverTimestamp()
                val batch = db.batch()
                batch.set(
                    db.collection("usernames").document(normalizedUsername),
                    mapOf(
                        "username" to normalizedUsername,
                        "uid" to uid,
                        "authEmail" to normalizedEmail,
                        "emailVerified" to user.isEmailVerified,
                        "createdAt" to nowServer,
                        "updatedAt" to nowServer
                    )
                )
                batch.set(
                    db.collection("users").document(uid),
                    mapOf(
                        "userId" to uid,
                        "username" to normalizedUsername,
                        "email" to normalizedEmail,
                        "displayName" to cleanName,
                        "bio" to "Available on PulseChat",
                        "darkModeEnabled" to true,
                        "notificationsEnabled" to true,
                        "createdAt" to nowServer,
                        "updatedAt" to nowServer
                    )
                )
                batch.set(
                    db.collection("public_profiles").document(uid),
                    mapOf(
                        "userId" to uid,
                        "displayName" to cleanName,
                        "handle" to normalizedUsername,
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
                batch.commit().await()
            } catch (writeError: Exception) {
                // Roll back orphan Firebase Auth user if username reservation/profile creation failed
                runCatching { user.delete().await() }
                runCatching { auth.signOut() }
                throw writeError
            }

            Result.success(
                SparkAuthResult(
                    uid = uid,
                    username = normalizedUsername,
                    displayName = cleanName,
                    email = normalizedEmail,
                    emailVerified = user.isEmailVerified,
                    message = "Verification email sent to $normalizedEmail."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e, "Unable to create account.")))
        }
    }

    override suspend fun loginWithUsername(username: String, password: String): Result<SparkAuthResult> {
        val userCheck = AuthValidators.validateUsername(username)
        if (!userCheck.valid) return Result.failure(IllegalArgumentException(userCheck.error))
        if (password.isEmpty()) return Result.failure(IllegalArgumentException("Enter your password."))

        val normalizedUsername = userCheck.normalizedValue
        return try {
            val usernameSnap = db.collection("usernames").document(normalizedUsername).get().await()
            if (!usernameSnap.exists()) {
                return Result.failure(IllegalArgumentException("Invalid username or password."))
            }
            val authEmail = usernameSnap.getString("authEmail")?.trim()?.lowercase().orEmpty()
            if (authEmail.isBlank()) {
                return Result.failure(IllegalStateException("Account login metadata is incomplete."))
            }

            val authRes = auth.signInWithEmailAndPassword(authEmail, password).await()
            val user = authRes.user
                ?: return Result.failure(IllegalStateException("Sign in failed."))

            runCatching { user.reload().await() }
            val wasVerified = usernameSnap.getBoolean("emailVerified") == true
            if (user.isEmailVerified && !wasVerified) {
                runCatching {
                    db.collection("usernames").document(normalizedUsername).update(
                        mapOf(
                            "emailVerified" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val privateSnap = runCatching { db.collection("users").document(user.uid).get().await() }.getOrNull()
            val displayName = privateSnap?.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: user.displayName?.takeIf { it.isNotBlank() }
                ?: normalizedUsername

            Result.success(
                SparkAuthResult(
                    uid = user.uid,
                    username = normalizedUsername,
                    displayName = displayName,
                    email = authEmail,
                    emailVerified = user.isEmailVerified
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e, "Invalid username or password.")))
        }
    }

    override suspend fun sendPasswordReset(username: String, recoveryEmail: String): Result<String> {
        val userCheck = AuthValidators.validateUsername(username)
        if (!userCheck.valid) return Result.failure(IllegalArgumentException(userCheck.error))

        val emailCheck = AuthValidators.validateEmail(recoveryEmail, required = true)
        if (!emailCheck.valid) return Result.failure(IllegalArgumentException(emailCheck.error))

        val normalizedUsername = userCheck.normalizedValue
        val normalizedEmail = emailCheck.normalizedValue

        return try {
            val snap = db.collection("usernames").document(normalizedUsername).get().await()
            val registeredEmail = snap.getString("authEmail")?.trim()?.lowercase().orEmpty()
            if (!snap.exists() || registeredEmail != normalizedEmail) {
                return Result.failure(
                    IllegalArgumentException("Username and recovery email do not match our records.")
                )
            }

            auth.sendPasswordResetEmail(normalizedEmail).await()
            Result.success(
                "Password reset link sent to $normalizedEmail. Open the link in your email to set a new password, then return here to log in."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e, "Unable to send password reset email.")))
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
        val email = user.email?.trim()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(
                IllegalStateException("Set a username & password with a recovery email first.")
            )

        return try {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e, "Failed to update password.")))
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
        val uid = user.uid

        val effectiveEmail = user.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: recoveryEmail.trim().lowercase()
        val emailCheck = AuthValidators.validateEmail(effectiveEmail, required = true)
        if (!emailCheck.valid) {
            return Result.failure(
                IllegalArgumentException("Enter a valid recovery email to enable password sign-in and reset.")
            )
        }

        val normalizedUsername = userCheck.normalizedValue
        val normalizedEmail = emailCheck.normalizedValue

        return try {
            val existingUsername = db.collection("usernames").document(normalizedUsername).get().await()
            if (existingUsername.exists() && existingUsername.getString("uid") != uid) {
                return Result.failure(IllegalStateException("This username is already taken."))
            }

            val hasPasswordProvider = user.providerData.any {
                it.providerId == EmailAuthProvider.PROVIDER_ID
            }
            if (!hasPasswordProvider) {
                val credential = EmailAuthProvider.getCredential(normalizedEmail, password)
                user.linkWithCredential(credential).await()
            } else {
                user.updatePassword(password).await()
            }

            if (!user.isEmailVerified) {
                runCatching { user.sendEmailVerification().await() }
            }

            val batch = db.batch()
            val nowServer = FieldValue.serverTimestamp()
            val usernameRef = db.collection("usernames").document(normalizedUsername)
            if (existingUsername.exists()) {
                batch.update(
                    usernameRef,
                    mapOf(
                        "authEmail" to normalizedEmail,
                        "emailVerified" to user.isEmailVerified,
                        "updatedAt" to nowServer
                    )
                )
            } else {
                batch.set(
                    usernameRef,
                    mapOf(
                        "username" to normalizedUsername,
                        "uid" to uid,
                        "authEmail" to normalizedEmail,
                        "emailVerified" to user.isEmailVerified,
                        "createdAt" to nowServer,
                        "updatedAt" to nowServer
                    )
                )
            }

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val displayName = userSnap.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: user.displayName?.takeIf { it.isNotBlank() }
                ?: normalizedUsername
            if (userSnap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "email" to normalizedEmail,
                    "updatedAt" to nowServer
                )
                if (userSnap.getString("username").isNullOrBlank()) {
                    updates["username"] = normalizedUsername
                }
                batch.update(userRef, updates)
            } else {
                batch.set(
                    userRef,
                    mapOf(
                        "userId" to uid,
                        "username" to normalizedUsername,
                        "email" to normalizedEmail,
                        "displayName" to displayName,
                        "bio" to "Available on PulseChat",
                        "darkModeEnabled" to true,
                        "notificationsEnabled" to true,
                        "createdAt" to nowServer,
                        "updatedAt" to nowServer
                    )
                )
            }

            val publicRef = db.collection("public_profiles").document(uid)
            val publicSnap = publicRef.get().await()
            if (publicSnap.exists()) {
                batch.update(
                    publicRef,
                    mapOf(
                        "handle" to normalizedUsername,
                        "updatedAt" to nowServer
                    )
                )
            }
            batch.commit().await()

            Result.success(
                SparkAuthResult(
                    uid = uid,
                    username = normalizedUsername,
                    displayName = displayName,
                    email = normalizedEmail,
                    emailVerified = user.isEmailVerified
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e, "Failed to link username credentials.")))
        }
    }

    private fun mapAuthError(error: Throwable, fallback: String): String = when (error) {
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> "Invalid username or password."
        is FirebaseAuthUserCollisionException -> "This email or username is already registered."
        is FirebaseAuthRecentLoginRequiredException ->
            "For security, please sign out and log back in before updating credentials."
        is FirebaseTooManyRequestsException ->
            "Too many authentication attempts. Please wait a few minutes and try again."
        else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
}
