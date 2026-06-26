package com.zstream.android.ui.components.themed

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ZsTvOutlinedWrapper(
    modifier: Modifier = Modifier,
    outlineColor: Color = Color.White,
    outlineWidth: Dp = 3.dp,
    gap: Dp = 4.dp,
    shape: Shape = RoundedCornerShape(50),
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    val animatedWidth by animateDpAsState(if (visible) outlineWidth else 0.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .border(animatedWidth, outlineColor, shape)
            .padding(gap),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
