package com.animecliplabs.clipmark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A fixed brand palette (not dynamic colour): the app is part of a creator's identity, so it
// looks the same on every phone.
private val Light = lightColorScheme(
    primary = Color(0xFF5B3FE0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1C0A5E),
    secondary = Color(0xFF5E5C71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E0F9),
    onSecondaryContainer = Color(0xFF1A1A2C),
    tertiary = Color(0xFFB0305A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E1),
    onTertiaryContainer = Color(0xFF3F0019),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE5E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0ECF4),
    surfaceContainerHigh = Color(0xFFEAE7EF),
    surfaceContainerHighest = Color(0xFFE5E1E9),
    outline = Color(0xFF78767F),
    outlineVariant = Color(0xFFC8C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFCBBEFF),
    onPrimary = Color(0xFF2F1A8A),
    primaryContainer = Color(0xFF4630C6),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFC7C4DC),
    onSecondary = Color(0xFF2F2F42),
    secondaryContainer = Color(0xFF464559),
    onSecondaryContainer = Color(0xFFE3E0F9),
    tertiary = Color(0xFFFFB1C4),
    onTertiary = Color(0xFF65002E),
    tertiaryContainer = Color(0xFF8E1543),
    onTertiaryContainer = Color(0xFFFFD9E1),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
    outline = Color(0xFF928F99),
    outlineVariant = Color(0xFF47464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val BaseType = Typography()
private val AppTypography = BaseType.copy(
    displaySmall = BaseType.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ClipMarkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
