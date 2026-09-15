package com.tejgokani.strata.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import com.tejgokani.strata.core.Formatting
import com.tejgokani.strata.engine.Chunker
import com.tejgokani.strata.ui.components.SlabCard
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType
import com.tejgokani.strata.ui.vm.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel, onLocked: () -> Unit) {
    val state by vm.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScrollCompat()) {
        Text("SETTINGS", style = StrataType.Label)
        Spacer(Modifier.height(16.dp))

        SlabCard(modifier = Modifier.fillMaxWidth(), corner = false) {
            Column {
                Text("STORAGE", style = StrataType.LabelDim)
                Spacer(Modifier.height(8.dp))
                Text("Chunk size: ${Formatting.bytes(Chunker.DEFAULT_CHUNK_SIZE_BYTES)}", style = StrataType.Data)
                Text("A multiple of 256 KiB, per Drive's resumable-upload framing (R9).", style = StrataType.LabelDim)
            }
        }

        Spacer(Modifier.height(16.dp))
        SlabCard(modifier = Modifier.fillMaxWidth(), corner = false) {
            Column {
                Text("⚠ SIMULATE DISASTER RECOVERY", style = StrataType.Label.copy(color = StrataColors.Signal))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Rebuilds the manifest from scratch using only what your connected accounts " +
                        "hold — encrypted chunk metadata plus any surviving WAL replicas. Read-only: " +
                        "your current local database is not touched.",
                    style = StrataType.BodyDim,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = vm::runDisasterDrill, enabled = !state.running) {
                    if (state.running) CircularProgressIndicator(modifier = Modifier.height(16.dp), color = StrataColors.Signal)
                    else Text("RUN DRILL", style = StrataType.Label.copy(color = StrataColors.Signal))
                }
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.error!!, style = StrataType.Data.copy(color = StrataColors.Alert))
                }
                state.lastDrill?.let { drill ->
                    Spacer(Modifier.height(12.dp))
                    Text("Last run: ${Formatting.timestamp(drill.ranAtMs)}", style = StrataType.LabelDim)
                    Text("Recovered ${drill.recoveredFileCount} files, ${drill.recoveredPlacementCount} placements (${drill.adoptedFileCount} adopted from orphans)", style = StrataType.Data.copy(color = StrataColors.Ok))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SlabCard(modifier = Modifier.fillMaxWidth(), corner = false) {
            Column {
                Text("DANGER ZONE", style = StrataType.Label.copy(color = StrataColors.Alert))
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.lockVault(); onLocked() },
                    colors = ButtonDefaults.buttonColors(containerColor = StrataColors.Alert, contentColor = StrataColors.Bg),
                ) { Text("LOCK VAULT") }
            }
        }
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    return this.verticalScroll(scrollState)
}
