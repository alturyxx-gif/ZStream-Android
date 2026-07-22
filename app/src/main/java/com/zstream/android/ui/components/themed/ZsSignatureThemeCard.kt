package com.zstream.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zstream.android.theme.signature.SignatureThemeMeta
import com.zstream.android.ui.theme.background.SignatureBackgroundLayer

@Composable
fun ZsSignatureThemeGrid(
    metas: List<SignatureThemeMeta>,
    activeId: String,
    animate: Boolean,
    onSelect: (SignatureThemeMeta) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metas.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { meta ->
                    Box(modifier = Modifier.weight(1f)) {
                        ZsSignatureThemeCard(
                            meta = meta,
                            selected = meta.id == activeId,
                            animate = animate,
                            onClick = { onSelect(meta) },
                        )
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ZsSignatureThemeCard(
    meta: SignatureThemeMeta,
    selected: Boolean,
    animate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0A0A0C))
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier.border(2.dp, meta.accent.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            ),
    ) {
        SignatureBackgroundLayer(
            meta = meta,
            modifier = Modifier.fillMaxSize(),
            staticFallback = !animate,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        startY = 0.35f,
                    ),
                ),
        )

        if (selected) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("Active", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = meta.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = meta.tagline,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
