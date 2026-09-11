package com.tejgokani.strata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType

data class TableColumn(val header: String, val weight: Float = 1f)

/** A dense, monospace, hairline-ruled table — the primary way STRATA presents lists of anything. */
@Composable
fun DataTable(
    columns: List<TableColumn>,
    rowCount: Int,
    modifier: Modifier = Modifier,
    cell: @Composable (row: Int, col: Int) -> Unit,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            columns.forEach { c ->
                BasicText(text = c.header, style = StrataType.LabelDim, modifier = Modifier.weight(c.weight))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().background(StrataColors.Hairline).height1())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(rowCount) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    columns.forEachIndexed { col, c ->
                        Box(modifier = Modifier.weight(c.weight)) { cell(row, col) }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().background(StrataColors.Grid).height1())
            }
        }
    }
}

private fun Modifier.height1() = this.height(1.dp)
