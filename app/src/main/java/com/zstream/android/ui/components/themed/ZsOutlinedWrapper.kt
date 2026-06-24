package com.zstream.android.ui.components.themed

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zstream.android.theme.LocalZStreamTheme

@Composable
fun ZsOutlinedWrapper(
    modifier: Modifier = Modifier,
    outlineColor: Color = LocalZStreamTheme.current.colors.global.accentA.copy(alpha = 0.6f),
    outlineWidth: Dp = 1.5.dp,
    gap: Dp = 4.dp,
    shape: Shape,
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    ZsOutlinedWrapper(
        modifier = modifier,
        outlineColor = outlineColor,
        outlineWidth = outlineWidth,
        start = gap,
        top = gap,
        end = gap,
        bottom = gap,
        shape = shape,
        visible = visible,
        content = content
    )
}

@Composable
fun ZsOutlinedWrapper(
    modifier: Modifier = Modifier,
    outlineColor: Color = LocalZStreamTheme.current.colors.global.accentA.copy(alpha = 0.6f),
    outlineWidth: Dp = 1.5.dp,
    horizontal: Dp,
    vertical: Dp,
    shape: Shape,
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    ZsOutlinedWrapper(
        modifier = modifier,
        outlineColor = outlineColor,
        outlineWidth = outlineWidth,
        start = horizontal,
        top = vertical,
        end = horizontal,
        bottom = vertical,
        shape = shape,
        visible = visible,
        content = content
    )
}

@Composable
fun ZsOutlinedWrapper(
    modifier: Modifier = Modifier,
    outlineColor: Color = LocalZStreamTheme.current.colors.global.accentA.copy(alpha = 0.6f),
    outlineWidth: Dp = 1.5.dp,
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
    shape: Shape,
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    Box(
        modifier = modifier.then(
            if (visible) {
                Modifier.drawWithContent {
                    drawContent()
                    val startPx = start.toPx()
                    val topPx = top.toPx()
                    val endPx = end.toPx()
                    val bottomPx = bottom.toPx()
                    
                    val leftPx = if (layoutDirection == LayoutDirection.Ltr) startPx else endPx
                    val rightPx = if (layoutDirection == LayoutDirection.Ltr) endPx else startPx

                    val strokeWidthPx = outlineWidth.toPx()
                    val inflatedSize = Size(size.width + leftPx + rightPx, size.height + topPx + bottomPx)
                    val outline = shape.createOutline(inflatedSize, layoutDirection, density)
                    
                    drawContext.canvas.save()
                    drawContext.canvas.translate(-leftPx, -topPx)
                    drawOutline(
                        outline = outline,
                        color = outlineColor,
                        style = Stroke(width = strokeWidthPx)
                    )
                    drawContext.canvas.restore()
                }
            } else {
                Modifier
            }
        )
    ) {
        content()
    }
}
