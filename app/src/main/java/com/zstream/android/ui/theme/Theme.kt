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

val LatoFontFamily = FontFamily(
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
        displayLarge = displayLarge.copy(fontFamily = LatoFontFamily),
        displayMedium = displayMedium.copy(fontFamily = LatoFontFamily),
        displaySmall = displaySmall.copy(fontFamily = LatoFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = LatoFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = LatoFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = LatoFontFamily),
        titleLarge = titleLarge.copy(fontFamily = LatoFontFamily),
        titleMedium = titleMedium.copy(fontFamily = LatoFontFamily),
        titleSmall = titleSmall.copy(fontFamily = LatoFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = LatoFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = LatoFontFamily),
        bodySmall = bodySmall.copy(fontFamily = LatoFontFamily),
        labelLarge = labelLarge.copy(fontFamily = LatoFontFamily),
        labelMedium = labelMedium.copy(fontFamily = LatoFontFamily),
        labelSmall = labelSmall.copy(fontFamily = LatoFontFamily),
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
