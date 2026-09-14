package com.tejgokani.strata.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.tejgokani.strata.di.AppContainer

/** Manual DI for ViewModels (plan §3 — no Hilt). Every screen's VM takes only [AppContainer]. */
class StrataViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when (modelClass) {
            BootViewModel::class.java -> BootViewModel(container) as T
            VaultViewModel::class.java -> VaultViewModel(container) as T
            NodesViewModel::class.java -> NodesViewModel(container) as T
            FilesViewModel::class.java -> FilesViewModel(container) as T
            ShardMapViewModel::class.java -> ShardMapViewModel(container) as T
            TransfersViewModel::class.java -> TransfersViewModel(container) as T
            LedgerViewModel::class.java -> LedgerViewModel(container) as T
            SettingsViewModel::class.java -> SettingsViewModel(container) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}
