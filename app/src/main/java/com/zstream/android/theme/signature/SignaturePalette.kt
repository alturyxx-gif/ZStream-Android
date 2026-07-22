package com.zstream.android.theme.signature

import androidx.compose.ui.graphics.Color
import com.zstream.android.theme.About
import com.zstream.android.theme.Authentication
import com.zstream.android.theme.Background
import com.zstream.android.theme.Buttons
import com.zstream.android.theme.Dropdown
import com.zstream.android.theme.EditBadge
import com.zstream.android.theme.Errors
import com.zstream.android.theme.ErrorsType
import com.zstream.android.theme.Global
import com.zstream.android.theme.LargeCard
import com.zstream.android.theme.LightBar
import com.zstream.android.theme.MediaCard
import com.zstream.android.theme.Modal
import com.zstream.android.theme.Onboarding
import com.zstream.android.theme.Pill
import com.zstream.android.theme.Progress
import com.zstream.android.theme.Search
import com.zstream.android.theme.Settings
import com.zstream.android.theme.SettingsCard
import com.zstream.android.theme.SettingsSaveBar
import com.zstream.android.theme.SettingsSidebar
import com.zstream.android.theme.SettingsSidebarType
import com.zstream.android.theme.ThemeColors
import com.zstream.android.theme.ThemePreview
import com.zstream.android.theme.Type
import com.zstream.android.theme.Utils
import com.zstream.android.theme.Video
import com.zstream.android.theme.VideoAudio
import com.zstream.android.theme.VideoAutoPlay
import com.zstream.android.theme.VideoContext
import com.zstream.android.theme.VideoContextButtons
import com.zstream.android.theme.VideoContextType
import com.zstream.android.theme.VideoScraping

data class SignatureBgPalette(
    val deepest: Color,
    val deep: Color,
    val base: Color,
    val raised: Color,
    val elevated: Color,
    val line: Color,
)

data class SignatureAccentPalette(val core: Color, val core2: Color, val soft: Color)

data class SignatureTextPalette(val primary: Color, val secondary: Color, val faint: Color)

data class SignaturePalette(
    val white: Color,
    val bg: SignatureBgPalette,
    val accent: SignatureAccentPalette,
    val text: SignatureTextPalette,
)

private object SignatureSemantic {
    val redC100 = Color(0xFFF46E6E)
    val redC200 = Color(0xFFE44F4F)
    val greenC100 = Color(0xFF60D26A)
    val greenC200 = Color(0xFF40B44B)
    val silverC300 = Color(0xFF8EA3B0)
    val yellowC100 = Color(0xFFFFF599)
    val yellowC200 = Color(0xFFFCEC61)
    val yellowC400 = Color(0xFFAFA349)
    val roseC100 = Color(0xFFDB3D61)
    val roseC200 = Color(0xFF8A293B)
    val roseC300 = Color(0xFF812435)
}

