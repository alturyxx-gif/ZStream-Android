package com.zstream.android.theme.themes

import androidx.compose.ui.graphics.Color
import com.zstream.android.theme.About
import com.zstream.android.theme.Authentication
import com.zstream.android.theme.Background
import com.zstream.android.theme.Buttons
import com.zstream.android.theme.DefaultTheme
import com.zstream.android.theme.Dropdown
import com.zstream.android.theme.EditBadge
import com.zstream.android.theme.Errors
import com.zstream.android.theme.ErrorsType
import com.zstream.android.theme.Global
import com.zstream.android.theme.LargeCard
import com.zstream.android.theme.LightBar
import com.zstream.android.theme.MediaCard
import com.zstream.android.theme.Modal
import com.zstream.android.theme.Progress
import com.zstream.android.theme.Settings
import com.zstream.android.theme.SettingsCard
import com.zstream.android.theme.SettingsSaveBar
import com.zstream.android.theme.SettingsSidebar
import com.zstream.android.theme.SettingsSidebarType
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
import com.zstream.android.theme.ZStreamTheme
import com.zstream.android.theme.Pill
import com.zstream.android.theme.Search

object BlueTheme {
    fun create(): ZStreamTheme {
        val base = DefaultTheme.create()
        val purple50 = Color(0xFFAAC5FF)
        val purple100 = Color(0xFF82A9FF)
        val purple200 = Color(0xFF4681FF)
        val purple300 = Color(0xFF1A63FF)
        val purple400 = Color(0xFF054EEC)
        val purple500 = Color(0xFF083AA7)

        val shade25 = Color(0xFF5D7DB3)
        val shade50 = Color(0xFF756790)
        val shade100 = Color(0xFF60527A)
        val shade200 = Color(0xFF4A3F60)
        val shade300 = Color(0xFF3C324F)
        val shade400 = Color(0xFF302741)
        val shade500 = Color(0xFF251E32)
        val shade600 = Color(0xFF1D1728)
        val shade700 = Color(0xFF181322)
        val shade800 = Color(0xFF130F1B)
        val shade900 = Color(0xFF0D0A12)

        val ash50 = Color(0xFF7F859B)
        val ash100 = Color(0xFF5B627B)
        val ash200 = Color(0xFF444B64)
        val ash300 = Color(0xFF2B344E)
        val ash400 = Color(0xFF202842)
        val ash500 = Color(0xFF1C243C)
        val ash600 = Color(0xFF171D32)
        val ash700 = Color(0xFF131829)
        val ash800 = Color(0xFF101420)
        val ash900 = Color(0xFF0C0F16)

        val blue200 = Color(0xFF5D65AE)
        val blue300 = Color(0xFF3B438C)
        val blue400 = Color(0xFF2A3171)
        val blue500 = Color(0xFF1F2450)

        return base.copy(
            id = "blue",
            name = "Blue",
            colors = base.colors.copy(
                themePreview = ThemePreview(
                    primary = blue200,
                    secondary = shade50,
                    ghost = base.colors.themePreview.ghost,
                ),
                pill = Pill(
                    background = shade300,
                    backgroundHover = shade200,
                    highlight = blue200,
                    activeBackground = shade300,
                ),
                global = Global(
                    accentA = blue200,
                    accentB = blue300,
                ),
                lightBar = LightBar(light = blue400),
                buttons = Buttons(
                    toggle = purple300,
                    toggleDisabled = ash500,
                    danger = base.colors.buttons.danger,
                    dangerHover = base.colors.buttons.dangerHover,
                    secondary = ash700,
                    secondaryText = base.colors.buttons.secondaryText,
                    secondaryHover = ash700,
                    primary = base.colors.buttons.primary,
                    primaryText = base.colors.buttons.primaryText,
                    primaryHover = base.colors.buttons.primaryHover,
                    purple = purple500,
                    purpleHover = purple400,
                    cancel = ash500,
                    cancelHover = ash300,
                ),
                background = Background(
                    main = shade900,
                    secondary = shade600,
                    secondaryHover = shade400,
                    accentA = purple500,
                    accentB = blue500,
                ),
                modal = Modal(background = shade800),
                type = Type(
                    logo = purple100,
                    emphasis = base.colors.type.emphasis,
                    text = shade50,
                    dimmed = shade50,
                    divider = ash500,
                    secondary = ash100,
                    danger = base.colors.type.danger,
                    success = base.colors.type.success,
                    link = purple100,
                    linkHover = purple50,
                ),
                search = Search(
                    background = shade500,
                    hoverBackground = shade600,
                    focused = shade400,
                    placeholder = shade100,
                    icon = shade100,
                    text = base.colors.search.text,
                ),
                mediaCard = MediaCard(
                    hoverBackground = shade600,
                    hoverAccent = shade25,
                    hoverShadow = shade900,
                    shadow = shade700,
                    barColor = ash200,
                    barFillColor = purple100,
                    badge = shade700,
                    badgeText = ash100,
                ),
                largeCard = LargeCard(
                    background = shade600,
                    icon = purple400,
                ),
                dropdown = Dropdown(
                    background = shade600,
                    altBackground = shade700,
                    hoverBackground = shade500,
                    highlight = base.colors.dropdown.highlight,
                    highlightHover = base.colors.dropdown.highlightHover,
                    text = shade50,
                    secondary = shade100,
                    border = shade400,
                    contentBackground = shade500,
                ),
                authentication = Authentication(
                    border = shade300,
                    inputBg = shade600,
                    inputBgHover = shade500,
                    wordBackground = shade500,
                    copyText = shade100,
                    copyTextHover = ash50,
                    errorText = base.colors.authentication.errorText,
                ),
                settings = Settings(
                    sidebar = SettingsSidebar(
                        activeLink = shade600,
                        badge = shade900,
                        type = SettingsSidebarType(
                            secondary = shade200,
                            inactive = shade50,
                            icon = shade50,
                            iconActivated = purple200,
                            activated = purple50,
                        ),
                    ),
                    card = SettingsCard(
                        border = shade400,
                        background = shade400,
                        altBackground = shade400,
                    ),
                    saveBar = SettingsSaveBar(background = shade800),
                ),
                utils = Utils(divider = ash300),
                errors = Errors(
                    card = shade800,
                    border = ash500,
                    type = ErrorsType(secondary = ash100),
                ),
                about = About(
                    circle = ash500,
                    circleText = ash50,
                ),
                editBadge = EditBadge(
                    bg = ash500,
                    bgHover = ash400,
                    text = ash50,
                ),
                progress = Progress(
                    background = ash50,
                    preloaded = ash50,
                    filled = purple200,
                ),
                video = Video(
                    buttonBackground = ash200,
                    autoPlay = VideoAutoPlay(
                        background = ash700,
                        hover = ash500,
                    ),
                    scraping = VideoScraping(
                        card = shade700,
                        error = base.colors.video.scraping.error,
                        success = base.colors.video.scraping.success,
                        loading = purple200,
                        noresult = ash100,
                    ),
                    audio = VideoAudio(set = purple200),
                    context = VideoContext(
                        background = ash900,
                        light = shade50,
                        border = ash600,
                        hoverColor = ash600,
                        buttonFocus = ash500,
                        flagBg = ash500,
                        inputBg = ash600,
                        buttonOverInputHover = ash500,
                        inputPlaceholder = ash200,
                        cardBorder = ash700,
                        slider = ash50,
                        sliderFilled = purple200,
                        error = base.colors.video.context.error,
                        buttons = VideoContextButtons(
                            list = ash700,
                            active = ash900,
                        ),
                        closeHover = ash800,
                        type = VideoContextType(
                            main = base.colors.video.context.type.main,
                            secondary = ash200,
                            accent = purple200,
                        ),
                    ),
                ),
            ),
        )
    }
}
