package com.maswadkar.developers.androidify.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maswadkar.developers.androidify.data.KnowledgeDocument
import com.maswadkar.developers.androidify.data.KnowledgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KnowledgeDocumentsUiState(
    val cropId: String = "",
    val cropName: String = "",
    val documents: List<KnowledgeDocument> = emptyList(),
    val isLoading: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadingDocumentId: String? = null,
    val error: String? = null,
    val downloadUrl: String? = null
)

class KnowledgeDocumentsViewModel(
    private val cropId: String,
    private val repository: KnowledgeRepository = KnowledgeRepository.getInstance()
) : ViewModel() {


    private val _uiState = MutableStateFlow(KnowledgeDocumentsUiState(cropId = cropId))
    val uiState: StateFlow<KnowledgeDocumentsUiState> = _uiState.asStateFlow()

    init {
        loadDocuments()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val documents = repository.getDocuments(cropId)
                _uiState.value = _uiState.value.copy(
                    documents = documents,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load documents"
                )
            }
        }
    }

    fun onDocumentClick(document: KnowledgeDocument) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadingDocumentId = document.id,
                downloadUrl = null
            )

            try {
                val url = repository.getDocumentDownloadUrl(document.storagePath)
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadingDocumentId = null,
                    downloadUrl = url
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadingDocumentId = null,
                    error = "Failed to get document: ${e.message}"
                )
            }
        }
    }

    fun clearDownloadUrl() {
        _uiState.value = _uiState.value.copy(downloadUrl = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refresh() {
        loadDocuments()
    }

    class Factory(private val cropId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(KnowledgeDocumentsViewModel::class.java)) {
                return KnowledgeDocumentsViewModel(cropId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

