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

private val Lato = FontFamily(
    Font(R.font.lato_light, weight = FontWeight.Light),
    Font(R.font.lato_regular, weight = FontWeight.Normal),
    Font(R.font.lato_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(R.font.lato_bold, weight = FontWeight.Bold),
    Font(R.font.lato_bolditalic, weight = FontWeight.Bold, style = FontStyle.Italic),
    Font(R.font.lato_black, weight = FontWeight.Black),
)

private val ColorScheme = darkColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    background = Color(0xFF0A0A0A),
    onBackground = Color.White,
    surface = Color(0xFF141414),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFAAAAAA),
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
            colorScheme = ColorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}