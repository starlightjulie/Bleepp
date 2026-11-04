package com.bleepp.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.bleepp.R
import kotlinx.serialization.Serializable

/** The five colours everything is drawn from. Named for the scene: dark water lit by a warm beam. */
data class Palette(val sea: Color, val swell: Color, val beam: Color, val onBeam: Color, val text: Color) {
    val isLight get() = sea.luminance() > 0.5f
    val muted get() = text.copy(alpha = 0.68f)
    val line get() = text.copy(alpha = 0.16f)
}

class Colourway(val id: String, @StringRes val label: Int, val palette: Palette)

private fun way(id: String, label: Int, sea: Long, swell: Long, beam: Long, onBeam: Long, text: Long) =
    Colourway(id, label, Palette(Color(sea), Color(swell), Color(beam), Color(onBeam), Color(text)))

val Colourways = listOf(
    way("night_sea", R.string.cw_night_sea, 0xFF0D1B3D, 0xFF1C2E5C, 0xFFFFD36B, 0xFF3A2A05, 0xFFEAF0FF),
    way("harbour", R.string.cw_harbour, 0xFF10202B, 0xFF1F3644, 0xFFFF9D5C, 0xFF3D1C05, 0xFFE8F1F4),
    way("storm", R.string.cw_storm, 0xFF171B22, 0xFF2A313C, 0xFFC8D6E5, 0xFF1A222C, 0xFFEDF1F5),
    way("fjord", R.string.cw_fjord, 0xFF06262B, 0xFF12424A, 0xFF6FE7CF, 0xFF06302A, 0xFFE3F8F4),
    way("abyss", R.string.cw_abyss, 0xFF05070F, 0xFF141A2B, 0xFF7FB2FF, 0xFF06203F, 0xFFE6ECFA),
    way("ember", R.string.cw_ember, 0xFF23100B, 0xFF3F1F16, 0xFFFF7A45, 0xFF3A1002, 0xFFFBE9E1),
    way("plum", R.string.cw_plum, 0xFF1E1030, 0xFF34204F, 0xFFE6A7FF, 0xFF34104A, 0xFFF3E9FB),
    way("rose_tide", R.string.cw_rose_tide, 0xFF2A0F1F, 0xFF472038, 0xFFFF9BB8, 0xFF4A0F27, 0xFFFCE9F0),
    way("moss", R.string.cw_moss, 0xFF12200F, 0xFF25391F, 0xFFC4E86B, 0xFF263A07, 0xFFEEF6E4),
    way("arctic", R.string.cw_arctic, 0xFF0E2436, 0xFF1D3C55, 0xFFA6E6FF, 0xFF08324A, 0xFFE9F6FC),
    way("sodium", R.string.cw_sodium, 0xFF1A1508, 0xFF322A12, 0xFFFFB000, 0xFF332200, 0xFFFFF3D6),
    way("keeper", R.string.cw_keeper, 0xFF111111, 0xFF262626, 0xFFF2F2F2, 0xFF111111, 0xFFF2F2F2),
    way("paper_chart", R.string.cw_paper_chart, 0xFFF6EFE0, 0xFFEADFC8, 0xFF0F4C6E, 0xFFF6EFE0, 0xFF22303A),
    way("sea_fog", R.string.cw_sea_fog, 0xFFE6ECEF, 0xFFD3DDE2, 0xFF2B5F75, 0xFFF1F6F8, 0xFF1C2A31),
    way("sandbar", R.string.cw_sandbar, 0xFFF7E8D0, 0xFFEFD8B6, 0xFFC4471D, 0xFFFFF4E6, 0xFF3A2A1C),
    way("first_light", R.string.cw_first_light, 0xFFFBE3DC, 0xFFF4CFC6, 0xFF7A3BB0, 0xFFFBEFFF, 0xFF33222E),
)

enum class SeaBackground(@StringRes val label: Int) {
    BEAM(R.string.bg_beam),
    TIDE(R.string.bg_tide),
    STARS(R.string.bg_stars),
    RAIN(R.string.bg_rain),
    FOG(R.string.bg_fog),
    AURORA(R.string.bg_aurora),
    OFF(R.string.bg_off),
}

enum class LauncherIcon(val alias: String, @StringRes val label: Int, val background: Long, val bubble: Long) {
    AMBER(".IconAmber", R.string.icon_amber, 0xFFFFC531, 0xFF1E1A2E),
    INK(".IconInk", R.string.icon_ink, 0xFF1E1A2E, 0xFFFFC531),
    HARBOUR(".IconHarbour", R.string.icon_harbour, 0xFF10264D, 0xFFF4F7FF),
    CORAL(".IconCoral", R.string.icon_coral, 0xFFE4573D, 0xFFFFF4E8),
    MINT(".IconMint", R.string.icon_mint, 0xFF0F6E5C, 0xFFBDF5E3),
    PAPER(".IconPaper", R.string.icon_paper, 0xFFF4EFE6, 0xFF25222B),
}

@Serializable
data class CustomColours(val sea: Long, val swell: Long, val beam: Long, val onBeam: Long, val text: Long) {
    fun palette() = Palette(Color(sea), Color(swell), Color(beam), Color(onBeam), Color(text))
}

/** Everything the Appearance page can change, saved as one JSON value. */
@Serializable
data class Look(
    val colourway: String = Colourways.first().id,
    val custom: CustomColours? = null,
    val background: String = SeaBackground.BEAM.name,
    /** How fast backgrounds move, 0.25 to 2. */
    val speed: Float = 1f,
    /** How strongly backgrounds show, 0.2 to 1. */
    val strength: Float = 0.8f,
    val corners: Int = 18,
    val serifHeadings: Boolean = true,
) {
    val palette: Palette
        get() = custom?.palette() ?: (Colourways.firstOrNull { it.id == colourway } ?: Colourways.first()).palette

    val seaBackground: SeaBackground
        get() = SeaBackground.entries.firstOrNull { it.name == background } ?: SeaBackground.BEAM
}

@Serializable
data class SavedLook(val name: String, val look: Look)
