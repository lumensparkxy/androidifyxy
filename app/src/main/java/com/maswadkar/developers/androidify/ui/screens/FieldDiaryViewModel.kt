package com.maswadkar.developers.androidify.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maswadkar.developers.androidify.data.DiaryActivityType
import com.maswadkar.developers.androidify.data.FieldDiaryEntry
import com.maswadkar.developers.androidify.data.FieldDiaryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class FieldDiaryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedFilter: DiaryActivityType? = null,
    val entries: List<FieldDiaryEntry> = emptyList(),
    val groupedEntries: List<FieldDiaryTimelineGroup> = emptyList()
)

class FieldDiaryViewModel(
    private val repository: FieldDiaryRepository = FieldDiaryRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FieldDiaryUiState())
    val uiState: StateFlow<FieldDiaryUiState> = _uiState.asStateFlow()

    private var entriesJob: Job? = null

    init {
        loadEntries()
    }

    fun loadEntries() {
        entriesJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        entriesJob = viewModelScope.launch {
            repository.getEntriesFlow()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to load Field Diary entries"
                    )
                }
                .collect { entries ->
                    _uiState.value = _uiState.value
                        .copy(
                            isLoading = false,
                            errorMessage = null,
                            entries = entries
                        )
                        .withGroupedEntries()
                }
        }
    }

    fun onFilterSelected(filter: DiaryActivityType?) {
        _uiState.value = _uiState.value
            .copy(selectedFilter = filter)
            .withGroupedEntries()
    }

    private fun FieldDiaryUiState.withGroupedEntries(): FieldDiaryUiState = copy(
        groupedEntries = buildFieldDiaryTimelineGroups(
            entries = entries,
            selectedFilter = selectedFilter
        )
    )
}
