package codes.cy.portfolio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class PortfolioThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun nextToggleMode(isCurrentlyDark: Boolean): PortfolioThemeMode =
        if (isCurrentlyDark) LIGHT else DARK
}

data class PortfolioEditorialColors(
    val hairlineBorder: Color,
    val codeContainer: Color,
    val codeBorder: Color,
    val accentSurface: Color,
    val mutedText: Color,
    val statusActive: Color,
    val statusPrototype: Color,
    val statusArchived: Color,
)

private object EditorialPaletteHex {
    // Paper & Ink (Light)
    const val LIGHT_BG: Long = 0xFFFAF9F6L
    const val LIGHT_SURFACE: Long = 0xFFFFFFFFL
    const val LIGHT_SURFACE_VARIANT: Long = 0xFFF2F0EBL
    const val LIGHT_SURFACE_CONTAINER: Long = 0xFFF5F3EEL
    const val LIGHT_PRIMARY: Long = 0xFF1D4ED8L
    const val LIGHT_ON_PRIMARY: Long = 0xFFFFFFFFL
    const val LIGHT_PRIMARY_CONTAINER: Long = 0xFFDBEAFEL
    const val LIGHT_ON_PRIMARY_CONTAINER: Long = 0xFF1E3A8AL
    const val LIGHT_SECONDARY: Long = 0xFF0F766EL
    const val LIGHT_ON_SECONDARY: Long = 0xFFFFFFFFL
    const val LIGHT_SECONDARY_CONTAINER: Long = 0xFFCCFBF1L
    const val LIGHT_ON_SECONDARY_CONTAINER: Long = 0xFF115E59L
    const val LIGHT_INK: Long = 0xFF141619L
    const val LIGHT_MUTED: Long = 0xFF525866L
    const val LIGHT_OUTLINE: Long = 0xFFD5D0C5L
    const val LIGHT_HAIRLINE: Long = 0xFFE2DFD7L
    const val LIGHT_CODE_BG: Long = 0xFFF3F1ECL
    const val LIGHT_CODE_BORDER: Long = 0xFFDFDBD2L
    const val LIGHT_ACCENT_SURFACE: Long = 0xFFEFF4FFL
    const val LIGHT_ERROR: Long = 0xFFB91C1CL
    const val LIGHT_ERROR_CONTAINER: Long = 0xFFFEE2E2L
    const val LIGHT_ACTIVE: Long = 0xFF047857L
    const val LIGHT_PROTOTYPE: Long = 0xFFB45309L
    const val LIGHT_ARCHIVED: Long = 0xFF6B7280L

    // Obsidian & Chalk (Dark)
    const val DARK_BG: Long = 0xFF0E1116L
    const val DARK_SURFACE: Long = 0xFF161B22L
    const val DARK_SURFACE_VARIANT: Long = 0xFF1E2530L
    const val DARK_SURFACE_CONTAINER: Long = 0xFF1A2029L
    const val DARK_PRIMARY: Long = 0xFF60A5FAL
    const val DARK_ON_PRIMARY: Long = 0xFF0B192CL
    const val DARK_PRIMARY_CONTAINER: Long = 0xFF1E3A5FL
    const val DARK_ON_PRIMARY_CONTAINER: Long = 0xFFDBEAFEL
    const val DARK_SECONDARY: Long = 0xFF2DD4BFL
    const val DARK_ON_SECONDARY: Long = 0xFF042F2EL
    const val DARK_SECONDARY_CONTAINER: Long = 0xFF134E4AL
    const val DARK_ON_SECONDARY_CONTAINER: Long = 0xFF99F6E4L
    const val DARK_CHALK: Long = 0xFFECEFF4L
    const val DARK_MUTED: Long = 0xFF9AA4B2L
    const val DARK_OUTLINE: Long = 0xFF343D4BL
    const val DARK_HAIRLINE: Long = 0xFF2A313CL
    const val DARK_CODE_BG: Long = 0xFF11161DL
    const val DARK_CODE_BORDER: Long = 0xFF28303CL
    const val DARK_ACCENT_SURFACE: Long = 0xFF162235L
    const val DARK_ERROR: Long = 0xFFF87171L
    const val DARK_ERROR_CONTAINER: Long = 0xFF7F1D1DL
    const val DARK_ACTIVE: Long = 0xFF34D399L
    const val DARK_PROTOTYPE: Long = 0xFFFBBF24L
    const val DARK_ARCHIVED: Long = 0xFF9CA3AFL
}

