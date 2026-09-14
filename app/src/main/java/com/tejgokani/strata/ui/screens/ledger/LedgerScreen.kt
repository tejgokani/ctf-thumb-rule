package com.tejgokani.strata.ui.screens.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.core.Formatting
import com.tejgokani.strata.ui.components.ChainBadge
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType
import com.tejgokani.strata.ui.vm.LedgerViewModel

@Composable
fun LedgerScreen(vm: LedgerViewModel) {
    val state by vm.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("LEDGER", style = StrataType.Label)
            OutlinedButton(onClick = vm::verifyChain, enabled = !state.verifying) {
                if (state.verifying) CircularProgressIndicator(modifier = Modifier.height(14.dp), color = StrataColors.Signal)
                else Text("VERIFY CHAIN", style = StrataType.Label.copy(color = StrataColors.Signal))
            }
        }
        Spacer(Modifier.height(8.dp))
        ChainBadge(result = state.verifyResult)
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(state.events) { event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(event.type.name, style = StrataType.Data)
                        Text("seq ${event.seq} · lamport ${event.lamport} · ${event.deviceId.take(12)}", style = StrataType.LabelDim)
                    }
                    Text(Formatting.timestamp(event.wallClockMs), style = StrataType.Data.copy(color = StrataColors.Dim))
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp).background(StrataColors.Grid),
                )
            }
        }
    }
}
