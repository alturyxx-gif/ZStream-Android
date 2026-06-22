package com.zstream.android.ui.components.themed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.zstream.android.theme.LocalZStreamTheme

enum class ZsCardVariant {
    Default,
    Elevated,
    Modal,
}

@Composable
fun ZsCard(
    modifier: Modifier = Modifier,
    variant: ZsCardVariant = ZsCardVariant.Default,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalZStreamTheme.current
    val background = when (variant) {
        ZsCardVariant.Default -> theme.colors.settings.card.background
        ZsCardVariant.Elevated -> theme.colors.background.secondary
        ZsCardVariant.Modal -> theme.colors.modal.background
    }

    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.18f)),
        content = { androidx.compose.foundation.layout.Column(content = content) },
    )
}
