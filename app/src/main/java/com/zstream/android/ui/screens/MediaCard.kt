package com.zstream.android.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.zstream.android.AppChromeViewModel
import com.zstream.android.data.model.Media
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper

typealias MediaCardComponent = @Composable (
    media: Media,
    onClick: () -> Unit,
    percentage: Float?,
    seriesLabel: String?,
    width: Dp?,
    height: Dp?,
    editOverlay: Boolean
) -> Unit

val LocalMediaCard = compositionLocalOf<MediaCardComponent> {
    { m, o, p, s, w, h, e -> MediaCardStandard(m, o, p, s, w, h, e) }
}

@Composable
fun MediaCard(
    media: Media,
    onClick: () -> Unit,
    percentage: Float? = null,
    seriesLabel: String? = null,
    width: Dp? = null,
    height: Dp? = null,
    editOverlay: Boolean = false,
) {
    LocalMediaCard.current(media, onClick, percentage, seriesLabel, width, height, editOverlay)
}

@Composable
fun MediaCardStandard(
    media: Media,
    onClick: () -> Unit,
    percentage: Float? = null,
    seriesLabel: String? = null,
    width: Dp? = null,
    height: Dp? = null,
    editOverlay: Boolean = false,
) {
    val theme = LocalZStreamTheme.current
    val posterUrl = media.posterUrl("w342")
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }
    val cardWidth: Dp = width ?: if (isTv) 120.dp else 110.dp
    val cardHeight: Dp = height ?: if (isTv) 180.dp else 165.dp

    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(14.dp),
        outlineColor = Color.White,
        start = 4.dp,
        top = 4.dp,
        end = 4.dp,
        bottom = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .onFocusChanged { isFocused = it.isFocused }
                .graphicsLayer {
                    shadowElevation = if (isFocused && isTv) 12.dp.toPx() else 0f
                    clip = false
                }
                .clickable(onClick = onClick)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
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
                            modifier = Modifier.size(if (isTv) 48.dp else 40.dp).align(Alignment.Center)
                        )
                    }

                    if (editOverlay) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                        if (isTv) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit item",
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center).size(36.dp),
                            )
                        }
                    }

                    if (percentage != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            theme.colors.mediaCard.hoverShadow.copy(alpha = 0.5f),
                                        )
                                    )
                                )
                        )
                    }

                    if (seriesLabel != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(if (isTv) 6.dp else 6.dp)
                                .clip(RoundedCornerShape(if (isTv) 4.dp else 4.dp))
                                .background(theme.colors.mediaCard.badge)
                                .padding(horizontal = if (isTv) 6.dp else 6.dp, vertical = if (isTv) 2.dp else 0.dp),
                        ) {
                            Text(
                                text = seriesLabel,
                                color = theme.colors.mediaCard.badgeText,
                                fontSize = if (isTv) 11.sp else 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (percentage != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(if (isTv) 8.dp else 10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isTv) 3.dp else 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(theme.colors.progress.background)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(percentage / 100f)
                                        .background(theme.colors.progress.filled, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Text(
                    text = media.displayTitle,
                    color = theme.colors.type.emphasis,
                    fontSize = if (isTv) 13.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.offset(y = if (isTv) (-4).dp else (-6).dp)
                ) {
                    val typeLabel = if (media.type == "tv") "Show" else "Movie"
                    val year = (media.displayDate).take(4).takeIf { it.length == 4 } ?: ""
                    Text(
                        text = typeLabel,
                        color = theme.colors.type.dimmed,
                        fontSize = if (isTv) 11.sp else 10.sp,
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
                            fontSize = if (isTv) 11.sp else 10.sp,
                        )
                    }
                }
            }

            if (isFocused && isTv && !editOverlay) {
                Box(

                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
internal fun TvMediaEditMenu(
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalZStreamTheme.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Popup(
        alignment = Alignment.CenterEnd,
        offset = IntOffset(16, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        Surface(
            color = theme.colors.modal.background,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.25f)),
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.width(170.dp).padding(8.dp)) {
                onEdit?.let {
                    TvMediaEditMenuItem(Icons.Default.Edit, "Edit", Modifier.focusRequester(firstFocus)) {
                        it()
                        onDismiss()
                    }
                }
                TvMediaEditMenuItem(
                    Icons.Default.Close,
                    "Remove",
                    if (onEdit == null) Modifier.focusRequester(firstFocus) else Modifier,
                ) {
                    onRemove()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun TvMediaEditMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalZStreamTheme.current
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(visible = focused, shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = theme.colors.type.emphasis, modifier = Modifier.size(20.dp))
            Text(label, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun MediaCardMinimal(
    media: Media,
    onClick: () -> Unit,
    percentage: Float? = null,
    seriesLabel: String? = null,
    width: Dp? = null,
    height: Dp? = null,
    editOverlay: Boolean = false,
) {
    val theme = LocalZStreamTheme.current
    val posterUrl = media.posterUrl("w342")
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }
    val cardWidth: Dp = width ?: if (isTv) 120.dp else 110.dp
    val cardHeight: Dp = height ?: if (isTv) 180.dp else 165.dp

    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(12.dp),
        outlineColor = Color.White,
        gap = 4.dp
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .onFocusChanged { isFocused = it.isFocused }
                .graphicsLayer {
                    shadowElevation = if (isFocused && isTv) 12.dp.toPx() else 0f
                    clip = false
                }
                .clickable(onClick = onClick)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
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
                            modifier = Modifier.size(if (isTv) 48.dp else 40.dp).align(Alignment.Center)
                        )
                    }

                    if (editOverlay) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                        if (isTv) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit item",
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center).size(36.dp),
                            )
                        }
                    }

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

                    if (seriesLabel != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(if (isTv) 6.dp else 6.dp)
                                .clip(RoundedCornerShape(if (isTv) 4.dp else 4.dp))
                                .background(theme.colors.mediaCard.badge)
                                .padding(horizontal = if (isTv) 6.dp else 6.dp, vertical = if (isTv) 2.dp else 0.dp),
                        ) {
                            Text(
                                text = seriesLabel,
                                color = theme.colors.mediaCard.badgeText,
                                fontSize = if (isTv) 11.sp else 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (percentage != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(if (isTv) 8.dp else 10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isTv) 3.dp else 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(theme.colors.progress.background)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(percentage / 100f)
                                        .background(theme.colors.progress.filled, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }
                }
            }

            if (isFocused && isTv && !editOverlay) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * A double-wide end-of-carousel card for TV that replaces the ZsTextButton "View more".
 * Width is 2× a standard TV card plus one gap so it sits flush in the LazyRow.
 * Its Column structure mirrors MediaCardStandard exactly so the total height is identical.
 */
@Composable
fun ViewMoreCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }

    // Matches MediaCardStandard's TV dimensions exactly
    // 2 × cardWidth(120) + 1 × gap(12) = 252.dp
    val cardWidth = 252.dp
    val chromeVm: AppChromeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val useMinimalCards by chromeVm.useMinimalCards.collectAsState()
    val cardHeight = if (useMinimalCards) 180.dp else 236.dp

    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(14.dp),
        outlineColor = Color.White,
        start = 4.dp,
        top = 4.dp,
        end = 4.dp,
        bottom = 0.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) onFocused?.invoke()
                }
                .graphicsLayer {
                    shadowElevation = if (isFocused && isTv) 12.dp.toPx() else 0f
                    clip = false
                }
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.colors.background.secondary)
                        .border(
                            1.dp,
                            theme.colors.type.divider.copy(alpha = 0.35f),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = theme.colors.type.emphasis,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = "View more",
                            color = theme.colors.type.emphasis,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // TV focus overlay — matches MediaCardStandard
                    if (isFocused && isTv) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                        )
                    }
                }
        }
    }
}
