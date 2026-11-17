package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatThemeConfigTest {

    @Test
    fun toWireString_andFromWireString_roundTripCustomTheme() {
        val custom = ChatThemePreference(
            accent = ChatAccentOption.COBALT,
            background = ChatBackgroundOption.WORK_GRID,
            tag = ChatCategoryTag.WORK
        )

        assertFalse(custom.isDefault)
        val wire = custom.toWireString()
        assertEquals("accent=cobalt;bg=work_grid;tag=work", wire)
        assertEquals(custom, ChatThemePreference.fromWireString(wire))
    }

    @Test
    fun defaultTheme_serializesToEmptyAndDeserializesSafely() {
        assertTrue(ChatThemePreference.Default.isDefault)
        assertEquals("", ChatThemePreference.Default.toWireString())
        assertEquals(ChatThemePreference.Default, ChatThemePreference.fromWireString(null))
        assertEquals(ChatThemePreference.Default, ChatThemePreference.fromWireString(""))
        assertEquals(ChatThemePreference.Default, ChatThemePreference.fromWireString("   "))
    }

    @Test
    fun fromWireString_handlesMalformedAndUnknownValuesGracefully() {
        val parsed = ChatThemePreference.fromWireString("accent=unknown_color;bg=personal_waves;badsegment;tag=personal")
        assertEquals(ChatAccentOption.DEFAULT, parsed.accent)
        assertEquals(ChatBackgroundOption.PERSONAL_WAVES, parsed.background)
        assertEquals(ChatCategoryTag.PERSONAL, parsed.tag)
    }

    @Test
    fun presets_includePersonalAndWorkAndRoundTrip() {
        val presetMap = ChatThemePreference.Presets.toMap()
        val personal = presetMap["Personal"]!!
        val work = presetMap["Work"]!!

        assertEquals(ChatCategoryTag.PERSONAL, personal.tag)
        assertEquals(ChatAccentOption.ROSE, personal.accent)
        assertEquals(personal, ChatThemePreference.fromWireString(personal.toWireString()))

        assertEquals(ChatCategoryTag.WORK, work.tag)
        assertEquals(ChatAccentOption.COBALT, work.accent)
        assertEquals(work, ChatThemePreference.fromWireString(work.toWireString()))
    }
}
