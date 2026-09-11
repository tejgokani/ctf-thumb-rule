package com.tejgokani.strata.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tejgokani.strata.di.AppContainer
import com.tejgokani.strata.engine.AccountRecord
import com.tejgokani.strata.engine.FileRecord
import com.tejgokani.strata.engine.PlacementState
import com.tejgokani.strata.ui.components.ShardCell
import com.tejgokani.strata.ui.components.ShardCellState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShardMapUiState(
    val file: FileRecord? = null,
    val accounts: List<AccountRecord> = emptyList(),
    val cells: List<ShardCell> = emptyList(),
)

class ShardMapViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ShardMapUiState())
    val state: StateFlow<ShardMapUiState> = _state.asStateFlow()

    fun load(fileId: String) {
        viewModelScope.launch {
            val file = container.fileRepository.get(fileId) ?: return@launch
            val accounts = container.accountRepository.all()
            val accountIndex = accounts.withIndex().associate { (i, a) -> a.id to i }
            val chunks = container.chunkRepository.forFile(fileId)
            val cells = chunks.flatMap { chunk ->
                container.placementRepository.forChunk(chunk.id).mapNotNull { placement ->
                    val acctIdx = accountIndex[placement.accountId] ?: return@mapNotNull null
                    val cellState = when {
                        placement.state == PlacementState.VERIFIED && placement.isMirror -> ShardCellState.MIRROR
                        placement.state == PlacementState.VERIFIED -> ShardCellState.VERIFIED
                        placement.state == PlacementState.LOST -> ShardCellState.DAMAGED
                        else -> ShardCellState.PENDING
                    }
                    ShardCell(chunk.index, acctIdx, cellState)
                }
            }
            _state.value = ShardMapUiState(file, accounts, cells)
        }
    }
}
