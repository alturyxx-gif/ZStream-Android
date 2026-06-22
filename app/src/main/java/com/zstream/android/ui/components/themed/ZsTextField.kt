package com.zstream.android.ui.components.themed

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import com.zstream.android.theme.LocalZStreamTheme

@Composable
fun ZsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val theme = LocalZStreamTheme.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = theme.colors.type.text,
            unfocusedTextColor = theme.colors.type.text,
            disabledTextColor = theme.colors.type.dimmed,
            errorTextColor = theme.colors.type.danger,
            focusedBorderColor = theme.colors.buttons.purple,
            unfocusedBorderColor = theme.colors.background.secondary,
            disabledBorderColor = theme.colors.type.divider.copy(alpha = 0.25f),
            errorBorderColor = theme.colors.type.danger,
            focusedLabelColor = theme.colors.type.emphasis.copy(alpha = 0.85f),
            unfocusedLabelColor = theme.colors.type.secondary,
            disabledLabelColor = theme.colors.type.dimmed,
            errorLabelColor = theme.colors.type.danger,
            focusedPlaceholderColor = theme.colors.type.dimmed.copy(alpha = 0.6f),
            unfocusedPlaceholderColor = theme.colors.type.dimmed.copy(alpha = 0.6f),
            disabledPlaceholderColor = theme.colors.type.dimmed.copy(alpha = 0.45f),
            cursorColor = theme.colors.type.emphasis,
            errorCursorColor = theme.colors.type.danger,
        ),
    )
}
