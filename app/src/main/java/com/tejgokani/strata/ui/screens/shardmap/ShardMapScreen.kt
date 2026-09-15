package com.tejgokani.strata.ui.screens.shardmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.core.Formatting
import com.tejgokani.strata.ui.components.HealthDot
import com.tejgokani.strata.ui.components.HealthState
import com.tejgokani.strata.ui.components.ShardMatrix
import com.tejgokani.strata.ui.components.SlabCard
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType
import com.tejgokani.strata.ui.vm.ShardMapViewModel

@Composable
fun ShardMapScreen(vm: ShardMapViewModel, fileId: String) {
    LaunchedEffect(fileId) { vm.load(fileId) }
    val state by vm.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("SHARD MAP", style = StrataType.Label)
        Spacer(Modifier.height(4.dp))
        Text(state.file?.name ?: "…", style = StrataType.DataLarge)
        Spacer(Modifier.height(20.dp))

        SlabCard(modifier = Modifier.fillMaxWidth(), corner = false) {
            Column {
                Text("CHUNK INDEX × ACCOUNT", style = StrataType.LabelDim)
                Spacer(Modifier.height(12.dp))
                ShardMatrix(
                    chunkCount = state.file?.chunkCount ?: 0,
                    accountCount = state.accounts.size,
                    cells = state.cells,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("LEGEND", style = StrataType.Label)
        Spacer(Modifier.height(8.dp))
        LegendRow(HealthState.OK, "Verified primary")
        LegendRow(HealthState.COLD, "Verified mirror")
        LegendRow(HealthState.WARN, "Pending / uploading")
        LegendRow(HealthState.ALERT, "Damaged / lost")

        Spacer(Modifier.height(20.dp))
        Text("ACCOUNTS", style = StrataType.Label)
        Spacer(Modifier.height(8.dp))
        state.accounts.forEachIndexed { i, acct ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("[$i] ${acct.email}", style = StrataType.Data)
            }
        }
    }
}

@Composable
private fun LegendRow(state: HealthState, label: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        HealthDot(state = state)
        Spacer(Modifier.height(0.dp))
        Text("  $label", style = StrataType.Data.copy(color = StrataColors.Dim))
    }
}
