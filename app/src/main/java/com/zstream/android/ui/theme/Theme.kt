package com.zstream.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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

val OnestFontFamily = FontFamily(
    Font(R.font.onest_light, weight = FontWeight.Light),
    Font(R.font.onest_regular, weight = FontWeight.Normal),
    Font(R.font.onest_medium, weight = FontWeight.Medium),
    Font(R.font.onest_semibold, weight = FontWeight.SemiBold),
    Font(R.font.onest_bold, weight = FontWeight.Bold),
    Font(R.font.onest_black, weight = FontWeight.Black),
)

val GoogleSansFontFamily = FontFamily(
    Font(R.font.googlesans_regular, weight = FontWeight.Normal),
    Font(R.font.googlesans_medium, weight = FontWeight.Medium),
    Font(R.font.googlesans_bold, weight = FontWeight.Bold),
)

val SpecialGothicExpandedOneFontFamily = FontFamily(
    Font(R.font.specialgothic_regular, weight = FontWeight.Normal),
)

val ZalandoSansSemiExpandedFontFamily = FontFamily(
    Font(R.font.zalandosans_regular, weight = FontWeight.Normal),
    Font(R.font.zalandosans_medium, weight = FontWeight.Medium),
    Font(R.font.zalandosans_bold, weight = FontWeight.Bold),
)

data class AppFontOption(val id: String, val displayName: String, val family: FontFamily)

object AppFonts {
    val options = listOf(
        AppFontOption("onest", "Onest", OnestFontFamily),
        AppFontOption("googlesans", "Google Sans", GoogleSansFontFamily),
        AppFontOption("specialgothic", "Special Gothic Expanded One", SpecialGothicExpandedOneFontFamily),
        AppFontOption("zalandosans", "Zalando Sans SemiExpanded", ZalandoSansSemiExpandedFontFamily),
        AppFontOption("lato", "Lato", LatoFontFamily),
    )
    val default = options.first()

    fun byId(id: String?): AppFontOption = options.find { it.id == id } ?: default
}

val LocalAppFontFamily = compositionLocalOf { AppFonts.default.family }

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

private fun typographyFor(fontFamily: FontFamily) = with(Typography()) {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily),
    )
}

@Composable
fun ZStreamTheme(content: @Composable () -> Unit) {
    val themeVm: ThemeViewModel = viewModel()
    val currentTheme = themeVm.currentTheme.value
    val currentFont = AppFonts.byId(themeVm.currentFont.value).family

    CompositionLocalProvider(
        LocalZStreamTheme provides currentTheme,
        LocalAppFontFamily provides currentFont,
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(currentTheme),
            typography = typographyFor(currentFont),
            content = content,
        )
    }
}
