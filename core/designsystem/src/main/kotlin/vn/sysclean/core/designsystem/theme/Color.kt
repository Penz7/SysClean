package vn.sysclean.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand palette: a fresh teal that reads as "clean" without clashing with warning/error states.
internal val Teal10 = Color(0xFF002019)
internal val Teal20 = Color(0xFF00382D)
internal val Teal30 = Color(0xFF005142)
internal val Teal40 = Color(0xFF006B57)
internal val Teal80 = Color(0xFF5CDBB9)
internal val Teal90 = Color(0xFF7AF8D4)

internal val Blue30 = Color(0xFF004A77)
internal val Blue40 = Color(0xFF00639D)
internal val Blue80 = Color(0xFF96CCFF)
internal val Blue90 = Color(0xFFCEE5FF)

internal val Slate10 = Color(0xFF171D1B)
internal val Slate20 = Color(0xFF2B3230)
internal val Slate90 = Color(0xFFDDE4E0)
internal val Slate95 = Color(0xFFECF2EE)
internal val Slate99 = Color(0xFFF7FBF8)

internal val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    inversePrimary = Teal80,
    secondary = Color(0xFF4B635B),
    secondaryContainer = Color(0xFFCDE9DE),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Blue40,
    tertiaryContainer = Blue90,
    onTertiaryContainer = Color(0xFF001D33),
    background = Slate99,
    onBackground = Slate10,
    surface = Slate99,
    onSurface = Slate10,
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Slate95,
    surfaceContainerHigh = Color(0xFFE6ECE9),
    surfaceContainerHighest = Slate90,
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
)

internal val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    inversePrimary = Teal40,
    secondary = Color(0xFFB2CCC2),
    secondaryContainer = Color(0xFF344C44),
    onSecondaryContainer = Color(0xFFCDE9DE),
    tertiary = Blue80,
    tertiaryContainer = Blue30,
    onTertiaryContainer = Blue90,
    background = Color(0xFF0F1513),
    onBackground = Slate90,
    surface = Color(0xFF0F1513),
    onSurface = Slate90,
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Slate10,
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Slate20,
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
)

/** Status colors Material 3 does not define but a system-health app needs everywhere. */
@Immutable
data class StatusColors(
    val good: Color,
    val onGood: Color,
    val warning: Color,
    val onWarning: Color,
    val critical: Color,
    val onCritical: Color,
)

internal val LightStatusColors = StatusColors(
    good = Color(0xFF1E8E5A),
    onGood = Color.White,
    warning = Color(0xFFB26B00),
    onWarning = Color.White,
    critical = Color(0xFFBA1A1A),
    onCritical = Color.White,
)

internal val DarkStatusColors = StatusColors(
    good = Color(0xFF6DD9A0),
    onGood = Color(0xFF00391F),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF462A00),
    critical = Color(0xFFFFB4AB),
    onCritical = Color(0xFF690005),
)

internal val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
