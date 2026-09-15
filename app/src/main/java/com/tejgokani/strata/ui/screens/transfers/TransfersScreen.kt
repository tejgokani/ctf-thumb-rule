package com.tejgokani.strata.ui.screens.transfers

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.engine.PlacementState
import com.tejgokani.strata.ui.components.HealthDot
import com.tejgokani.strata.ui.components.HealthState
import com.tejgokani.strata.ui.components.SlabCard
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType
import com.tejgokani.strata.ui.vm.TransfersViewModel

@Composable
fun TransfersScreen(vm: TransfersViewModel) {
    val state by vm.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("TRANSFERS", style = StrataType.Label)
        Spacer(Modifier.height(16.dp))
        if (state.rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing in flight. Uploads and their per-account placements show up here.", style = StrataType.BodyDim)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.rows) { row ->
                    SlabCard(modifier = Modifier.fillMaxWidth(), corner = false) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HealthDot(
                                    state = when (row.placement.state) {
                                        PlacementState.VERIFIED -> HealthState.OK
                                        PlacementState.LOST -> HealthState.ALERT
                                        PlacementState.TRASHED_REPAIRABLE -> HealthState.WARN
                                        else -> HealthState.COLD
                                    },
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("${row.file.name} · chunk ${row.chunk.index}", style = StrataType.Data)
                                    Text(row.placement.accountId, style = StrataType.LabelDim)
                                }
                            }
                            Text(
                                row.placement.state.name + if (row.placement.isMirror) " (mirror)" else "",
                                style = StrataType.Data.copy(color = StrataColors.Dim),
                            )
                        }
                    }
                }
            }
        }
    }
}
