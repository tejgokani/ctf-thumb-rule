package com.tejgokani.strata.ui.vm

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tejgokani.strata.core.StrataResult
import com.tejgokani.strata.data.AndroidByteSource
import com.tejgokani.strata.di.AppContainer
import com.tejgokani.strata.engine.FileRecord
import com.tejgokani.strata.engine.TransferCoordinator
import com.tejgokani.strata.ui.ActivityResultBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilesUiState(
    val files: List<FileRecord> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val lastAction: String? = null,
)

class FilesViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _state.value = _state.value.copy(files = container.fileRepository.all()) }
    }

    fun uploadFile(bridge: ActivityResultBridge, resolver: ContentResolver, mirror: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            val uri = bridge.pickDocumentToUpload()
            if (uri == null) {
                _state.value = _state.value.copy(busy = false)
                return@launch
            }
            val source = AndroidByteSource(resolver, uri)
            val request = TransferCoordinator.UploadRequest(source.displayName(), source.mimeType(), source, mirror)
            when (val result = container.transferCoordinator().uploadFile(request)) {
                is StrataResult.Ok -> _state.value = _state.value.copy(busy = false, lastAction = "Uploaded ${result.value.name}", files = container.fileRepository.all())
                is StrataResult.Err -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }

    fun downloadFile(bridge: ActivityResultBridge, resolver: ContentResolver, file: FileRecord) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            val destUri = bridge.pickDestinationForDownload(file.name, file.mimeType)
            if (destUri == null) {
                _state.value = _state.value.copy(busy = false)
                return@launch
            }
            val result = resolver.openOutputStream(destUri)?.use { out ->
                container.transferCoordinator().downloadFile(file.id, out)
            } ?: StrataResult.Err(com.tejgokani.strata.core.StrataError.Fatal("could not open destination for writing"))

            when (result) {
                is StrataResult.Ok -> _state.value = _state.value.copy(busy = false, lastAction = "Downloaded ${file.name}")
                is StrataResult.Err -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }
}
