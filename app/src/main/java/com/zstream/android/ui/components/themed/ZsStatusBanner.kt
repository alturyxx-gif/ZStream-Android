package com.zstream.android.ui.components.themed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zstream.android.theme.LocalZStreamTheme

enum class ZsStatusBannerVariant {
    Info,
    Success,
    Error,
}

@Composable
fun ZsStatusBanner(
    message: String,
    modifier: Modifier = Modifier,
    variant: ZsStatusBannerVariant = ZsStatusBannerVariant.Info,
) {
    val theme = LocalZStreamTheme.current
    val (containerColor, contentColor, borderColor) = when (variant) {
        ZsStatusBannerVariant.Info -> Triple(
            theme.colors.background.secondary.copy(alpha = 0.55f),
            theme.colors.type.secondary,
            theme.colors.type.divider.copy(alpha = 0.22f),
        )
        ZsStatusBannerVariant.Success -> Triple(
            theme.colors.type.success.copy(alpha = 0.14f),
            theme.colors.type.success,
            theme.colors.type.success.copy(alpha = 0.28f),
        )
        ZsStatusBannerVariant.Error -> Triple(
            theme.colors.type.danger.copy(alpha = 0.14f),
            theme.colors.type.danger,
            theme.colors.type.danger.copy(alpha = 0.28f),
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                color = contentColor,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}
