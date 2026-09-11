package com.tejgokani.strata.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.tejgokani.strata.ui.theme.StrataColors

enum class ShardCellState { VERIFIED, MIRROR, PENDING, DAMAGED, EMPTY }

data class ShardCell(val chunkIndex: Int, val accountIndex: Int, val state: ShardCellState)

/**
 * The signature screen (plan §8): a literal chunk-index × account matrix, one cell per placement.
 * This is the physical stripe layout of a file made visible — the whole point of exposing the
 * machinery rather than hiding it behind a spinner.
 */
@Composable
fun ShardMatrix(
    chunkCount: Int,
    accountCount: Int,
    cells: List<ShardCell>,
    modifier: Modifier = Modifier,
) {
    val cellLookup = remember(cells) { cells.associateBy { it.chunkIndex to it.accountIndex } }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (accountCount == 0) 1f else chunkCount.coerceAtLeast(1) / accountCount.toFloat().coerceAtLeast(1f)),
    ) {
        if (chunkCount == 0 || accountCount == 0) return@Canvas
        val cellW = size.width / chunkCount
        val cellH = size.height / accountCount
        val gap = (minOf(cellW, cellH) * 0.08f).coerceAtLeast(0.5f)

        for (chunkIdx in 0 until chunkCount) {
            for (acctIdx in 0 until accountCount) {
                val state = cellLookup[chunkIdx to acctIdx]?.state ?: ShardCellState.EMPTY
                val color = colorFor(state)
                val topLeft = Offset(chunkIdx * cellW + gap / 2, acctIdx * cellH + gap / 2)
                val cellSize = Size(cellW - gap, cellH - gap)
                if (state == ShardCellState.EMPTY) {
                    drawRect(color = StrataColors.Grid, topLeft = topLeft, size = cellSize, style = Stroke(width = 1f))
                } else {
                    drawRect(color = color, topLeft = topLeft, size = cellSize)
                }
            }
        }
    }
}

private fun colorFor(state: ShardCellState): Color = when (state) {
    ShardCellState.VERIFIED -> StrataColors.Ok
    ShardCellState.MIRROR -> StrataColors.Cold
    ShardCellState.PENDING -> StrataColors.Signal
    ShardCellState.DAMAGED -> StrataColors.Alert
    ShardCellState.EMPTY -> StrataColors.Grid
}
