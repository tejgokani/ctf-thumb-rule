package com.tejgokani.strata.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType

/** A huge tabular-numeral readout with a unit and an optional delta — the vault screen's headline figure. */
@Composable
fun StatReadout(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaPositive: Boolean = true,
) {
    Column(modifier = modifier) {
        BasicText(text = label, style = StrataType.LabelDim)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            BasicText(text = value, style = StrataType.Readout.copy(color = StrataColors.Fg))
            if (unit != null) {
                BasicText(text = " $unit", style = StrataType.ReadoutUnit, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        if (delta != null) {
            BasicText(
                text = delta,
                style = StrataType.Data.copy(color = if (deltaPositive) StrataColors.Ok else StrataColors.Alert),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
