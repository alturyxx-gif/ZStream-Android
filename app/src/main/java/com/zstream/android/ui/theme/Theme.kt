package com.zstream.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zstream.android.R
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ThemeViewModel
import com.zstream.android.theme.ZStreamTheme

private val Lato = FontFamily(
    Font(R.font.lato_light, weight = FontWeight.Light),
    Font(R.font.lato_regular, weight = FontWeight.Normal),
    Font(R.font.lato_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(R.font.lato_bold, weight = FontWeight.Bold),
    Font(R.font.lato_bolditalic, weight = FontWeight.Bold, style = FontStyle.Italic),
    Font(R.font.lato_black, weight = FontWeight.Black),
)

private fun colorSchemeFor(theme: ZStreamTheme) = darkColorScheme(
    primary = theme.colors.global.accentA,
    onPrimary = theme.colors.buttons.primaryText,
    background = theme.colors.background.main,
    onBackground = theme.colors.type.emphasis,
    surface = theme.colors.settings.card.background,
    onSurface = theme.colors.type.emphasis,
    surfaceVariant = theme.colors.background.secondary,
    onSurfaceVariant = theme.colors.type.secondary,
    secondary = theme.colors.buttons.purple,
    onSecondary = Color.White,
    tertiary = theme.colors.type.link,
    onTertiary = theme.colors.type.emphasis,
    error = theme.colors.type.danger,
    onError = Color.White,
    outline = theme.colors.type.divider,
)

private val AppTypography = with(Typography()) {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Lato),
        displayMedium = displayMedium.copy(fontFamily = Lato),
        displaySmall = displaySmall.copy(fontFamily = Lato),
        headlineLarge = headlineLarge.copy(fontFamily = Lato),
        headlineMedium = headlineMedium.copy(fontFamily = Lato),
        headlineSmall = headlineSmall.copy(fontFamily = Lato),
        titleLarge = titleLarge.copy(fontFamily = Lato),
        titleMedium = titleMedium.copy(fontFamily = Lato),
        titleSmall = titleSmall.copy(fontFamily = Lato),
        bodyLarge = bodyLarge.copy(fontFamily = Lato),
        bodyMedium = bodyMedium.copy(fontFamily = Lato),
        bodySmall = bodySmall.copy(fontFamily = Lato),
        labelLarge = labelLarge.copy(fontFamily = Lato),
        labelMedium = labelMedium.copy(fontFamily = Lato),
        labelSmall = labelSmall.copy(fontFamily = Lato),
    )
}

@Composable
fun ZStreamTheme(content: @Composable () -> Unit) {
    val themeVm: ThemeViewModel = viewModel()
    val currentTheme = themeVm.currentTheme.value

    androidx.compose.runtime.CompositionLocalProvider(
        LocalZStreamTheme provides currentTheme
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(currentTheme),
            typography = AppTypography,
            content = content,
        )
    }
}