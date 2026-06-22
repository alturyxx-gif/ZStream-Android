package com.zstream.android.ui.components.themed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zstream.android.theme.LocalZStreamTheme

@Composable
fun ZsBottomSheetSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalZStreamTheme.current
    Text(
        title,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 18.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = theme.colors.type.emphasis,
    )
}

@Composable
fun ZsBottomSheetSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalZStreamTheme.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.colors.modal.background,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, theme.colors.background.secondary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                title,
                color = theme.colors.type.emphasis,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}