private val LightPortfolioColorScheme: ColorScheme = lightColorScheme(
    primary = Color(EditorialPaletteHex.LIGHT_PRIMARY),
    onPrimary = Color(EditorialPaletteHex.LIGHT_ON_PRIMARY),
    primaryContainer = Color(EditorialPaletteHex.LIGHT_PRIMARY_CONTAINER),
    onPrimaryContainer = Color(EditorialPaletteHex.LIGHT_ON_PRIMARY_CONTAINER),
    secondary = Color(EditorialPaletteHex.LIGHT_SECONDARY),
    onSecondary = Color(EditorialPaletteHex.LIGHT_ON_SECONDARY),
    secondaryContainer = Color(EditorialPaletteHex.LIGHT_SECONDARY_CONTAINER),
    onSecondaryContainer = Color(EditorialPaletteHex.LIGHT_ON_SECONDARY_CONTAINER),
    background = Color(EditorialPaletteHex.LIGHT_BG),
    onBackground = Color(EditorialPaletteHex.LIGHT_INK),
    surface = Color(EditorialPaletteHex.LIGHT_SURFACE),
    onSurface = Color(EditorialPaletteHex.LIGHT_INK),
    surfaceVariant = Color(EditorialPaletteHex.LIGHT_SURFACE_VARIANT),
    onSurfaceVariant = Color(EditorialPaletteHex.LIGHT_MUTED),
    surfaceContainerLow = Color(EditorialPaletteHex.LIGHT_SURFACE_CONTAINER),
    outline = Color(EditorialPaletteHex.LIGHT_OUTLINE),
    outlineVariant = Color(EditorialPaletteHex.LIGHT_HAIRLINE),
    error = Color(EditorialPaletteHex.LIGHT_ERROR),
    errorContainer = Color(EditorialPaletteHex.LIGHT_ERROR_CONTAINER),
)

private val DarkPortfolioColorScheme: ColorScheme = darkColorScheme(
    primary = Color(EditorialPaletteHex.DARK_PRIMARY),
    onPrimary = Color(EditorialPaletteHex.DARK_ON_PRIMARY),
    primaryContainer = Color(EditorialPaletteHex.DARK_PRIMARY_CONTAINER),
    onPrimaryContainer = Color(EditorialPaletteHex.DARK_ON_PRIMARY_CONTAINER),
    secondary = Color(EditorialPaletteHex.DARK_SECONDARY),
    onSecondary = Color(EditorialPaletteHex.DARK_ON_SECONDARY),
    secondaryContainer = Color(EditorialPaletteHex.DARK_SECONDARY_CONTAINER),
    onSecondaryContainer = Color(EditorialPaletteHex.DARK_ON_SECONDARY_CONTAINER),
    background = Color(EditorialPaletteHex.DARK_BG),
    onBackground = Color(EditorialPaletteHex.DARK_CHALK),
    surface = Color(EditorialPaletteHex.DARK_SURFACE),
    onSurface = Color(EditorialPaletteHex.DARK_CHALK),
    surfaceVariant = Color(EditorialPaletteHex.DARK_SURFACE_VARIANT),
    onSurfaceVariant = Color(EditorialPaletteHex.DARK_MUTED),
    surfaceContainerLow = Color(EditorialPaletteHex.DARK_SURFACE_CONTAINER),
    outline = Color(EditorialPaletteHex.DARK_OUTLINE),
    outlineVariant = Color(EditorialPaletteHex.DARK_HAIRLINE),
    error = Color(EditorialPaletteHex.DARK_ERROR),
    errorContainer = Color(EditorialPaletteHex.DARK_ERROR_CONTAINER),
)

private val LightEditorialColors = PortfolioEditorialColors(
    hairlineBorder = Color(EditorialPaletteHex.LIGHT_HAIRLINE),
    codeContainer = Color(EditorialPaletteHex.LIGHT_CODE_BG),
    codeBorder = Color(EditorialPaletteHex.LIGHT_CODE_BORDER),
    accentSurface = Color(EditorialPaletteHex.LIGHT_ACCENT_SURFACE),
    mutedText = Color(EditorialPaletteHex.LIGHT_MUTED),
    statusActive = Color(EditorialPaletteHex.LIGHT_ACTIVE),
    statusPrototype = Color(EditorialPaletteHex.LIGHT_PROTOTYPE),
    statusArchived = Color(EditorialPaletteHex.LIGHT_ARCHIVED),
)

private val DarkEditorialColors = PortfolioEditorialColors(
    hairlineBorder = Color(EditorialPaletteHex.DARK_HAIRLINE),
    codeContainer = Color(EditorialPaletteHex.DARK_CODE_BG),
    codeBorder = Color(EditorialPaletteHex.DARK_CODE_BORDER),
    accentSurface = Color(EditorialPaletteHex.DARK_ACCENT_SURFACE),
    mutedText = Color(EditorialPaletteHex.DARK_MUTED),
    statusActive = Color(EditorialPaletteHex.DARK_ACTIVE),
    statusPrototype = Color(EditorialPaletteHex.DARK_PROTOTYPE),
    statusArchived = Color(EditorialPaletteHex.DARK_ARCHIVED),
)

val PortfolioTypography: Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.25.sp,
    ),
)

val LocalPortfolioColors = staticCompositionLocalOf { LightEditorialColors }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

object PortfolioThemeDefaults {
    val colors: PortfolioEditorialColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPortfolioColors.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsDarkTheme.current
}

@Composable
fun PortfolioTheme(
    mode: PortfolioThemeMode = PortfolioThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (mode) {
        PortfolioThemeMode.SYSTEM -> systemDark
        PortfolioThemeMode.LIGHT -> false
        PortfolioThemeMode.DARK -> true
    }

    val colorScheme = if (useDark) DarkPortfolioColorScheme else LightPortfolioColorScheme
    val editorialColors = if (useDark) DarkEditorialColors else LightEditorialColors

    CompositionLocalProvider(
        LocalPortfolioColors provides editorialColors,
        LocalIsDarkTheme provides useDark,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PortfolioTypography,
            content = content,
        )
    }
}