fun buildSignatureColors(p: SignaturePalette): ThemeColors {
    val s = SignatureSemantic
    return ThemeColors(
        themePreview = ThemePreview(
            primary = p.accent.core,
            secondary = p.text.secondary,
            ghost = p.white,
        ),
        pill = Pill(
            background = p.bg.base,
            backgroundHover = p.bg.raised,
            highlight = p.accent.core,
            activeBackground = p.bg.elevated,
        ),
        global = Global(accentA = p.accent.core, accentB = p.accent.core2),
        lightBar = LightBar(light = p.bg.elevated),
        buttons = Buttons(
            toggle = p.accent.core,
            toggleDisabled = p.bg.raised,
            danger = s.roseC300,
            dangerHover = s.roseC200,
            secondary = p.bg.base,
            secondaryText = p.text.secondary,
            secondaryHover = p.bg.raised,
            primary = p.white,
            primaryText = p.bg.deepest,
            primaryHover = Color(0xFFDEDEDE),
            purple = p.accent.core,
            purpleHover = p.accent.soft,
            cancel = p.bg.base,
            cancelHover = p.bg.raised,
        ),
        background = Background(
            main = p.bg.deepest,
            secondary = p.bg.deep,
            secondaryHover = p.bg.raised,
            accentA = p.accent.core,
            accentB = p.bg.base,
        ),
        modal = Modal(background = p.bg.elevated),
        type = Type(
            logo = p.accent.core,
            emphasis = p.white,
            text = p.text.primary,
            dimmed = p.text.secondary,
            divider = p.bg.line,
            secondary = p.text.secondary,
            danger = s.redC100,
            success = s.greenC100,
            link = p.accent.core,
            linkHover = p.accent.soft,
        ),
        search = Search(
            background = p.bg.base,
            hoverBackground = p.bg.deep,
            focused = p.bg.raised,
            placeholder = p.text.faint,
            icon = p.text.faint,
            text = p.white,
        ),
        mediaCard = MediaCard(
            hoverBackground = p.bg.raised,
            hoverAccent = p.accent.soft,
            hoverShadow = p.bg.deepest,
            shadow = p.bg.deep,
            barColor = p.bg.line,
            barFillColor = p.accent.core,
            badge = p.bg.elevated,
            badgeText = p.text.secondary,
        ),
        largeCard = LargeCard(background = p.bg.base, icon = p.accent.core),
        dropdown = Dropdown(
            background = p.bg.base,
            altBackground = p.bg.deep,
            hoverBackground = p.bg.raised,
            highlight = s.yellowC400,
            highlightHover = s.yellowC200,
            text = p.text.secondary,
            secondary = p.text.faint,
            border = p.bg.line,
            contentBackground = p.bg.base,
        ),
        authentication = Authentication(
            border = p.bg.line,
            inputBg = p.bg.base,
            inputBgHover = p.bg.raised,
            wordBackground = p.bg.elevated,
            copyText = p.text.faint,
            copyTextHover = p.text.primary,
            errorText = s.roseC100,
        ),
        settings = Settings(
            sidebar = SettingsSidebar(
                activeLink = p.bg.base,
                badge = p.bg.deepest,
                type = SettingsSidebarType(
                    secondary = p.text.faint,
                    inactive = p.text.secondary,
                    icon = p.bg.raised,
                    iconActivated = p.accent.core,
                    activated = p.accent.soft,
                ),
            ),
            card = SettingsCard(
                border = p.bg.line,
                background = p.bg.base,
                altBackground = p.bg.base,
            ),
            saveBar = SettingsSaveBar(background = p.bg.deepest),
        ),
        utils = Utils(divider = p.bg.line),
        onboarding = Onboarding(
            bar = p.bg.raised,
            barFilled = p.accent.core,
            divider = p.bg.line,
            card = p.bg.elevated,
            cardHover = p.bg.deep,
            border = p.bg.raised,
            good = p.accent.core,
            best = s.yellowC100,
            link = p.accent.core,
        ),
        errors = Errors(
            card = p.bg.deepest,
            border = p.bg.line,
            type = ErrorsType(secondary = p.text.faint),
        ),
        about = About(circle = p.bg.base, circleText = p.text.faint),
        editBadge = EditBadge(bg = p.bg.raised, bgHover = p.bg.elevated, text = p.text.faint),
        progress = Progress(
            background = p.bg.line,
            preloaded = p.text.faint,
            filled = p.accent.core,
        ),
        video = Video(
            buttonBackground = p.bg.raised,
            autoPlay = VideoAutoPlay(background = p.bg.elevated, hover = p.bg.raised),
            scraping = VideoScraping(
                card = p.bg.deepest,
                error = s.redC200,
                success = s.greenC200,
                loading = p.accent.core,
                noresult = p.bg.raised,
            ),
            audio = VideoAudio(set = p.accent.core),
            context = VideoContext(
                background = p.bg.deepest,
                light = p.text.faint,
                border = p.bg.raised,
                hoverColor = p.bg.raised,
                buttonFocus = p.bg.elevated,
                flagBg = p.bg.elevated,
                inputBg = p.bg.base,
                buttonOverInputHover = p.bg.elevated,
                inputPlaceholder = p.text.faint,
                cardBorder = p.bg.line,
                slider = p.bg.raised,
                sliderFilled = p.accent.core,
                error = s.redC200,
                buttons = VideoContextButtons(list = p.bg.raised, active = p.bg.deepest),
                closeHover = p.bg.elevated,
                type = VideoContextType(
                    main = s.silverC300,
                    secondary = p.text.faint,
                    accent = p.accent.core,
                ),
            ),
        ),
    )
}
