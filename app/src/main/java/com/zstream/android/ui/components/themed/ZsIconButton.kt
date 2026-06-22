package com.zstream.android.ui.components.themed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zstream.android.theme.LocalZStreamTheme

enum class ZsIconButtonVariant {
    Ghost,
    Secondary,
    Overlay,
    Danger,
}

@Composable
fun ZsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    contentDescription: String? = null,
    variant: ZsIconButtonVariant = ZsIconButtonVariant.Secondary,
    enabled: Boolean = true,
    selected: Boolean = false,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = CircleShape,
) {
    require(icon != null || painter != null) { "ZsIconButton requires either an icon or painter." }

    val theme = LocalZStreamTheme.current
    val (containerColor, contentColor, borderColor) = when (variant) {
        ZsIconButtonVariant.Ghost -> Triple(Color.Transparent, theme.colors.type.emphasis, Color.Transparent)
        ZsIconButtonVariant.Secondary -> Triple(
            if (selected) theme.colors.background.secondaryHover.copy(alpha = 0.92f) else theme.colors.background.secondary.copy(alpha = 0.8f),
            if (selected) theme.colors.type.emphasis else theme.colors.type.secondary,
            theme.colors.type.divider.copy(alpha = if (selected) 0.4f else 0.3f),
        )
        ZsIconButtonVariant.Overlay -> Triple(
            theme.colors.video.context.background.copy(alpha = 0.75f),
            theme.colors.type.emphasis,
            theme.colors.type.emphasis.copy(alpha = 0.15f),
        )
        ZsIconButtonVariant.Danger -> Triple(
            theme.colors.buttons.danger.copy(alpha = 0.2f),
            theme.colors.type.danger,
            theme.colors.type.danger.copy(alpha = 0.3f),
        )
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(containerSize),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.65f),
        shape = shape,
        border = if (borderColor.alpha > 0f) BorderStroke(1.dp, borderColor) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize),
                )
            } else if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
