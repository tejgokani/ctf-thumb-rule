package com.tejgokani.strata.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tejgokani.strata.di.AppContainer
import com.tejgokani.strata.ledger.ChainVerifyResult
import com.tejgokani.strata.ledger.LedgerEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LedgerUiState(
    val events: List<LedgerEvent> = emptyList(),
    val verifyResult: ChainVerifyResult? = null,
    val verifying: Boolean = false,
)

class LedgerViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LedgerUiState())
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _state.value = _state.value.copy(events = container.ledger.all().reversed()) }
    }

    fun verifyChain() {
        viewModelScope.launch {
            _state.value = _state.value.copy(verifying = true)
            val result = container.ledger.verifyChain()
            _state.value = _state.value.copy(verifying = false, verifyResult = result)
        }
    }

    fun exportAsCsv(): String {
        val header = "seq,deviceId,lamport,wallClockMs,type\n"
        val rows = container.ledger.all().joinToString("\n") { e ->
            "${e.seq},${e.deviceId},${e.lamport},${e.wallClockMs},${e.type.name}"
        }
        return header + rows
    }
}
