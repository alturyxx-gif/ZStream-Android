package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zstream.android.data.model.Media
import com.zstream.android.theme.LocalZStreamTheme
import androidx.compose.runtime.compositionLocalOf

// Match p-stream bar colors
private val ProgressTrack = Color(0xFF4A4A5A)
private val ProgressFill = Color(0xFFA78BFA)
private val BadgeBg = Color(0xFF2D2D3D)
private val BadgeText = Color(0xFFC4C4D4)


typealias MediaCardComponent = @Composable (
    media: Media,
    onClick: () -> Unit,
    percentage: Float?,
    seriesLabel: String?
) -> Unit

val LocalMediaCard = compositionLocalOf<MediaCardComponent> {
    { m, o, p, s -> MediaCardStandard(m, o, p, s) }
}

@Composable
fun MediaCard(
    media: Media,
    onClick: () -> Unit,
    percentage: Float? = null,
    seriesLabel: String? = null,
) {
    LocalMediaCard.current(media, onClick, percentage, seriesLabel)
}
@Composable
fun MediaCardStandard(
    media: Media,
    onClick: () -> Unit,
    percentage: Float? = null,
    seriesLabel: String? = null,
) {
    val theme = LocalZStreamTheme.current
    val posterUrl = media.posterUrl("w342")

    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.colors.background.secondary),
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = media.displayTitle,
                    contentScale = ContentScale.Crop,
                    onError = { state ->
                        android.util.Log.e("MediaCard", "Failed to load image for ${media.displayTitle} (${media.id}): $posterUrl", state.result.throwable)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (media.type == "tv") Icons.Filled.Tv else Icons.Filled.Movie,
                    contentDescription = null,
                    tint = theme.colors.type.dimmed.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp).align(Alignment.Center)
                )
            }

            // Gradient overlay at bottom (matching p-stream's from-mediaCard-shadow)
            if (percentage != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x80000000))
                            )
                        )
                )
            }

            // SE badge (top-right)
            if (seriesLabel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BadgeBg)
                        .padding(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = seriesLabel,
                        color = BadgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Progress bar at bottom
            if (percentage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp) // Thickness of the progress bar
                            .clip(RoundedCornerShape(2.dp))
                            .background(ProgressTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(percentage / 100f)
                                .background(ProgressFill, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        // Title
        Text(
            text = media.displayTitle,
            color = theme.colors.type.emphasis,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Metadata Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(y = (-10).dp)
        ) {
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

@Composable
fun MediaCardMinimal(
    media: Media,
    onClick: () -> Unit,
    percentage: Float? = null,
    seriesLabel: String? = null,
) {
    val theme = LocalZStreamTheme.current
    val posterUrl = media.posterUrl("w342")

    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.colors.background.secondary),
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = media.displayTitle,
                    contentScale = ContentScale.Crop,
                    onError = { state ->
                        android.util.Log.e("MediaCard", "Failed to load image for ${media.displayTitle} (${media.id}): $posterUrl", state.result.throwable)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (media.type == "tv") Icons.Filled.Tv else Icons.Filled.Movie,
                    contentDescription = null,
                    tint = theme.colors.type.dimmed.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp).align(Alignment.Center)
                )
            }

            // Gradient overlay at bottom (matching p-stream's from-mediaCard-shadow)
            if (percentage != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x80000000))
                            )
                        )
                )
            }

            // SE badge (top-right)
            if (seriesLabel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BadgeBg)
                        .padding(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = seriesLabel,
                        color = BadgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Progress bar at bottom
            if (percentage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp) // Thickness of the progress bar
                            .clip(RoundedCornerShape(2.dp))
                            .background(ProgressTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(percentage / 100f)
                                .background(ProgressFill, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
