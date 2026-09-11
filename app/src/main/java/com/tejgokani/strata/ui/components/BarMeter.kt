package com.tejgokani.strata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.ui.theme.StrataColors

/** A segmented capacity meter — `[████████░░░░░░]` rendered as solid blocks, not a smooth bar. */
@Composable
fun BarMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    segments: Int = 24,
    filledColor: Color = StrataColors.Signal,
    emptyColor: Color = StrataColors.Grid,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val filledCount = (clamped * segments).toInt().coerceIn(0, segments)
    Row(modifier = modifier.fillMaxWidth().height(10.dp)) {
        repeat(segments) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (i < filledCount) filledColor else emptyColor),
            )
            if (i != segments - 1) Spacer(modifier = Modifier.width(1.dp))
        }
    }
}
