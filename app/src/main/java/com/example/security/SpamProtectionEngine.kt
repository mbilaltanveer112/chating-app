package com.example.security

import com.example.data.model.SpamProtectionLevel
import java.util.Locale

data class LinkSafetyResult(
    val hasSuspiciousLinks: Boolean,
    val suspiciousUrls: List<String>,
    val reasons: List<String>,
    val isHighRisk: Boolean
)

sealed interface OutgoingSpamVerdict {
    data object Allowed : OutgoingSpamVerdict
    data class Warning(val reason: String) : OutgoingSpamVerdict
    data class Restricted(
        val untilEpochMs: Long,
        val reason: String,
        val violationCount: Int
    ) : OutgoingSpamVerdict
}

class SpamProtectionEngine(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private data class SentRecord(
        val timestampMs: Long,
        val normalizedText: String,
        val chatId: String
    )

    private val recentHistory = mutableListOf<SentRecord>()

    @Synchronized
    fun evaluateOutgoingMessage(
        text: String,
        chatId: String,
        level: SpamProtectionLevel,
        persistedRestrictedUntilMs: Long,
        currentViolationCount: Int
    ): OutgoingSpamVerdict {
        val now = clock()
        if (persistedRestrictedUntilMs > now) {
            val remainingSec = ((persistedRestrictedUntilMs - now) / 1000L).coerceAtLeast(1L)
            return OutgoingSpamVerdict.Restricted(
                untilEpochMs = persistedRestrictedUntilMs,
                reason = "Messaging temporarily paused for ${remainingSec}s due to repeated spam triggers.",
                violationCount = currentViolationCount
            )
        }

        if (level == SpamProtectionLevel.OFF) {
            recordMessage(now, text, chatId)
            return OutgoingSpamVerdict.Allowed
        }

        pruneOldRecords(now)
        val normalized = normalizeText(text)
        val rateWindowMs = 10_000L
        val maxMessagesInWindow = if (level == SpamProtectionLevel.STRICT) 5 else 8
        val burstThresholdMs = 3_000L
        val maxBurstCount = if (level == SpamProtectionLevel.STRICT) 4 else 5
        val duplicateLimit = if (level == SpamProtectionLevel.STRICT) 2 else 3

        val inRateWindow = recentHistory.filter { (now - it.timestampMs) <= rateWindowMs }
        if (inRateWindow.size >= maxMessagesInWindow) {
            val nextViolations = currentViolationCount + 1
            val cooldownMs = calculateCooldownMs(nextViolations, level)
            return OutgoingSpamVerdict.Restricted(
                untilEpochMs = now + cooldownMs,
                reason = "Rate limit exceeded (${inRateWindow.size} messages in 10s). Slow down to avoid spam blocks.",
                violationCount = nextViolations
            )
        }

        val inBurstWindow = recentHistory.filter { (now - it.timestampMs) <= burstThresholdMs }
        if (inBurstWindow.size >= maxBurstCount) {
            val nextViolations = currentViolationCount + 1
            val cooldownMs = calculateCooldownMs(nextViolations, level)
            return OutgoingSpamVerdict.Restricted(
                untilEpochMs = now + cooldownMs,
                reason = "Automated burst behavior detected. Messaging paused temporarily.",
                violationCount = nextViolations
            )
        }

        if (normalized.length >= 4) {
            val duplicateCount = recentHistory.count {
                (now - it.timestampMs) <= 60_000L && it.normalizedText == normalized
            }
            if (duplicateCount >= duplicateLimit) {
                val nextViolations = currentViolationCount + 1
                val cooldownMs = calculateCooldownMs(nextViolations, level)
                return OutgoingSpamVerdict.Restricted(
                    untilEpochMs = now + cooldownMs,
                    reason = "Repeated identical messages detected.",
                    violationCount = nextViolations
                )
            }
        }

        val linkAnalysis = analyzeLinks(text)
        if (linkAnalysis.isHighRisk && level == SpamProtectionLevel.STRICT) {
            val nextViolations = currentViolationCount + 1
            val cooldownMs = calculateCooldownMs(nextViolations, level)
            return OutgoingSpamVerdict.Restricted(
                untilEpochMs = now + cooldownMs,
                reason = "High-risk phishing link blocked: ${linkAnalysis.reasons.firstOrNull().orEmpty()}",
                violationCount = nextViolations
            )
        }

        recordMessage(now, text, chatId)
        return if (linkAnalysis.hasSuspiciousLinks) {
            OutgoingSpamVerdict.Warning(
                linkAnalysis.reasons.firstOrNull() ?: "Message contains a suspicious URL."
            )
        } else {
            OutgoingSpamVerdict.Allowed
        }
    }

    @Synchronized
    fun clearHistory() {
        recentHistory.clear()
    }

    private fun recordMessage(now: Long, text: String, chatId: String) {
        recentHistory.add(
            SentRecord(
                timestampMs = now,
                normalizedText = normalizeText(text),
                chatId = chatId
            )
        )
    }

    private fun pruneOldRecords(now: Long) {
        recentHistory.removeAll { (now - it.timestampMs) > 65_000L }
    }

    private fun calculateCooldownMs(violationCount: Int, level: SpamProtectionLevel): Long {
        val baseMs = if (level == SpamProtectionLevel.STRICT) 45_000L else 25_000L
        val multiplier = violationCount.coerceIn(1, 5)
        return (baseMs * multiplier).coerceAtMost(300_000L)
    }

    companion object {
        private val URL_REGEX = Regex(
            """(?i)\b((?:https?://|www\.)[^\s<>"{}|\\^`\[\]]+)"""
        )
        private val IP_HOST_REGEX = Regex(
            """^(\d{1,3}\.){3}\d{1,3}(:\d+)?$"""
        )
        private val SUSPICIOUS_SHORTENERS = setOf(
            "bit.ly", "tinyurl.com", "goo.su", "is.gd", "cutt.ly", "rb.gy", "shorturl.at"
        )
        private val LOOKALIKE_KEYWORDS = listOf(
            "paypa1", "g00gle", "micr0soft", "app1e", "whats-app-verify",
            "verify-account", "claim-prize", "free-nitro", "wallet-connect-sync",
            "login-security-alert"
        )

        fun normalizeText(input: String): String =
            input.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")

        fun analyzeLinks(text: String): LinkSafetyResult {
            if (text.isBlank()) {
                return LinkSafetyResult(false, emptyList(), emptyList(), false)
            }
            val matches = URL_REGEX.findAll(text).map { it.value }.toList()
            if (matches.isEmpty()) {
                return LinkSafetyResult(false, emptyList(), emptyList(), false)
            }

            val suspicious = mutableListOf<String>()
            val reasons = mutableListOf<String>()
            var highRisk = false

            for (rawUrl in matches) {
                val lower = rawUrl.lowercase(Locale.US)
                val withoutScheme = lower
                    .removePrefix("https://")
                    .removePrefix("http://")
                val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                val host = authority.substringAfter('@').substringBefore(':')

                var flagged = false
                if (authority.contains('@')) {
                    reasons.add("Contains deceptive '@' credential redirect in URL")
                    highRisk = true
                    flagged = true
                }
                if (IP_HOST_REGEX.matches(host)) {
                    reasons.add("Uses raw IP address instead of a verified domain")
                    highRisk = true
                    flagged = true
                }
                if (host.contains("xn--")) {
                    reasons.add("Uses Punycode/IDN homograph characters")
                    highRisk = true
                    flagged = true
                }
                if (LOOKALIKE_KEYWORDS.any { lower.contains(it) }) {
                    reasons.add("Matches common phishing or lookalike pattern")
                    highRisk = true
                    flagged = true
                }
                if (SUSPICIOUS_SHORTENERS.any { host == it || host.endsWith(".$it") }) {
                    reasons.add("Uses masked URL shortener ($host)")
                    flagged = true
                }
                if (lower.startsWith("http://") && host !in setOf("localhost", "127.0.0.1")) {
                    reasons.add("Unencrypted HTTP link")
                    flagged = true
                }

                if (flagged) {
                    suspicious.add(rawUrl)
                }
            }

            return LinkSafetyResult(
                hasSuspiciousLinks = suspicious.isNotEmpty(),
                suspiciousUrls = suspicious.distinct(),
                reasons = reasons.distinct(),
                isHighRisk = highRisk
            )
        }
    }
}
