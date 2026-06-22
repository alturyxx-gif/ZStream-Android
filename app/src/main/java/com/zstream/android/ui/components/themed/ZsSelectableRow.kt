package com.zstream.android.ui.components.themed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme

@Composable
fun ZsSelectableRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val theme = LocalZStreamTheme.current
    val borderColor = if (selected) {
        theme.colors.global.accentA.copy(alpha = 0.55f)
    } else {
        theme.colors.type.divider.copy(alpha = 0.18f)
    }
    val background = if (selected) {
        theme.colors.background.secondaryHover.copy(alpha = 0.75f)
    } else {
        theme.colors.settings.card.background
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) {
                leading()
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (selected) theme.colors.type.emphasis else theme.colors.type.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = theme.colors.type.secondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            SelectionIndicator(selected = selected, color = theme.colors.global.accentA)
        }
    }
}

@Composable
fun ZsThemePreviewCard(
    themeOption: ZStreamTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ZsSelectableRow(
        title = themeOption.name,
        subtitle = themeOption.id,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leading = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeSwatch(themeOption.colors.themePreview.primary)
                ThemeSwatch(themeOption.colors.themePreview.secondary)
                ThemeSwatch(themeOption.colors.global.accentA)
            }
        },
    )
}

@Composable
private fun ThemeSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SelectionIndicator(selected: Boolean, color: Color) {
    Surface(
        modifier = Modifier.size(22.dp),
        color = if (selected) color else Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (selected) color else color.copy(alpha = 0.35f)),
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
