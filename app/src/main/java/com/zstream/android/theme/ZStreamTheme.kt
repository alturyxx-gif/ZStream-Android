package com.zstream.android.theme

import androidx.compose.ui.graphics.Color

data class ZStreamTheme(
    val id: String,
    val name: String,
    val colors: ThemeColors,
) {
    companion object {
        val themes = mutableMapOf<String, ZStreamTheme>()
    }
}

data class ThemeColors(
    // Branding
    val themePreview: ThemePreview,
    val pill: Pill,
    val global: Global,
    val lightBar: LightBar,

    // Buttons
    val buttons: Buttons,

    // Backgrounds
    val background: Background,

    // Modals
    val modal: Modal,

    // Typography
    val type: Type,

    // Search bar
    val search: Search,

    // Media cards
    val mediaCard: MediaCard,

    // Large card
    val largeCard: LargeCard,

    // Dropdown
    val dropdown: Dropdown,

    // Authentication
    val authentication: Authentication,

    // Settings
    val settings: Settings,

    // Utilities
    val utils: Utils,

    // Onboarding
    val onboarding: Onboarding,

    // Error page
    val errors: Errors,

    // About page
    val about: About,

    // Edit badge
    val editBadge: EditBadge,

    // Progress
    val progress: Progress,

    // Video player
    val video: Video,
)

data class ThemePreview(val primary: Color, val secondary: Color, val ghost: Color)
data class Pill(val background: Color, val backgroundHover: Color, val highlight: Color, val activeBackground: Color)
data class Global(val accentA: Color, val accentB: Color)
data class LightBar(val light: Color)
data class Buttons(
    val toggle: Color,
    val toggleDisabled: Color,
    val danger: Color,
    val dangerHover: Color,
    val secondary: Color,
    val secondaryText: Color,
    val secondaryHover: Color,
    val primary: Color,
    val primaryText: Color,
    val primaryHover: Color,
    val purple: Color,
    val purpleHover: Color,
    val cancel: Color,
    val cancelHover: Color,
)
data class Background(
    val main: Color,
    val secondary: Color,
    val secondaryHover: Color,
    val accentA: Color,
    val accentB: Color,
)
data class Modal(val background: Color)
data class Type(
    val logo: Color,
    val emphasis: Color,
    val text: Color,
    val dimmed: Color,
    val divider: Color,
    val secondary: Color,
    val danger: Color,
    val success: Color,
    val link: Color,
    val linkHover: Color,
)
data class Search(
    val background: Color,
    val hoverBackground: Color,
    val focused: Color,
    val placeholder: Color,
    val icon: Color,
    val text: Color,
)
data class MediaCard(
    val hoverBackground: Color,
    val hoverAccent: Color,
    val hoverShadow: Color,
    val shadow: Color,
    val barColor: Color,
    val barFillColor: Color,
    val badge: Color,
    val badgeText: Color,
)
data class LargeCard(val background: Color, val icon: Color)
data class Dropdown(
    val background: Color,
    val altBackground: Color,
    val hoverBackground: Color,
    val highlight: Color,
    val highlightHover: Color,
    val text: Color,
    val secondary: Color,
    val border: Color,
    val contentBackground: Color,
)
data class Authentication(
    val border: Color,
    val inputBg: Color,
    val inputBgHover: Color,
    val wordBackground: Color,
    val copyText: Color,
    val copyTextHover: Color,
    val errorText: Color,
)
data class Settings(
    val sidebar: SettingsSidebar,
    val card: SettingsCard,
    val saveBar: SettingsSaveBar,
)
data class SettingsSidebar(
    val activeLink: Color,
    val badge: Color,
    val type: SettingsSidebarType,
)
data class SettingsSidebarType(
    val secondary: Color,
    val inactive: Color,
    val icon: Color,
    val iconActivated: Color,
    val activated: Color,
)
data class SettingsCard(
    val border: Color,
    val background: Color,
    val altBackground: Color,
)
data class SettingsSaveBar(val background: Color)
data class Utils(val divider: Color)
data class Onboarding(
    val bar: Color,
    val barFilled: Color,
    val divider: Color,
    val card: Color,
    val cardHover: Color,
    val border: Color,
    val good: Color,
    val best: Color,
    val link: Color,
)
data class Errors(
    val card: Color,
    val border: Color,
    val type: ErrorsType,
)
data class ErrorsType(val secondary: Color)
data class About(val circle: Color, val circleText: Color)
data class EditBadge(val bg: Color, val bgHover: Color, val text: Color)
data class Progress(val background: Color, val preloaded: Color, val filled: Color)
data class Video(
    val buttonBackground: Color,
    val autoPlay: VideoAutoPlay,
    val scraping: VideoScraping,
    val audio: VideoAudio,
    val context: VideoContext,
)
data class VideoAutoPlay(val background: Color, val hover: Color)
data class VideoScraping(
    val card: Color,
    val error: Color,
    val success: Color,
    val loading: Color,
    val noresult: Color,
)
data class VideoAudio(val set: Color)
data class VideoContext(
    val background: Color,
    val light: Color,
    val border: Color,
    val hoverColor: Color,
    val buttonFocus: Color,
    val flagBg: Color,
    val inputBg: Color,
    val buttonOverInputHover: Color,
    val inputPlaceholder: Color,
    val cardBorder: Color,
    val slider: Color,
    val sliderFilled: Color,
    val error: Color,
    val buttons: VideoContextButtons,
    val closeHover: Color,
    val type: VideoContextType,
)
data class VideoContextButtons(val list: Color, val active: Color)
data class VideoContextType(val main: Color, val secondary: Color, val accent: Color)
