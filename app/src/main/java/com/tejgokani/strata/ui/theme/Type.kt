package com.tejgokani.strata.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Labels are uppercase with wide letter-spacing; data is always monospace with tabular figures
 * so numbers in a column never jitter as they change (plan §8).
 */
object StrataType {
    val Mono = FontFamily.Monospace
    val Sans = FontFamily.SansSerif

    val Label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.12.em)
    val LabelDim = Label.copy(color = StrataColors.Dim)

    val Data = TextStyle(fontFamily = Mono, fontSize = 13.sp)
    val DataLarge = TextStyle(fontFamily = Mono, fontSize = 15.sp)

    val Readout = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = (-0.02).em)
    val ReadoutUnit = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = StrataColors.Dim)

    val Body = TextStyle(fontFamily = Sans, fontSize = 13.sp, color = StrataColors.Fg)
    val BodyDim = Body.copy(color = StrataColors.Dim)

    fun materialTypography() = Typography(
        bodyLarge = Body, bodyMedium = Body, bodySmall = BodyDim,
        labelLarge = Label, labelMedium = Label, labelSmall = LabelDim,
        titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    )
}
