package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zstream.android.data.model.Media
import com.zstream.android.theme.LocalZStreamTheme

@Composable
fun MediaCard(media: Media, onClick: () -> Unit) {
    val theme = LocalZStreamTheme.current
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = media.posterUrl("w342"),
            contentDescription = media.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.colors.background.secondary)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = media.displayTitle,
            color = theme.colors.type.emphasis,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val typeLabel = if (media.type == "tv") "Show" else "Movie"
            val year = (media.displayDate).take(4).takeIf { it.length == 4 } ?: ""
            Text(
                text = typeLabel,
                color = theme.colors.type.dimmed,
                fontSize = 10.sp,
            )
            if (year.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(theme.colors.global.accentA)
                )
                Text(
                    text = year,
                    color = theme.colors.type.dimmed,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
