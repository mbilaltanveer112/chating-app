package com.example.data.auth

import com.example.base.FirestoreEmulatorTestBase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SparkAuthRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val TIMEOUT_MS = 8000L
    }

    @Test
    fun registerAndLoginWithNormalizedUsername_enforcesUniquenessAndStoresZeroPasswords() = runBlocking {
        val runId = UUID.randomUUID().toString().take(6).lowercase()
        val username = "spark_$runId"
        val email = "spark_$runId@example.com"
        val repo = SparkAuthRepository(firestore, auth)

        val availBefore = withTimeout(TIMEOUT_MS) {
            repo.checkUsernameAvailability("  ${username.uppercase()} ").getOrThrow()
        }
        assertTrue(availBefore.valid)
        assertTrue(availBefore.available)
        assertEquals(username, availBefore.normalizedUsername)

        val reg = withTimeout(TIMEOUT_MS) {
            repo.register(
                username = " ${username.uppercase()} ",
                password = "Strong#Pass2026",
                displayName = "Spark User",
                recoveryEmail = "  $email "
            ).getOrThrow()
        }
        assertEquals(username, reg.username)
        assertEquals(email, reg.email)

        // Ensure Firestore documents contain no password or hash fields
        val usernameDoc = withTimeout(TIMEOUT_MS) {
            firestore.collection("usernames").document(username).get().await()
        }
        assertTrue(usernameDoc.exists())
        assertEquals(reg.uid, usernameDoc.getString("uid"))
        assertNull(usernameDoc.get("password"))
        assertNull(usernameDoc.get("passwordHash"))

        val userDoc = withTimeout(TIMEOUT_MS) {
            firestore.collection("users").document(reg.uid).get().await()
        }
        assertTrue(userDoc.exists())
        assertEquals(username, userDoc.getString("username"))
        assertNull(userDoc.get("password"))
        assertNull(userDoc.get("passwordHash"))

        // Duplicate username is rejected
        auth.signOut()
        val availAfter = withTimeout(TIMEOUT_MS) {
            repo.checkUsernameAvailability(username).getOrThrow()
        }
        assertFalse(availAfter.available)

        val dupReg = withTimeout(TIMEOUT_MS) {
            repo.register(
                username = username,
                password = "Another#Pass2026",
                displayName = "Dup User",
                recoveryEmail = "dup_$runId@example.com"
            )
        }
        assertTrue(dupReg.isFailure)

        // Login with uppercase variant succeeds via normalized lookup
        val loginOk = withTimeout(TIMEOUT_MS) {
            repo.loginWithUsername(username.uppercase(), "Strong#Pass2026").getOrThrow()
        }
        assertEquals(reg.uid, loginOk.uid)
        assertEquals(username, loginOk.username)

        // Wrong password fails
        auth.signOut()
        val wrongPw = withTimeout(TIMEOUT_MS) {
            repo.loginWithUsername(username, "Wrong#Pass999")
        }
        assertTrue(wrongPw.isFailure)

        // Reset with mismatched recovery email fails
        val badReset = withTimeout(TIMEOUT_MS) {
            repo.sendPasswordReset(username, "wrong_$runId@example.com")
        }
        assertTrue(badReset.isFailure)
    }
}
