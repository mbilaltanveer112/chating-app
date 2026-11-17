package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class PresenceFormatterTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun utcMillis(
        year: Int,
        monthZeroBased: Int,
        day: Int,
        hour24: Int,
        minute: Int
    ): Long = Calendar.getInstance(utc, Locale.US).apply {
        clear()
        set(year, monthZeroBased, day, hour24, minute, 0)
    }.timeInMillis

    @Test
    fun formatLastSeen_handlesTodayYesterdayAndCalendarDate() {
        val now = utcMillis(2026, Calendar.NOVEMBER, 15, 16, 0)
        val todayEarlier = utcMillis(2026, Calendar.NOVEMBER, 15, 13, 14)
        val yesterday = utcMillis(2026, Calendar.NOVEMBER, 14, 20, 30)
        val earlierMonth = utcMillis(2026, Calendar.NOVEMBER, 10, 18, 45)
        val priorYear = utcMillis(2025, Calendar.NOVEMBER, 14, 9, 5)

        assertEquals(
            "Last seen today at 1:14 PM",
            PresenceFormatter.formatLastSeenFull(todayEarlier, now, utc, Locale.US)
        )
        assertEquals(
            "Last seen yesterday at 8:30 PM",
            PresenceFormatter.formatLastSeenFull(yesterday, now, utc, Locale.US)
        )
        assertEquals(
            "Last seen on November 10 at 6:45 PM",
            PresenceFormatter.formatLastSeenFull(earlierMonth, now, utc, Locale.US)
        )
        assertEquals(
            "Last seen on November 14, 2025 at 9:05 AM",
            PresenceFormatter.formatLastSeenFull(priorYear, now, utc, Locale.US)
        )
        assertEquals("", PresenceFormatter.formatLastSeenFull(0L, now, utc, Locale.US))
    }
}
