package com.example.security

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats Last Seen timestamps using WhatsApp-style relative wording:
 * - "today at 1:14 PM"
 * - "yesterday at 8:30 PM"
 * - "on November 14 at 6:45 PM"
 * - "on November 14, 2025 at 6:45 PM" (if from an earlier year)
 */
object PresenceFormatter {

    fun formatRelativeLastSeen(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        if (timestampMillis <= 0L) return ""

        val thenCal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = timestampMillis
        }
        val nowCal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = nowMillis
        }

        val timeFormat = SimpleDateFormat("h:mm a", locale).apply {
            this.timeZone = timeZone
        }
        val timeText = timeFormat.format(Date(timestampMillis))

        val sameYear = thenCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
        val sameDay = sameYear && thenCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
        if (sameDay) {
            return "today at $timeText"
        }

        val yesterdayCal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = thenCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
            thenCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            return "yesterday at $timeText"
        }

        val datePattern = if (sameYear) "MMMM d" else "MMMM d, yyyy"
        val dateFormat = SimpleDateFormat(datePattern, locale).apply {
            this.timeZone = timeZone
        }
        return "on ${dateFormat.format(Date(timestampMillis))} at $timeText"
    }

    fun formatLastSeenFull(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val relative = formatRelativeLastSeen(timestampMillis, nowMillis, timeZone, locale)
        return if (relative.isBlank()) "" else "Last seen $relative"
    }
}
