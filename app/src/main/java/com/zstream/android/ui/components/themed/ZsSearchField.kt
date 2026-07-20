package com.zstream.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zstream.android.R
import com.zstream.android.theme.LocalZStreamTheme

@Composable
fun ZsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
) {
    val theme = LocalZStreamTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(theme.colors.search.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = theme.colors.search.icon,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(18.dp),
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .fillMaxWidth()
                .heightIn(min = 24.dp)
                .padding(start = 28.dp, end = if (value.isNotEmpty()) 28.dp else 0.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = theme.colors.search.text,
                fontSize = 15.sp,
            ),
            cursorBrush = SolidColor(theme.colors.type.emphasis),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                if (value.isEmpty() && placeholder.isNotBlank()) {
                    Text(
                        text = placeholder,
                        color = theme.colors.search.placeholder,
                        fontSize = 15.sp,
                    )
                }
                innerTextField()
            },
        )

        if (value.isNotEmpty()) {
            ZsIconButton(
                onClick = { onValueChange("") },
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.system_clear_search),
                variant = ZsIconButtonVariant.Ghost,
                containerSize = 22.dp,
                iconSize = 14.dp,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
