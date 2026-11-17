package com.example.security

import com.example.data.model.SpamProtectionLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamProtectionEngineTest {

    @Test
    fun analyzeLinks_detectsPhishingIpPunycodeAndShorteners() {
        val safe = SpamProtectionEngine.analyzeLinks("Check the docs at https://developer.android.com/privacy")
        assertFalse(safe.hasSuspiciousLinks)
        assertFalse(safe.isHighRisk)

        val ipLink = SpamProtectionEngine.analyzeLinks("Urgent login http://192.168.1.50/verify")
        assertTrue(ipLink.hasSuspiciousLinks)
        assertTrue(ipLink.isHighRisk)

        val atRedirect = SpamProtectionEngine.analyzeLinks("Open https://trusted.com@evil-phish.example/login")
        assertTrue(atRedirect.hasSuspiciousLinks)
        assertTrue(atRedirect.isHighRisk)

        val punycode = SpamProtectionEngine.analyzeLinks("Visit https://xn--pple-43d.com/reset")
        assertTrue(punycode.hasSuspiciousLinks)
        assertTrue(punycode.isHighRisk)

        val shortener = SpamProtectionEngine.analyzeLinks("Claim now https://bit.ly/3xyz")
        assertTrue(shortener.hasSuspiciousLinks)
    }

    @Test
    fun evaluateOutgoingMessage_detectsRepeatedDuplicatesAndRateLimits() {
        var nowMs = 1_000_000L
        val engine = SpamProtectionEngine(clock = { nowMs })

        // Repeated identical messages trigger temporary restriction in STANDARD mode on 4th identical send
        repeat(3) {
            nowMs += 3_500L
            val verdict = engine.evaluateOutgoingMessage(
                text = "FREE CRYPTO AIRDROP CLAIM NOW",
                chatId = "chat_1",
                level = SpamProtectionLevel.STANDARD,
                persistedRestrictedUntilMs = 0L,
                currentViolationCount = 0
            )
            assertTrue(verdict is OutgoingSpamVerdict.Allowed)
        }

        nowMs += 3_500L
        val blockedVerdict = engine.evaluateOutgoingMessage(
            text = "FREE CRYPTO AIRDROP CLAIM NOW",
            chatId = "chat_1",
            level = SpamProtectionLevel.STANDARD,
            persistedRestrictedUntilMs = 0L,
            currentViolationCount = 0
        )
        assertTrue(blockedVerdict is OutgoingSpamVerdict.Restricted)

        // Rapid burst within 3s triggers automated spam restriction even with unique text
        engine.clearHistory()
        nowMs = 2_000_000L
        repeat(4) { i ->
            nowMs += 200L
            val v = engine.evaluateOutgoingMessage(
                text = "Unique message $i",
                chatId = "chat_1",
                level = SpamProtectionLevel.STRICT,
                persistedRestrictedUntilMs = 0L,
                currentViolationCount = 0
            )
            assertTrue(v is OutgoingSpamVerdict.Allowed)
        }
        nowMs += 200L
        val burstRestricted = engine.evaluateOutgoingMessage(
            text = "Unique message 5",
            chatId = "chat_1",
            level = SpamProtectionLevel.STRICT,
            persistedRestrictedUntilMs = 0L,
            currentViolationCount = 0
        )
        assertTrue(burstRestricted is OutgoingSpamVerdict.Restricted)
    }
}
