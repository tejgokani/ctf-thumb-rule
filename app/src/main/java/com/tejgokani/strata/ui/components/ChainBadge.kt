package com.tejgokani.strata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.ledger.ChainVerifyResult
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType

/** Renders the ledger's hash-chain verification result as a small status pill. */
@Composable
fun ChainBadge(result: ChainVerifyResult?, modifier: Modifier = Modifier) {
    val (color, text) = when (result) {
        null -> StrataColors.Dim to "NOT VERIFIED"
        is ChainVerifyResult.Valid -> StrataColors.Ok to "CHAIN VALID"
        is ChainVerifyResult.Empty -> StrataColors.Dim to "EMPTY"
        is ChainVerifyResult.Broken -> StrataColors.Alert to "BROKEN @ SEQ ${result.atSeq}"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HealthDot(
            state = when (result) {
                is ChainVerifyResult.Valid -> HealthState.OK
                is ChainVerifyResult.Broken -> HealthState.ALERT
                else -> HealthState.COLD
            },
        )
        BasicText(text = "  $text", style = StrataType.Label.copy(color = color))
    }
}
