package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class ChatAccentOption(
    val wireKey: String,
    val label: String,
    val darkAccent: Color,
    val darkOnAccent: Color,
    val lightAccent: Color,
    val lightOnAccent: Color
) {
    DEFAULT(
        wireKey = "default",
        label = "Pulse Teal",
        darkAccent = ElectricTeal,
        darkOnAccent = DeepTealOnPrimary,
        lightAccent = DeepTealPrimary,
        lightOnAccent = CrispWhite
    ),
    COBALT(
        wireKey = "cobalt",
        label = "Work Cobalt",
        darkAccent = Color(0xFF2563EB),
        darkOnAccent = Color(0xFFFFFFFF),
        lightAccent = Color(0xFF1D4ED8),
        lightOnAccent = Color(0xFFFFFFFF)
    ),
    ROSE(
        wireKey = "rose",
        label = "Personal Rose",
        darkAccent = Color(0xFFE11D48),
        darkOnAccent = Color(0xFFFFFFFF),
        lightAccent = Color(0xFFBE123C),
        lightOnAccent = Color(0xFFFFFFFF)
    ),
    EMERALD(
        wireKey = "emerald",
        label = "Focus Emerald",
        darkAccent = Color(0xFF10B981),
        darkOnAccent = Color(0xFF042F2E),
        lightAccent = Color(0xFF047857),
        lightOnAccent = Color(0xFFFFFFFF)
    ),
    VIOLET(
        wireKey = "violet",
        label = "Creative Violet",
        darkAccent = Color(0xFF7C3AED),
        darkOnAccent = Color(0xFFFFFFFF),
        lightAccent = Color(0xFF6D28D9),
        lightOnAccent = Color(0xFFFFFFFF)
    ),
    AMBER(
        wireKey = "amber",
        label = "Priority Amber",
        darkAccent = Color(0xFFF59E0B),
        darkOnAccent = Color(0xFF1C1917),
        lightAccent = Color(0xFFB45309),
        lightOnAccent = Color(0xFFFFFFFF)
    );

    fun accentColor(isDarkTheme: Boolean): Color = if (isDarkTheme) darkAccent else lightAccent
    fun onAccentColor(isDarkTheme: Boolean): Color = if (isDarkTheme) darkOnAccent else lightOnAccent

    companion object {
        fun fromWire(value: String?): ChatAccentOption =
            entries.firstOrNull { it.wireKey == value } ?: DEFAULT
    }
}

enum class ChatBackgroundOption(
    val wireKey: String,
    val label: String
) {
    DEFAULT("default", "Clean"),
    SOFT_TINT("soft_tint", "Soft Tint"),
    AURORA_GLOW("aurora_glow", "Aurora Glow"),
    WORK_GRID("work_grid", "Work Grid"),
    PERSONAL_WAVES("personal_waves", "Personal Waves");

    companion object {
        fun fromWire(value: String?): ChatBackgroundOption =
            entries.firstOrNull { it.wireKey == value } ?: DEFAULT
    }
}

enum class ChatCategoryTag(
    val wireKey: String,
    val label: String
) {
    NONE("none", "None"),
    PERSONAL("personal", "Personal"),
    WORK("work", "Work"),
    PRIORITY("priority", "Priority");

    companion object {
        fun fromWire(value: String?): ChatCategoryTag =
            entries.firstOrNull { it.wireKey == value } ?: NONE
    }
}

data class ChatThemePreference(
    val accent: ChatAccentOption = ChatAccentOption.DEFAULT,
    val background: ChatBackgroundOption = ChatBackgroundOption.DEFAULT,
    val tag: ChatCategoryTag = ChatCategoryTag.NONE
) {
    val isDefault: Boolean
        get() = accent == ChatAccentOption.DEFAULT &&
            background == ChatBackgroundOption.DEFAULT &&
            tag == ChatCategoryTag.NONE

    fun toWireString(): String {
        if (isDefault) return ""
        return "accent=${accent.wireKey};bg=${background.wireKey};tag=${tag.wireKey}"
    }

    companion object {
        val Default = ChatThemePreference()

        val Presets: List<Pair<String, ChatThemePreference>> = listOf(
            "Default" to Default,
            "Personal" to ChatThemePreference(
                accent = ChatAccentOption.ROSE,
                background = ChatBackgroundOption.PERSONAL_WAVES,
                tag = ChatCategoryTag.PERSONAL
            ),
            "Work" to ChatThemePreference(
                accent = ChatAccentOption.COBALT,
                background = ChatBackgroundOption.WORK_GRID,
                tag = ChatCategoryTag.WORK
            ),
            "Focus" to ChatThemePreference(
                accent = ChatAccentOption.EMERALD,
                background = ChatBackgroundOption.SOFT_TINT,
                tag = ChatCategoryTag.WORK
            ),
            "Creative" to ChatThemePreference(
                accent = ChatAccentOption.VIOLET,
                background = ChatBackgroundOption.AURORA_GLOW,
                tag = ChatCategoryTag.NONE
            ),
            "Priority" to ChatThemePreference(
                accent = ChatAccentOption.AMBER,
                background = ChatBackgroundOption.AURORA_GLOW,
                tag = ChatCategoryTag.PRIORITY
            )
        )

        fun fromWireString(raw: String?): ChatThemePreference {
            if (raw.isNullOrBlank()) return Default
            val parts = raw.split(";")
                .mapNotNull { segment ->
                    val idx = segment.indexOf('=')
                    if (idx <= 0) null
                    else segment.substring(0, idx).trim() to segment.substring(idx + 1).trim()
                }
                .toMap()
            return ChatThemePreference(
                accent = ChatAccentOption.fromWire(parts["accent"]),
                background = ChatBackgroundOption.fromWire(parts["bg"]),
                tag = ChatCategoryTag.fromWire(parts["tag"])
            )
        }
    }
}
