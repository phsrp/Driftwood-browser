package com.driftwood.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.driftwood.browser.R
import com.driftwood.browser.data.AppSettings

/** The palette in use, for the few places that need colours Material doesn't name. */
val LocalPalette = staticCompositionLocalOf { ThemeId.ROSE_PINE_DAWN.palette }

@OptIn(ExperimentalTextApi::class)
private fun montserrat(weight: Int) = Font(
    R.font.montserrat,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Montserrat, bundled in the app (a variable font, so one file has every weight). */
val Montserrat = FontFamily(montserrat(400), montserrat(500), montserrat(600), montserrat(700))

private val AppTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Montserrat),
        displayMedium = displayMedium.copy(fontFamily = Montserrat),
        displaySmall = displaySmall.copy(fontFamily = Montserrat),
        headlineLarge = headlineLarge.copy(fontFamily = Montserrat),
        headlineMedium = headlineMedium.copy(fontFamily = Montserrat),
        headlineSmall = headlineSmall.copy(fontFamily = Montserrat),
        titleLarge = titleLarge.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = Montserrat),
        bodyMedium = bodyMedium.copy(fontFamily = Montserrat),
        bodySmall = bodySmall.copy(fontFamily = Montserrat),
        labelLarge = labelLarge.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = Montserrat),
        labelSmall = labelSmall.copy(fontFamily = Montserrat),
    )
}

@Composable
fun activePalette(settings: AppSettings): Palette {
    if (!settings.followSystem) return settings.theme.palette
    return if (isSystemInDarkTheme()) settings.darkTheme.palette else settings.lightTheme.palette
}

@Composable
fun DriftwoodTheme(palette: Palette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = palette.toColorScheme(), typography = AppTypography) {
            // Text and icons without their own colour use the theme text colour (light on dark themes).
            CompositionLocalProvider(LocalContentColor provides palette.text, content = content)
        }
    }
}

private fun Palette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = surfaceHigh,
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = subtle,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceHigh,
        inverseSurface = text,
        inverseOnSurface = background,
        outline = muted,
        outlineVariant = surfaceHigh,
        error = danger,
        onError = onAccent,
    )
}
