package com.zstream.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zstream.android.R
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ThemeViewModel
import com.zstream.android.ui.screens.SettingsViewModel
import com.zstream.android.ui.screens.MediaCardMinimal
import com.zstream.android.ui.screens.MediaCardStandard
import com.zstream.android.theme.LocalMediaCard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.map

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

/**
 * Settings can be passed through here to avoid Prop Drilling
 * see enableminimalcards code as example
 */

@Composable
fun ZStreamTheme(content: @Composable () -> Unit) {
    val themeVm: ThemeViewModel = viewModel()
    val currentTheme = themeVm.currentTheme.value
    val settingsVm: SettingsViewModel = hiltViewModel()
        // apparently if u call ZStreamTheme in a @Preview this crashes it but idk how to fix it cleanly and easily
        // dont think its worth it to figure out, it seems like u dont use it anyway
    val useMinimalCards by remember(settingsVm) {
        settingsVm.settings.map { it.enableMinimalCards } //takes value from settings
    }.collectAsState(initial = false)
    val cardImplementation = remember(useMinimalCards) {
        if (useMinimalCards) ::MediaCardMinimal else ::MediaCardStandard // picks which card to use
    }
    
    CompositionLocalProvider(
        LocalZStreamTheme provides currentTheme,
        LocalMediaCard provides cardImplementation
    ) {
        MaterialTheme(
            colorScheme = ColorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
