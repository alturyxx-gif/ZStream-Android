package com.zstream.android.theme.signature

import androidx.compose.ui.graphics.Color
import com.zstream.android.theme.ZStreamTheme

enum class BackgroundKind { AURORA, MESH, STARFIELD, SYNTHWAVE, PETALS, WAVES, EMBERS, NONE }

data class BackgroundSpec(val kind: BackgroundKind, val colors: List<Color>)

data class SignatureEffects(val grain: Float = 0f, val vignette: Float = 0f)
// my themes
data class SignatureThemeMeta(
    val id: String,
    val name: String,
    val tagline: String,
    val accent: Color,
    val background: BackgroundSpec,
    val effects: SignatureEffects,
)

private fun c(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

val signatureThemeMetas: List<SignatureThemeMeta> = listOf(
    SignatureThemeMeta(
        id = "aurora",
        name = "Aurora",
        tagline = "Ribbons of light over deep night",
        accent = c("#34D8B0"),
        background = BackgroundSpec(BackgroundKind.AURORA, listOf(c("#34D8B0"), c("#7C6BFF"), c("#5FE8C7"))),
        effects = SignatureEffects(grain = 0.035f, vignette = 0.5f),
    ),
    SignatureThemeMeta(
        id = "nocturne",
        name = "Nocturne",
        tagline = "Neon noir, city after dark",
        accent = c("#FF2FD0"),
        background = BackgroundSpec(BackgroundKind.MESH, listOf(c("#FF2FD0"), c("#00E5FF"))),
        effects = SignatureEffects(grain = 0.05f, vignette = 0.6f),
    ),
    SignatureThemeMeta(
        id = "cosmos",
        name = "Cosmos",
        tagline = "Adrift among distant stars",
        accent = c("#8C6BFF"),
        background = BackgroundSpec(BackgroundKind.STARFIELD, listOf(c("#8C6BFF"), c("#FF6BD6"))),
        effects = SignatureEffects(grain = 0.03f, vignette = 0.55f),
    ),
    SignatureThemeMeta(
        id = "sunset",
        name = "Sunset",
        tagline = "Retro horizon, endless dusk",
        accent = c("#FF5C8A"),
        background = BackgroundSpec(BackgroundKind.SYNTHWAVE, listOf(c("#FF5C8A"), c("#FFB84D"))),
        effects = SignatureEffects(grain = 0.035f, vignette = 0.45f),
    ),
    SignatureThemeMeta(
        id = "sakura",
        name = "Sakura",
        tagline = "Petals adrift on a quiet night",
        accent = c("#FF8FB3"),
        background = BackgroundSpec(BackgroundKind.PETALS, listOf(c("#FF8FB3"), c("#FFC9DC"))),
        effects = SignatureEffects(grain = 0.03f, vignette = 0.5f),
    ),
    SignatureThemeMeta(
        id = "abyss",
        name = "Abyss",
        tagline = "Deep water, slow currents",
        accent = c("#2BC7E0"),
        background = BackgroundSpec(BackgroundKind.WAVES, listOf(c("#2BC7E0"), c("#1E90C9"))),
        effects = SignatureEffects(grain = 0.03f, vignette = 0.55f),
    ),
    SignatureThemeMeta(
        id = "cinder",
        name = "Cinder",
        tagline = "Embers rising from the hearth",
        accent = c("#FF7A3D"),
        background = BackgroundSpec(BackgroundKind.EMBERS, listOf(c("#FF7A3D"), c("#FFB74D"))),
        effects = SignatureEffects(grain = 0.04f, vignette = 0.55f),
    ),
    SignatureThemeMeta(
        id = "obsidian",
        name = "Obsidian",
        tagline = "Polished dark glass, one clean accent",
        accent = c("#8FA8FF"),
        background = BackgroundSpec(BackgroundKind.NONE, listOf(c("#8FA8FF"))),
        effects = SignatureEffects(grain = 0.025f, vignette = 0.4f),
    ),
    SignatureThemeMeta(
        id = "glacier",
        name = "Glacier",
        tagline = "Breath on glass, morning cold",
        accent = c("#8FE3FF"),
        background = BackgroundSpec(BackgroundKind.MESH, listOf(c("#8FE3FF"), c("#EAF7FF"))),
        effects = SignatureEffects(grain = 0.025f, vignette = 0.4f),
    ),
    SignatureThemeMeta(
        id = "velvet",
        name = "Velvet",
        tagline = "Deep burgundy, gold-trimmed dusk",
        accent = c("#D8A657"),
        background = BackgroundSpec(BackgroundKind.WAVES, listOf(c("#7A2E3B"), c("#D8A657"))),
        effects = SignatureEffects(grain = 0.035f, vignette = 0.55f),
    ),
    SignatureThemeMeta(
        id = "solstice",
        name = "Solstice",
        tagline = "Sunbeams through warm stardust",
        accent = c("#FFD166"),
        background = BackgroundSpec(BackgroundKind.STARFIELD, listOf(c("#FFD166"), c("#FFF3D6"))),
        effects = SignatureEffects(grain = 0.03f, vignette = 0.45f),
    ),
    SignatureThemeMeta(
        id = "nightfall",
        name = "Nightfall",
        tagline = "Indigo silence, weight of night",
        accent = c("#5B6EFF"),
        background = BackgroundSpec(BackgroundKind.MESH, listOf(c("#5B6EFF"), c("#0B0C1E"))),
        effects = SignatureEffects(grain = 0.045f, vignette = 0.65f),
    ),
    SignatureThemeMeta(
        id = "meadow",
        name = "Meadow",
        tagline = "Fireflies over sleeping grass",
        accent = c("#6FCF7A"),
        background = BackgroundSpec(BackgroundKind.PETALS, listOf(c("#6FCF7A"), c("#FFE08A"))),
        effects = SignatureEffects(grain = 0.03f, vignette = 0.5f),
    ),
)

val signatureThemeMetaMap: Map<String, SignatureThemeMeta> = signatureThemeMetas.associateBy { it.id }

private fun palette(
    white: String,
    deepest: String,
    deep: String,
    base: String,
    raised: String,
    elevated: String,
    line: String,
    accentCore: String,
    accentCore2: String,
    accentSoft: String,
    textPrimary: String,
    textSecondary: String,
    textFaint: String,
) = SignaturePalette(
    white = c(white),
    bg = SignatureBgPalette(c(deepest), c(deep), c(base), c(raised), c(elevated), c(line)),
    accent = SignatureAccentPalette(c(accentCore), c(accentCore2), c(accentSoft)),
    text = SignatureTextPalette(c(textPrimary), c(textSecondary), c(textFaint)),
)

private val signaturePalettes: Map<String, SignaturePalette> = mapOf(
    "aurora" to palette(
        "#FFFFFF", "#060B14", "#0A1220", "#101B2E", "#16233A", "#1C2C46", "#2A3B57",
        "#34D8B0", "#7C6BFF", "#5FE8C7", "#EAF2F2", "#9FB3C8", "#63768C",
    ),
    "nocturne" to palette(
        "#FFFFFF", "#050505", "#0A0A0C", "#101013", "#17171B", "#1E1E24", "#2A2A31",
        "#FF2FD0", "#00E5FF", "#FF7AE8", "#F2F2F5", "#9A9AA6", "#5C5C68",
    ),
    "cosmos" to palette(
        "#FFFFFF", "#05040F", "#0A0820", "#100C30", "#181240", "#211A52", "#332863",
        "#8C6BFF", "#FF6BD6", "#B79CFF", "#EDEBFA", "#A79FD1", "#6C6394",
    ),
    "sunset" to palette(
        "#FFFFFF", "#0F0714", "#1A0B24", "#251034", "#331545", "#421A58", "#5A2470",
        "#FF5C8A", "#FFB84D", "#FF8FB0", "#FBEAF0", "#CDA0BB", "#8F6685",
    ),
    "sakura" to palette(
        "#FFFFFF", "#0F0508", "#180810", "#231018", "#301622", "#3E1C2C", "#54263C",
        "#FF8FB3", "#FFC9DC", "#FFB3CB", "#FCEEF2", "#D2A4B6", "#8F6674",
    ),
    "abyss" to palette(
        "#FFFFFF", "#020A0F", "#051620", "#082330", "#0C3040", "#103D52", "#175267",
        "#2BC7E0", "#1E90C9", "#6FE0F0", "#E4F6FA", "#93BFCC", "#5A828F",
    ),
    "cinder" to palette(
        "#FFFFFF", "#0C0908", "#16100D", "#201713", "#2C1F19", "#392820", "#4E362A",
        "#FF7A3D", "#FFB74D", "#FFA36B", "#FBEFE7", "#D1AC97", "#8F6E5C",
    ),
    "obsidian" to palette(
        "#FFFFFF", "#08090B", "#0F1114", "#16181C", "#1D2025", "#25282E", "#33373E",
        "#8FA8FF", "#5C7CFF", "#B4C4FF", "#F1F2F4", "#9BA1AC", "#5F6570",
    ),
    "glacier" to palette(
        "#FFFFFF", "#081419", "#0D1E26", "#132A34", "#1A3743", "#224656", "#305A6C",
        "#8FE3FF", "#C9F5FF", "#B4ECFF", "#EAFBFF", "#9FC7D4", "#5F8996",
    ),
    "velvet" to palette(
        "#FFFFFF", "#170A0E", "#210F15", "#2E151C", "#3D1C24", "#4E242D", "#623041",
        "#D8A657", "#7A2E3B", "#E9C583", "#F6EADB", "#C9A88F", "#8A6B5C",
    ),
    "solstice" to palette(
        "#FFFFFF", "#1A1408", "#241C0C", "#332811", "#433517", "#56451F", "#6B5A2B",
        "#FFD166", "#FFF3D6", "#FFE29B", "#FFF6E0", "#D9C393", "#93815A",
    ),
    "nightfall" to palette(
        "#FFFFFF", "#050510", "#090918", "#0F0F22", "#15162E", "#1C1D3C", "#2A2C54",
        "#5B6EFF", "#9AA5FF", "#7C87FF", "#E7E8FF", "#8E92C4", "#54578A",
    ),
    "meadow" to palette(
        "#FFFFFF", "#0A140C", "#0F1D11", "#152A18", "#1D3821", "#26482B", "#345C3B",
        "#6FCF7A", "#FFE08A", "#96E09E", "#EBF9EC", "#A4CBA8", "#658A69",
    ),
)

val signatureZStreamThemes: List<ZStreamTheme> = signatureThemeMetas.map { meta ->
    ZStreamTheme(
        id = meta.id,
        name = meta.name,
        colors = buildSignatureColors(signaturePalettes.getValue(meta.id)),
    )
}
