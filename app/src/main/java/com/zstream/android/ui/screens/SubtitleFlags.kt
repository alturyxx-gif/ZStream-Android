package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale

private const val ASSET_PREFIX = "file:///android_asset/flags/"

internal fun languageToFlag(code: String?): String? {
    val normalized = normalizeSubtitleLanguageCode(code).orEmpty()
    if (normalized.isBlank()) return null

    return when (normalized) {
        "pirate" -> "${ASSET_PREFIX}skull.svg"
        "kitty", "cat" -> "${ASSET_PREFIX}cat.png"
        "uwu" -> "${ASSET_PREFIX}uwu.png"
        "tok" -> "${ASSET_PREFIX}tokiPona.svg"
        "gl-es" -> "${ASSET_PREFIX}galicia.svg"
        "minion", "futhark" -> normalized
        else -> countryFlagCodeForLocale(normalized)?.let { "${ASSET_PREFIX}4x3/$it.svg" }
    }
}

internal fun normalizeSubtitleLanguageCode(code: String?): String? {
    val normalized = code?.trim()?.lowercase(Locale.US).orEmpty()
    if (normalized.isBlank()) return null
    return when (normalized) {
        "pb", "po", "br" -> "pt-BR"
        else -> normalized
    }
}

@Composable
internal fun SubtitleMenuIcon(icon: String, modifier: Modifier = Modifier) {
    when (icon) {
        "minion" -> MinionIcon(modifier)
        "futhark" -> FutharkIcon(modifier)
        else -> {
            if (icon.startsWith(ASSET_PREFIX)) {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = modifier.clip(RoundedCornerShape(6.dp)),
                )
            } else {
                Text(
                    text = icon,
                    fontSize = 18.sp,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun MinionIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp, 20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFFFF11A)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F2937)),
            )
        }
    }
}

@Composable
private fun FutharkIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp, 20.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF1F2937)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ᚠ",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun countryFlagCodeForLocale(locale: String): String? {
    val exact = when (locale) {
        "en", "en-us", "nv", "nv-us" -> "us"
        "es", "sp", "ea", "es-es" -> "es"
        "fr", "fr-fr" -> "fr"
        "de", "de-de" -> "de"
        "de-ch" -> "ch"
        "it", "it-it" -> "it"
        "pt" -> "pt"
        "pt-br", "br", "po", "pb" -> "br"
        "hi", "hi-in" -> "in"
        "ja", "ja-jp" -> "jp"
        "ko", "ko-kr" -> "kr"
        "zh", "zh-cn" -> "cn"
        "zh-hant", "zh-tw", "zt" -> "tw"
        "ru", "ru-ru" -> "ru"
        "ar", "ar-sa" -> "sa"
        "tr", "tr-tr" -> "tr"
        "vi", "vi-vn" -> "vn"
        "th", "th-th" -> "th"
        "id", "id-id" -> "id"
        "nl", "nl-nl" -> "nl"
        "pl", "pl-pl" -> "pl"
        "sv", "sv-se" -> "se"
        "da", "da-dk" -> "dk"
        "fi", "fi-fi" -> "fi"
        "no", "no-no" -> "no"
        "el", "el-gr" -> "gr"
        "he", "he-il" -> "il"
        "cs", "cs-cz" -> "cz"
        "hu", "hu-hu" -> "hu"
        "ro", "ro-ro" -> "ro"
        "uk", "uk-ua" -> "ua"
        "hr", "hr-hr" -> "hr"
        "fa", "fa-ir" -> "ir"
        "sr", "sr-rs" -> "rs"
        "gl-es" -> null
        else -> null
    }
    if (exact != null) return exact

    val base = locale.substringBefore('-')
    return when (base) {
        "en" -> "us"
        "es" -> "es"
        "fr" -> "fr"
        "de" -> "de"
        "it" -> "it"
        "pt" -> "pt"
        "hi" -> "in"
        "ja" -> "jp"
        "ko" -> "kr"
        "zh" -> "cn"
        "ru" -> "ru"
        "ar" -> "sa"
        "tr" -> "tr"
        "vi" -> "vn"
        "th" -> "th"
        "id" -> "id"
        "nl" -> "nl"
        "pl" -> "pl"
        "sv" -> "se"
        "da" -> "dk"
        "fi" -> "fi"
        "no" -> "no"
        "el" -> "gr"
        "he" -> "il"
        "cs" -> "cz"
        "hu" -> "hu"
        "ro" -> "ro"
        "uk" -> "ua"
        "hr" -> "hr"
        "fa" -> "ir"
        "sr" -> "rs"
        else -> null
    }
}
