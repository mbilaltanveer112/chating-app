package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

private const val PHONE_AUTH_TAG = "PhoneAuth"

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

sealed interface PhoneOtpSendOutcome {
    data class CodeSent(
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken
    ) : PhoneOtpSendOutcome

    data class AutoVerified(
        val uid: String,
        val phoneNumber: String
    ) : PhoneOtpSendOutcome

    data class Failure(
        val message: String,
        val isRateLimited: Boolean = false
    ) : PhoneOtpSendOutcome
}

sealed interface PhoneOtpVerifyOutcome {
    data class Success(
        val uid: String,
        val phoneNumber: String
    ) : PhoneOtpVerifyOutcome

    data class Failure(
        val message: String,
        val isExpired: Boolean = false,
        val isRateLimited: Boolean = false
    ) : PhoneOtpVerifyOutcome
}

object PhoneAuthHelper {

    fun sendOtpCode(
        context: Context,
        e164PhoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        onResult: (PhoneOtpSendOutcome) -> Unit
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            onResult(PhoneOtpSendOutcome.Failure("Unable to access host screen for SMS verification."))
            return
        }

        val auth = Firebase.auth
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(PHONE_AUTH_TAG, "Phone verification auto-completed via Play Services")
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { result ->
                        val user = result.user
                        if (user != null) {
                            onResult(
                                PhoneOtpSendOutcome.AutoVerified(
                                    uid = user.uid,
                                    phoneNumber = user.phoneNumber ?: e164PhoneNumber
                                )
                            )
                        } else {
                            onResult(PhoneOtpSendOutcome.Failure("Verification succeeded but user session could not be established."))
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(PHONE_AUTH_TAG, "Auto-sign-in with phone credential failed", e)
                        val mapped = mapFirebaseAuthError(e)
                        onResult(PhoneOtpSendOutcome.Failure(mapped.first, mapped.second))
                    }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(PHONE_AUTH_TAG, "Phone verification failed: ${e.message}", e)
                val mapped = mapFirebaseAuthError(e)
                onResult(PhoneOtpSendOutcome.Failure(mapped.first, mapped.second))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(PHONE_AUTH_TAG, "SMS OTP dispatched successfully")
                onResult(
                    PhoneOtpSendOutcome.CodeSent(
                        verificationId = verificationId,
                        resendToken = token
                    )
                )
            }
        }

        try {
            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164PhoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
            if (resendToken != null) {
                builder.setForceResendingToken(resendToken)
            }
            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        } catch (e: Exception) {
            Log.e(PHONE_AUTH_TAG, "Failed to start phone verification", e)
            val mapped = mapFirebaseAuthError(e)
            onResult(PhoneOtpSendOutcome.Failure(mapped.first, mapped.second))
        }
    }

    suspend fun verifyOtpCode(
        verificationId: String,
        smsCode: String,
        fallbackPhoneNumber: String
    ): PhoneOtpVerifyOutcome {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode.trim())
            val authResult = Firebase.auth.signInWithCredential(credential).await()
            val user = authResult.user
                ?: return PhoneOtpVerifyOutcome.Failure("Unable to complete sign-in after verification.")
            PhoneOtpVerifyOutcome.Success(
                uid = user.uid,
                phoneNumber = user.phoneNumber ?: fallbackPhoneNumber
            )
        } catch (e: Exception) {
            Log.w(PHONE_AUTH_TAG, "OTP verification failed", e)
            val code = (e as? FirebaseAuthException)?.errorCode
            val isExpired = code == "ERROR_SESSION_EXPIRED"
            val mapped = mapFirebaseAuthError(e)
            PhoneOtpVerifyOutcome.Failure(
                message = mapped.first,
                isExpired = isExpired,
                isRateLimited = mapped.second
            )
        }
    }

    private fun mapFirebaseAuthError(e: Exception): Pair<String, Boolean> {
        val authCode = (e as? FirebaseAuthException)?.errorCode.orEmpty()
        return when {
            e is FirebaseTooManyRequestsException ||
                authCode == "ERROR_TOO_MANY_REQUESTS" ||
                authCode == "ERROR_QUOTA_EXCEEDED" -> {
                "Too many SMS verification requests right now. Please wait a few minutes before retrying." to true
            }
            authCode == "ERROR_SESSION_EXPIRED" -> {
                "This verification code has expired. Tap Resend OTP to receive a new 6-digit code." to false
            }
            authCode == "ERROR_INVALID_VERIFICATION_CODE" -> {
                "Incorrect 6-digit code. Please double-check the SMS and try again." to false
            }
            e is FirebaseAuthInvalidCredentialsException -> {
                "Invalid phone number or verification code format. Please check and try again." to false
            }
            e is FirebaseNetworkException -> {
                "Network connection lost. Check your internet and try again." to false
            }
            e is FirebaseAuthUserCollisionException -> {
                "This phone number is already linked to another account." to false
            }
            authCode == "ERROR_OPERATION_NOT_ALLOWED" -> {
                "Phone sign-in is not enabled yet on this Firebase project. Enable Phone provider in Firebase Console Authentication settings." to false
            }
            authCode == "ERROR_APP_NOT_AUTHORIZED" || authCode == "ERROR_MISSING_CLIENT_IDENTIFIER" -> {
                "App verification could not complete on this device. Ensure Google Play Services / SHA fingerprint is configured in Firebase." to false
            }
            else -> {
                (e.localizedMessage ?: "Verification failed. Please try again.") to false
            }
        }
    }
}
