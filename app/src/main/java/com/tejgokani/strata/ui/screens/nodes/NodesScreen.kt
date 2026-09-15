package com.tejgokani.strata.ui.screens.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.core.Formatting
import com.tejgokani.strata.engine.AuthState
import com.tejgokani.strata.ui.LocalActivityResultBridge
import com.tejgokani.strata.ui.components.BarMeter
import com.tejgokani.strata.ui.components.HealthDot
import com.tejgokani.strata.ui.components.HealthState
import com.tejgokani.strata.ui.components.SlabCard
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType
import com.tejgokani.strata.ui.vm.NodesViewModel

@Composable
fun NodesScreen(vm: NodesViewModel) {
    val state by vm.state.collectAsState()
    val bridge = LocalActivityResultBridge.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("NODES", style = StrataType.Label)
            Button(
                onClick = { vm.addNode(bridge) }, enabled = !state.addingNode,
                colors = ButtonDefaults.buttonColors(containerColor = StrataColors.Signal, contentColor = StrataColors.Bg),
            ) {
                if (state.addingNode) CircularProgressIndicator(modifier = Modifier.height(16.dp), color = StrataColors.Bg)
                else Text("+ ADD NODE")
            }
        }
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.error!!, style = StrataType.Data.copy(color = StrataColors.Alert))
        }
        Spacer(Modifier.height(16.dp))

        if (state.accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No nodes connected. Add a Google account to begin.", style = StrataType.BodyDim)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.accounts) { account ->
                    SlabCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HealthDot(
                                        state = when (account.authState) {
                                            AuthState.OK -> HealthState.OK
                                            AuthState.NEEDS_CONSENT -> HealthState.WARN
                                            AuthState.ACCOUNT_MISSING, AuthState.ACCESS_REVOKED -> HealthState.ALERT
                                        },
                                    )
                                    Spacer(Modifier.height(0.dp))
                                    Text("  ${account.email}", style = StrataType.DataLarge)
                                }
                                Text(account.authState.name, style = StrataType.Data.copy(color = StrataColors.Dim))
                            }
                            Spacer(Modifier.height(12.dp))
                            BarMeter(fraction = account.usageRatio.toFloat())
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${Formatting.bytes(account.quotaUsageBytes)} / ${Formatting.bytes(account.quotaLimitBytes)}", style = StrataType.Data)
                                Text("${Formatting.bytes(account.effectiveFreeBytes)} usable", style = StrataType.Data.copy(color = StrataColors.Ok))
                            }
                            if (account.reclaimableBytes > 0) {
                                Text("${Formatting.bytes(account.reclaimableBytes)} reclaimable", style = StrataType.Data.copy(color = StrataColors.Signal))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Last checked: ${Formatting.timestamp(account.lastQuotaRefreshMs)}",
                                style = StrataType.LabelDim,
                            )
                        }
                    }
                }
            }
        }
    }
}
