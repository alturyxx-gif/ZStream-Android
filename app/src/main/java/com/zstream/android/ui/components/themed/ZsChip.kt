package com.zstream.android.ui.components.themed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zstream.android.theme.LocalZStreamTheme

enum class ZsChipVariant {
    Subtle,
    Selectable,
}

@Composable
fun ZsChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    variant: ZsChipVariant = ZsChipVariant.Selectable,
    leadingIcon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    selectedContainerColor: Color? = null,
    selectedContentColor: Color? = null,
    selectedBorderColor: Color? = null,
) {
    val theme = LocalZStreamTheme.current
    val (containerColor, contentColor, borderColor) = when (variant) {
        ZsChipVariant.Subtle -> Triple(
            theme.colors.type.text.copy(alpha = 0.08f),
            theme.colors.type.text,
            theme.colors.type.divider.copy(alpha = 0.15f),
        )
        ZsChipVariant.Selectable -> Triple(
            if (selected) {
                selectedContainerColor ?: theme.colors.background.secondaryHover.copy(alpha = 0.8f)
            } else {
                theme.colors.background.secondary.copy(alpha = 0.5f)
            },
            if (selected) {
                selectedContentColor ?: theme.colors.type.emphasis
            } else {
                theme.colors.type.secondary
            },
            if (selected) {
                selectedBorderColor ?: theme.colors.global.accentA.copy(alpha = 0.45f)
            } else {
                theme.colors.type.divider.copy(alpha = 0.22f)
            },
        )
    }

    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        color = containerColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                text = label,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}
