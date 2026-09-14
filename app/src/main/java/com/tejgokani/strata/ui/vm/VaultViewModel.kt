package com.tejgokani.strata.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tejgokani.strata.di.AppContainer
import com.tejgokani.strata.engine.AccountRecord
import com.tejgokani.strata.ledger.LedgerEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VaultUiState(
    val accounts: List<AccountRecord> = emptyList(),
    val fileCount: Int = 0,
    val recentEvents: List<LedgerEvent> = emptyList(),
) {
    val totalLimitBytes: Long get() = accounts.sumOf { it.quotaLimitBytes }
    val totalUsageBytes: Long get() = accounts.sumOf { it.quotaUsageBytes }
    val totalReclaimableBytes: Long get() = accounts.sumOf { it.reclaimableBytes }
    val totalFreeBytes: Long get() = (totalLimitBytes - totalUsageBytes).coerceAtLeast(0)
    val usageFraction: Float get() = if (totalLimitBytes == 0L) 0f else (totalUsageBytes.toFloat() / totalLimitBytes.toFloat())
}

class VaultViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = VaultUiState(
                accounts = container.accountRepository.all(),
                fileCount = container.fileRepository.all().size,
                recentEvents = container.ledger.tail(20).reversed(),
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                accounts = container.accountRepository.all(),
                fileCount = container.fileRepository.all().size,
                recentEvents = container.ledger.tail(20).reversed(),
            )
        }
    }
}
