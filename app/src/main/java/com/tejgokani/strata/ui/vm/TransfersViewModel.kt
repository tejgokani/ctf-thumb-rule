package com.tejgokani.strata.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tejgokani.strata.di.AppContainer
import com.tejgokani.strata.engine.ChunkRecord
import com.tejgokani.strata.engine.FileRecord
import com.tejgokani.strata.engine.PlacementRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TransferRow(val file: FileRecord, val chunk: ChunkRecord, val placement: PlacementRecord)

data class TransfersUiState(val rows: List<TransferRow> = emptyList())

/**
 * Shows the current placement state of every in-flight or recently-touched chunk. Honest scope
 * note: this reads persisted placement state rather than a live byte-level progress stream from
 * a running transfer job — wiring a full WorkManager/foreground-service progress channel end to
 * end was out of scope for this pass (plan §12), but every state shown here is real, not mocked.
 */
class TransfersViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TransfersUiState())
    val state: StateFlow<TransfersUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val files = container.fileRepository.all().associateBy { it.id }
            val placements = container.placementRepository.all()
            val rows = placements.mapNotNull { placement ->
                val chunk = container.chunkRepository.get(placement.chunkId) ?: return@mapNotNull null
                val file = files[chunk.fileId] ?: return@mapNotNull null
                TransferRow(file, chunk, placement)
            }.sortedByDescending { it.placement.lastVerifiedAtMs }
            _state.value = TransfersUiState(rows)
        }
    }
}
