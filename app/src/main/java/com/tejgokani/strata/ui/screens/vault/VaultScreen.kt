package com.tejgokani.strata.ui.screens.vault

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.core.Formatting
import com.tejgokani.strata.ui.components.BarMeter
import com.tejgokani.strata.ui.components.HealthDot
import com.tejgokani.strata.ui.components.HealthState
import com.tejgokani.strata.ui.components.SlabCard
import com.tejgokani.strata.ui.components.StatReadout
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType
import com.tejgokani.strata.ui.vm.VaultViewModel

@Composable
fun VaultScreen(vm: VaultViewModel) {
    val state by vm.state.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SlabCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    val (value, unit) = Formatting.bytes(state.totalFreeBytes).split(" ").let { it[0] to it.getOrElse(1) { "" } }
                    StatReadout(label = "POOLED FREE CAPACITY", value = value, unit = unit)
                    Spacer(Modifier.height(16.dp))
                    BarMeter(fraction = state.usageFraction)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${Formatting.bytes(state.totalUsageBytes)} USED", style = StrataType.LabelDim)
                        Text("${Formatting.bytes(state.totalLimitBytes)} TOTAL", style = StrataType.LabelDim)
                    }
                    if (state.totalReclaimableBytes > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("${Formatting.bytes(state.totalReclaimableBytes)} RECLAIMABLE (in trash)", style = StrataType.Data.copy(color = StrataColors.Signal))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SlabCard(modifier = Modifier.weight(1f), corner = false) {
                    StatReadout(label = "NODES", value = state.accounts.size.toString())
                }
                SlabCard(modifier = Modifier.weight(1f), corner = false) {
                    StatReadout(label = "FILES", value = state.fileCount.toString())
                }
            }
        }

        item {
            Text("NODES", style = StrataType.Label, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
        items(state.accounts) { account ->
            SlabCard(modifier = Modifier.fillMaxWidth(), corner = false) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row {
                            HealthDot(state = if (account.isFullyUsable) HealthState.OK else HealthState.ALERT)
                            Spacer(Modifier.height(0.dp))
                            Text("  ${account.email}", style = StrataType.Data)
                        }
                        Text(Formatting.percent(account.usageRatio.toFloat()), style = StrataType.Data.copy(color = StrataColors.Dim))
                    }
                    Spacer(Modifier.height(8.dp))
                    BarMeter(fraction = account.usageRatio.toFloat(), segments = 32)
                }
            }
        }

        item {
            Text("RECENT ACTIVITY", style = StrataType.Label, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
        items(state.recentEvents) { event ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(event.type.name, style = StrataType.Data)
                Text(Formatting.timestamp(event.wallClockMs), style = StrataType.Data.copy(color = StrataColors.Dim))
            }
        }
    }
}
