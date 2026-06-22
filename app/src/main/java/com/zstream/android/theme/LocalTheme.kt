package com.zstream.android.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.zstream.android.data.model.Media
import com.zstream.android.ui.screens.MediaCardStandard

val LocalZStreamTheme = staticCompositionLocalOf<ZStreamTheme> {
    error("ZStreamTheme not provided")
}

typealias MediaCardComponent = @Composable (
    media: Media,
    onClick: () -> Unit,
    percentage: Float?,
    seriesLabel: String?
) -> Unit

val LocalMediaCard = compositionLocalOf<MediaCardComponent> {
    { m, o, p, s -> MediaCardStandard(m, o, p, s) }
}