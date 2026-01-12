package com.maswadkar.developers.androidify.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maswadkar.developers.androidify.data.Crop
import com.maswadkar.developers.androidify.data.KnowledgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KnowledgeBaseUiState(
    val crops: List<Crop> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class KnowledgeBaseViewModel(
    private val repository: KnowledgeRepository = KnowledgeRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(KnowledgeBaseUiState())
    val uiState: StateFlow<KnowledgeBaseUiState> = _uiState.asStateFlow()

    init {
        loadCrops()
    }

    private fun loadCrops() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val crops = repository.getCrops()
                _uiState.value = _uiState.value.copy(
                    crops = crops,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load crops"
                )
            }
        }
    }

    fun refresh() {
        loadCrops()
    }
}

