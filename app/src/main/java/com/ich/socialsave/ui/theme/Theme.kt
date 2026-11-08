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

/** Neutral base, dark actions and green accents. */
@Immutable
data class BrandColors(
    val canvas: Color,
    val surface: Color,
    /** Filled pills, thumbnails' empty state and the track of segmented controls. */
    val muted: Color,
    val ink: Color,
    val gray: Color,
    val iconMuted: Color,
    val line: Color,
    val danger: Color,
    /** Primary buttons. */
    val action: Color,
    val onAction: Color,
    /** Bright fill for the add button, checks and favorite badges. Content on it is always [onMint]. */
    val mint: Color,
    val onMint: Color,
    /** Outline of selected items. */
    val accent: Color,
    /** Accent words in large headlines. */
    val accentHeadline: Color,
    /** Small accent text and icons. */
    val accentText: Color,
    /** Fill behind selected chips and the navigation indicator. */
    val accentSoft: Color,
    val isDark: Boolean,
)

val LightBrand = BrandColors(
    canvas = Color(0xFFFCFCFC),
    surface = Color(0xFFFFFFFF),
    muted = Color(0xFFF2F2F2),
    ink = Color(0xFF141414),
    gray = Color(0xFF6E6E6E),
    iconMuted = Color(0xFF8A8A8A),
    line = Color(0xFFE8E8E8),
    danger = Color(0xFFC93A28),
    action = Color(0xFF2C2C2C),
    onAction = Color(0xFFFFFFFF),
    mint = Color(0xFF52ECB8),
    onMint = Color(0xFF141414),
    accent = Color(0xFF14B482),
    accentHeadline = Color(0xFF0FA070),
    accentText = Color(0xFF0A7F5A),
    accentSoft = Color(0xFFE3FAF1),
    isDark = false,
)

val DarkBrand = BrandColors(
    canvas = Color(0xFF101110),
    surface = Color(0xFF1A1B1A),
    muted = Color(0xFF242524),
    ink = Color(0xFFF5F5F5),
    gray = Color(0xFFA3A3A3),
    iconMuted = Color(0xFF8C8C8C),
    line = Color(0xFF2C2E2C),
    danger = Color(0xFFF07A66),
    action = Color(0xFFF5F5F5),
    onAction = Color(0xFF141414),
    mint = Color(0xFF52ECB8),
    onMint = Color(0xFF141414),
    accent = Color(0xFF52ECB8),
    accentHeadline = Color(0xFF52ECB8),
    accentText = Color(0xFF52ECB8),
    accentSoft = Color(0xFF183C30),
    isDark = true,
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

object Brand {
    val colors: BrandColors @Composable get() = LocalBrand.current
    val cardRadius = 20.dp
    val controlRadius = 16.dp
    val chipHeight = 40.dp
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
        tertiary = brand.mint,
        onTertiary = brand.onMint,
        tertiaryContainer = brand.accentSoft,
        onTertiaryContainer = brand.ink,
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
        headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp),
        bodyMedium = TextStyle(fontSize = 16.sp),
        bodySmall = TextStyle(fontSize = 13.sp),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = Shapes(
                small = RoundedCornerShape(Brand.controlRadius),
                medium = RoundedCornerShape(Brand.cardRadius),
                large = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
