package com.tejgokani.strata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.ui.theme.StrataColors

enum class HealthState { OK, WARN, ALERT, COLD }

@Composable
fun HealthDot(state: HealthState, modifier: Modifier = Modifier) {
    val color = when (state) {
        HealthState.OK -> StrataColors.Ok
        HealthState.WARN -> StrataColors.Signal
        HealthState.ALERT -> StrataColors.Alert
        HealthState.COLD -> StrataColors.Cold
    }
    Box(modifier = modifier.size(8.dp).background(color, CircleShape))
}
