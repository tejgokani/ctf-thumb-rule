package com.tejgokani.strata.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brutalist data-vault palette (plan §8): this is an instrument, not a consumer app. Density,
 * monospace, hairlines, hard edges — the aesthetic IS the information. No elevation, no soft
 * shadows; state is communicated by color and borders alone.
 */
object StrataColors {
    val Bg = Color(0xFF0A0A0A)
    val Surface = Color(0xFF121212)
    val Hairline = Color(0xFF2A2A2A)
    val Grid = Color(0xFF1A1A1A)

    val Fg = Color(0xFFE8E8E3)
    val Dim = Color(0xFF8A8A85)

    /** Amber — active/warning/attention. */
    val Signal = Color(0xFFFFB020)
    /** Green — healthy/verified. */
    val Ok = Color(0xFF4ADE80)
    /** Red — degraded/broken/damaged. */
    val Alert = Color(0xFFFF3B30)
    /** Blue — idle/cold/unused. */
    val Cold = Color(0xFF5B9DFF)
}
