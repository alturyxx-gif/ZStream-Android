package com.zstream.android.ui.components.themed

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.LocalIsTv

enum class ZsButtonVariant {
    Primary,
    Secondary,
    Purple,
    Danger,
}

@Composable
fun ZsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ZsButtonVariant = ZsButtonVariant.Primary,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    leadingIcon: ImageVector? = null,
    loading: Boolean = false,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }
    val focusBorderWidth by animateDpAsState(if (isFocused && isTv) 3.dp else 0.dp)

    val (containerColor, contentColor, borderColor) = when (variant) {
        ZsButtonVariant.Primary -> Triple(
            theme.colors.buttons.primary,
            theme.colors.buttons.primaryText,
            theme.colors.type.divider.copy(alpha = 0.3f),
        )
        ZsButtonVariant.Secondary -> Triple(
            theme.colors.buttons.secondary,
            theme.colors.buttons.secondaryText,
            theme.colors.type.divider.copy(alpha = 0.25f),
        )
        ZsButtonVariant.Purple -> Triple(
            theme.colors.buttons.purple,
            theme.colors.type.emphasis,
            theme.colors.buttons.purple.copy(alpha = 0.4f),
        )
        ZsButtonVariant.Danger -> Triple(
            theme.colors.buttons.danger,
            theme.colors.type.emphasis,
            theme.colors.buttons.danger.copy(alpha = 0.35f),
        )
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isTv) Modifier.border(focusBorderWidth, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                else Modifier
            ),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        contentPadding = contentPadding,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.7f),
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = contentColor,
                        strokeWidth = 2.dp,
                    )
                }
                leadingIcon != null -> {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ZsTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalZStreamTheme.current
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = theme.colors.buttons.secondaryText,
            disabledContentColor = theme.colors.buttons.secondaryText.copy(alpha = 0.6f),
        ),
    ) {
        Text(text = text)
    }
}
