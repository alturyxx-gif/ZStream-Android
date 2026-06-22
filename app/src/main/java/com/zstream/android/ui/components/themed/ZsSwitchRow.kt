package com.zstream.android.ui.components.themed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zstream.android.theme.LocalZStreamTheme

@Composable
fun ZsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    notice: String? = null,
) {
    val theme = LocalZStreamTheme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .then(if (enabled) Modifier else Modifier.alpha(0.5f))
            .padding(top = 12.dp, bottom = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = theme.colors.type.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        color = theme.colors.type.dimmed,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                if (!notice.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        notice,
                        color = theme.colors.type.danger,
                        fontSize = 10.sp,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = theme.colors.type.emphasis,
                    checkedTrackColor = theme.colors.global.accentA,
                    uncheckedThumbColor = theme.colors.type.dimmed,
                    uncheckedTrackColor = theme.colors.background.secondary,
                ),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
