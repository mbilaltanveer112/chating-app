package com.example.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinCryptoUtilsTest {

    @Test
    fun pinFormatValidation_enforcesSixDigits() {
        assertTrue(PinCryptoUtils.isValidPinFormat("482910"))
        assertFalse(PinCryptoUtils.isValidPinFormat("12345"))
        assertFalse(PinCryptoUtils.isValidPinFormat("1234567"))
        assertFalse(PinCryptoUtils.isValidPinFormat("12ab56"))
    }

    @Test
    fun hashAndVerifySecret_saltsProperlyAndVerifiesCorrectly() {
        val h1 = PinCryptoUtils.hashSecret("654321")
        val h2 = PinCryptoUtils.hashSecret("654321")

        assertNotEquals(h1.salt, h2.salt)
        assertNotEquals(h1.hash, h2.hash)
        assertTrue(PinCryptoUtils.verifySecret("654321", h1.hash, h1.salt))
        assertFalse(PinCryptoUtils.verifySecret("123456", h1.hash, h1.salt))
    }

    @Test
    fun generateRecoveryCode_producesFormattedCodeAndVerifiesViaHash() {
        val code = PinCryptoUtils.generateRecoveryCode()
        assertTrue(Regex("^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$").matches(code))
        val hashed = PinCryptoUtils.hashSecret(code)
        assertTrue(PinCryptoUtils.verifySecret(code, hashed.hash, hashed.salt))
        assertFalse(PinCryptoUtils.verifySecret("XXXX-XXXX-XXXX", hashed.hash, hashed.salt))
    }
}
