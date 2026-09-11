package com.tejgokani.strata.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tejgokani.strata.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DisasterDrillResult(
    val recoveredFileCount: Int,
    val adoptedFileCount: Int,
    val recoveredPlacementCount: Int,
    val ranAtMs: Long,
)

data class SettingsUiState(
    val running: Boolean = false,
    val lastDrill: DisasterDrillResult? = null,
    val error: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /**
     * Runs RecoveryEngine against the LIVE accounts and reports what it found, without touching
     * the current local database — a read-only rehearsal of the real disaster-recovery path
     * (plan §10: "an untested recovery path is a broken recovery path").
     */
    fun runDisasterDrill() {
        viewModelScope.launch {
            _state.value = _state.value.copy(running = true, error = null)
            val accountIds = container.accountRepository.all().map { it.id }
            if (accountIds.isEmpty()) {
                _state.value = _state.value.copy(running = false, error = "Add at least one node before running the drill")
                return@launch
            }
            when (val result = container.recoveryEngine().recover(accountIds)) {
                is com.tejgokani.strata.core.StrataResult.Ok -> {
                    _state.value = _state.value.copy(
                        running = false,
                        lastDrill = DisasterDrillResult(
                            recoveredFileCount = result.value.files.size,
                            adoptedFileCount = result.value.adoptedFileIds.size,
                            recoveredPlacementCount = result.value.placements.size,
                            ranAtMs = container.clock.wallClockMs(),
                        ),
                    )
                }
                is com.tejgokani.strata.core.StrataResult.Err -> {
                    _state.value = _state.value.copy(running = false, error = result.error.message)
                }
            }
        }
    }

    fun lockVault() {
        container.lock()
    }
}
