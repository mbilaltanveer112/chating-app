package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PulseDarkColorScheme = darkColorScheme(
    primary = ElectricTeal,
    onPrimary = DeepTealOnPrimary,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = ElectricTeal,
    secondary = SkyCyan,
    onSecondary = DeepObsidian,
    secondaryContainer = SkyContainerDark,
    onSecondaryContainer = SkyCyan,
    tertiary = CoralPulse,
    onTertiary = DeepObsidian,
    background = DeepObsidian,
    onBackground = TextPrimaryDark,
    surface = MidnightSlate,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedNavy,
    onSurfaceVariant = TextMutedDark,
    outline = SubtleBorderDark,
    error = CoralPulse,
    onError = DeepObsidian
)

private val PulseLightColorScheme = lightColorScheme(
    primary = DeepTealPrimary,
    onPrimary = CrispWhite,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = DeepTealPrimary,
    secondary = OceanBlue,
    onSecondary = CrispWhite,
    secondaryContainer = OceanContainerLight,
    onSecondaryContainer = OceanBlue,
    tertiary = CoralRoseLight,
    onTertiary = CrispWhite,
    background = PorcelainBg,
    onBackground = TextPrimaryLight,
    surface = CrispWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = CoolSlateSurface,
    onSurfaceVariant = TextMutedLight,
    outline = SubtleBorderLight,
    error = CoralRoseLight,
    onError = CrispWhite
)

private val AuthDarkColorScheme = darkColorScheme(
    primary = AuthElectricBlue,
    onPrimary = AuthPureWhite,
    primaryContainer = AuthBlueContainerDark,
    onPrimaryContainer = AuthPureWhite,
    secondary = AuthPureWhite,
    onSecondary = AuthPitchBlack,
    secondaryContainer = AuthSurfaceElevatedBlack,
    onSecondaryContainer = AuthPureWhite,
    tertiary = OnlineGreen,
    onTertiary = AuthPitchBlack,
    background = AuthPitchBlack,
    onBackground = AuthPureWhite,
    surface = AuthSurfaceBlack,
    onSurface = AuthPureWhite,
    surfaceVariant = AuthSurfaceElevatedBlack,
    onSurfaceVariant = AuthMutedWhite,
    outline = AuthBorderDark,
    error = CoralPulse,
    onError = AuthPitchBlack
)

private val AuthLightColorScheme = lightColorScheme(
    primary = AuthCobaltBlue,
    onPrimary = AuthPureWhite,
    primaryContainer = AuthBlueContainerLight,
    onPrimaryContainer = AuthCobaltBlue,
    secondary = AuthInkBlack,
    onSecondary = AuthPureWhite,
    secondaryContainer = AuthSurfaceVariantWhite,
    onSecondaryContainer = AuthInkBlack,
    tertiary = OnlineGreen,
    onTertiary = AuthPureWhite,
    background = AuthSnowWhiteBg,
    onBackground = AuthInkBlack,
    surface = AuthPureWhite,
    onSurface = AuthInkBlack,
    surfaceVariant = AuthSurfaceVariantWhite,
    onSurfaceVariant = AuthMutedInk,
    outline = AuthBorderLight,
    error = CoralRoseLight,
    onError = AuthPureWhite
)

private val PulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PulseChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PulseDarkColorScheme else PulseLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseChatTypography,
        shapes = PulseShapes,
        content = content
    )
}

@Composable
fun PulseAuthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AuthDarkColorScheme else AuthLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseChatTypography,
        shapes = PulseShapes,
        content = content
    )
}

