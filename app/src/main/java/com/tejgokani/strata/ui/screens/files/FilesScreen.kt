package com.tejgokani.strata.ui.screens.files

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tejgokani.strata.core.Formatting
import com.tejgokani.strata.engine.FileRecord
import com.tejgokani.strata.engine.FileStatus
import com.tejgokani.strata.ui.LocalActivityResultBridge
import com.tejgokani.strata.ui.components.HealthDot
import com.tejgokani.strata.ui.components.HealthState
import com.tejgokani.strata.ui.components.SlabCard
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType
import com.tejgokani.strata.ui.vm.FilesViewModel

@Composable
fun FilesScreen(vm: FilesViewModel, onOpenShardMap: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val bridge = LocalActivityResultBridge.current
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("FILES", style = StrataType.Label)
            Row {
                Button(
                    onClick = { vm.uploadFile(bridge, context.contentResolver, mirror = false) }, enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = StrataColors.Signal, contentColor = StrataColors.Bg),
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.height(16.dp), color = StrataColors.Bg)
                    else Text("+ UPLOAD")
                }
            }
        }
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.error!!, style = StrataType.Data.copy(color = StrataColors.Alert))
        }
        if (state.lastAction != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.lastAction!!, style = StrataType.Data.copy(color = StrataColors.Ok))
        }
        Spacer(Modifier.height(16.dp))

        if (state.files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No files yet. Upload something to stripe it across your nodes.", style = StrataType.BodyDim)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.files) { file ->
                    FileRow(file, onOpenShardMap = { onOpenShardMap(file.id) }, onDownload = {
                        vm.downloadFile(bridge, context.contentResolver, file)
                    })
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: FileRecord, onOpenShardMap: () -> Unit, onDownload: () -> Unit) {
    SlabCard(modifier = Modifier.fillMaxWidth(), corner = false) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HealthDot(
                        state = when (file.status) {
                            FileStatus.COMMITTED -> HealthState.OK
                            FileStatus.UPLOADING, FileStatus.INCOMPLETE_NO_SPACE -> HealthState.WARN
                            FileStatus.DEGRADED -> HealthState.WARN
                            FileStatus.DAMAGED -> HealthState.ALERT
                        },
                    )
                    Spacer(Modifier.height(0.dp))
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(file.name, style = StrataType.DataLarge)
                        Text("${Formatting.bytes(file.sizeBytes)} · ${file.chunkCount} shards" + if (file.mirrored) " · MIRRORED" else "", style = StrataType.LabelDim)
                    }
                }
                Row {
                    androidx.compose.material3.TextButton(onClick = onOpenShardMap) { Text("MAP", style = StrataType.Label.copy(color = StrataColors.Cold)) }
                    androidx.compose.material3.TextButton(onClick = onDownload) { Text("GET", style = StrataType.Label.copy(color = StrataColors.Signal)) }
                }
            }
        }
    }
}
