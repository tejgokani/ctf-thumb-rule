package com.tejgokani.strata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.ui.theme.StrataColors

/**
 * The signature container of the brutalist data-vault: a hairline-bordered panel with a small
 * corner tick, no elevation, no rounded softness — a bordered slab of instrument panel (plan §8).
 */
@Composable
fun SlabCard(
    modifier: Modifier = Modifier,
    corner: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .background(StrataColors.Surface)
            .border(1.dp, StrataColors.Hairline, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .padding(16.dp),
    ) {
        content()
        if (corner) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(6.dp)
                    .background(StrataColors.Signal),
            )
        }
    }
}
