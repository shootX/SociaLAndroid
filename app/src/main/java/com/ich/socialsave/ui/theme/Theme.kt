package com.ich.socialsave.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ich.socialsave.data.Appearance

/** Warm-neutral monochrome palette, identical to the iOS app's Brand colors. */
@Immutable
data class BrandColors(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val gray: Color,
    val line: Color,
    val danger: Color,
    val isDark: Boolean,
)

val LightBrand = BrandColors(
    canvas = Color(0xFFF6F5F2),
    surface = Color(0xFFFFFDFA),
    ink = Color(0xFF1C1B19),
    gray = Color(0xFF8A867C),
    line = Color(0xFFE3E0D8),
    danger = Color(0xFFB04030),
    isDark = false,
)

val DarkBrand = BrandColors(
    canvas = Color(0xFF121211),
    surface = Color(0xFF1C1C1A),
    ink = Color(0xFFF3F1EB),
    gray = Color(0xFF9C978C),
    line = Color(0xFF2C2B28),
    danger = Color(0xFFE87868),
    isDark = true,
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

object Brand {
    val colors: BrandColors @Composable get() = LocalBrand.current
    val cardRadius = 14.dp
    val controlRadius = 12.dp
    val chipHeight = 34.dp
}

@Composable
fun isDarkTheme(appearance: Appearance): Boolean = when (appearance) {
    Appearance.SYSTEM -> isSystemInDarkTheme()
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

@Composable
fun SocialSaveTheme(appearance: Appearance = Appearance.SYSTEM, content: @Composable () -> Unit) {
    val brand = if (isDarkTheme(appearance)) DarkBrand else LightBrand
    val base = if (brand.isDark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = brand.ink,
        onPrimary = brand.canvas,
        primaryContainer = brand.surface,
        onPrimaryContainer = brand.ink,
        secondary = brand.gray,
        onSecondary = brand.canvas,
        secondaryContainer = brand.line,
        onSecondaryContainer = brand.ink,
        background = brand.canvas,
        onBackground = brand.ink,
        surface = brand.canvas,
        onSurface = brand.ink,
        surfaceVariant = brand.surface,
        onSurfaceVariant = brand.gray,
        surfaceContainerLowest = brand.surface,
        surfaceContainerLow = brand.surface,
        surfaceContainer = brand.surface,
        surfaceContainerHigh = brand.surface,
        surfaceContainerHighest = brand.line,
        outline = brand.line,
        outlineVariant = brand.line,
        error = brand.danger,
    )
    val typography = Typography(
        headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp),
        bodyMedium = TextStyle(fontSize = 15.sp),
        bodySmall = TextStyle(fontSize = 13.sp),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = Shapes(
                small = RoundedCornerShape(Brand.controlRadius),
                medium = RoundedCornerShape(Brand.cardRadius),
                large = RoundedCornerShape(20.dp),
            ),
            content = content,
        )
    }
}
