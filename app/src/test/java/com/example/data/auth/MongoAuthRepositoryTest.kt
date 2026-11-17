package com.example.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MongoAuthRepositoryTest {

    private val dummyDb: FirebaseFirestore by lazy {
        // Not invoked by availability or password-reset calls; Mockito is not in dependencies.
        val constructor = FirebaseFirestore::class.java.declaredConstructors.firstOrNull()
        if (constructor != null) {
            constructor.isAccessible = true
        }
        unsafeDummy(FirebaseFirestore::class.java)
    }

    private val dummyAuth: FirebaseAuth by lazy {
        unsafeDummy(FirebaseAuth::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unsafeDummy(clazz: Class<T>): T {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val alloc = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return alloc.invoke(unsafe, clazz) as T
    }

    @Test
    fun normalizeBaseUrl_validatesAndAppendsTrailingSlash() {
        assertNull(MongoAuthRepository.normalizeBaseUrl(""))
        assertNull(MongoAuthRepository.normalizeBaseUrl("   "))
        assertNull(MongoAuthRepository.normalizeBaseUrl("ftp://example.com"))
        assertEquals(
            "https://pulsechat-auth.onrender.com/",
            MongoAuthRepository.normalizeBaseUrl("https://pulsechat-auth.onrender.com")
        )
        assertEquals(
            "http://10.0.2.2:8080/",
            MongoAuthRepository.normalizeBaseUrl("http://10.0.2.2:8080/")
        )
    }

    @Test
    fun missingBackendUrl_returnsActionableConfigurationError() = runTest {
        val repo = MongoAuthRepository(
            db = dummyDb,
            auth = dummyAuth,
            baseUrl = ""
        )
        val res = repo.loginWithUsername("bilal_ahmed", "Strong#Pass2026")
        assertTrue(res.isFailure)
        assertTrue(
            res.exceptionOrNull()?.message.orEmpty().contains("AUTH_BACKEND_BASE_URL")
        )
    }

    @Test
    fun availabilityAndPasswordReset_talkToMongoBackendAndParseErrors() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/auth/username-available") { ex ->
            val query = ex.requestURI.query.orEmpty()
            val body = if (query.contains("bilal")) {
                """{"available":true,"valid":true,"normalizedUsername":"bilal","reason":""}"""
            } else {
                """{"available":false,"valid":true,"normalizedUsername":"taken","reason":"This username is already taken."}"""
            }
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/auth/forgot-password") { ex ->
            val req = ex.requestBody.bufferedReader().readText()
            val (code, json) = if (req.contains("mismatch@example.com")) {
                400 to """{"error":"Username and recovery email do not match our records."}"""
            } else {
                200 to """{"message":"Verification code sent to bilal@example.com."}"""
            }
            val bytes = json.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/auth/reset-password") { ex ->
            val req = ex.requestBody.bufferedReader().readText()
            val (code, json) = if (req.contains("123456")) {
                200 to """{"message":"Password reset successful. Sign in with your new password."}"""
            } else {
                400 to """{"error":"Invalid verification code."}"""
            }
            val bytes = json.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val repo = MongoAuthRepository(
                db = dummyDb,
                auth = dummyAuth,
                baseUrl = "http://127.0.0.1:${server.address.port}"
            )

            val avail = repo.checkUsernameAvailability("  Bilal ").getOrThrow()
            assertTrue(avail.available)
            assertEquals("bilal", avail.normalizedUsername)

            val taken = repo.checkUsernameAvailability("taken").getOrThrow()
            assertFalse(taken.available)
            assertEquals("This username is already taken.", taken.reason)

            val resetMismatch = repo.sendPasswordReset("bilal", "mismatch@example.com")
            assertTrue(resetMismatch.isFailure)
            assertEquals(
                "Username and recovery email do not match our records.",
                resetMismatch.exceptionOrNull()?.message
            )

            val resetSent = repo.sendPasswordReset("bilal", "bilal@example.com").getOrThrow()
            assertTrue(resetSent.contains("bilal@example.com"))

            val confirmOk = repo.confirmPasswordReset(
                username = "bilal",
                recoveryEmail = "bilal@example.com",
                resetCode = "123456",
                newPassword = "Updated#Pass2026"
            ).getOrThrow()
            assertTrue(confirmOk.contains("Password reset successful"))
        } finally {
            server.stop(0)
        }
    }
}
