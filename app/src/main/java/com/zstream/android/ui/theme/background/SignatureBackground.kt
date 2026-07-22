package com.zstream.android.ui.theme.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import com.zstream.android.theme.signature.BackgroundKind
import com.zstream.android.theme.signature.SignatureThemeMeta
import com.zstream.android.theme.signature.signatureThemeMetaMap

@Composable
fun SignatureBackgroundLayer(
    meta: SignatureThemeMeta,
    modifier: Modifier = Modifier,
    staticFallback: Boolean = false,
) {
    Box(modifier = modifier.clipToBounds()) {
        if (staticFallback) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(meta.accent.copy(alpha = 0.12f)),
            )
        } else {
            when (meta.background.kind) {
                BackgroundKind.AURORA -> AuroraBackground(meta.background.colors, Modifier.fillMaxSize())
                BackgroundKind.MESH -> MeshBackground(meta.background.colors, Modifier.fillMaxSize())
                BackgroundKind.STARFIELD -> {
                    MeshBackground(meta.background.colors, Modifier.fillMaxSize())
                    ParticleField(ParticleVariant.STARFIELD, meta.background.colors, Modifier.fillMaxSize())
                }
                BackgroundKind.SYNTHWAVE -> SynthwaveBackground(meta.background.colors, Modifier.fillMaxSize())
                BackgroundKind.PETALS -> ParticleField(ParticleVariant.PETALS, meta.background.colors, Modifier.fillMaxSize())
                BackgroundKind.WAVES -> WavesBackground(meta.background.colors, Modifier.fillMaxSize())
                BackgroundKind.EMBERS -> ParticleField(ParticleVariant.EMBERS, meta.background.colors, Modifier.fillMaxSize())
                BackgroundKind.NONE -> {}
            }
        }
        if (meta.effects.grain > 0f) {
            Grain(opacity = meta.effects.grain, modifier = Modifier.fillMaxSize())
        }
        if (meta.effects.vignette > 0f) {
            Vignette(opacity = meta.effects.vignette, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun ThemeAmbientBackground(themeId: String?, staticFallback: Boolean, modifier: Modifier = Modifier) {
    val meta = themeId?.let { signatureThemeMetaMap[it] } ?: return
    SignatureBackgroundLayer(meta = meta, modifier = modifier, staticFallback = staticFallback)
}
