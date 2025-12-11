package com.maswadkar.developers.androidify.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.data.MandiPreferences
import com.maswadkar.developers.androidify.data.MandiPriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MandiPreferencesUiState(
    val isLoadingStates: Boolean = false,
    val isLoadingDistricts: Boolean = false,
    val isSaving: Boolean = false,
    val hasPreferences: Boolean = false,
    val savedPreferences: MandiPreferences? = null,
    val states: List<String> = emptyList(),
    val districts: List<String> = emptyList(),
    val selectedState: String? = null,
    val selectedDistrict: String? = null,
    val error: String? = null
)

class MandiPreferencesViewModel : ViewModel() {

    private val repository = MandiPriceRepository.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(MandiPreferencesUiState())
    val uiState: StateFlow<MandiPreferencesUiState> = _uiState.asStateFlow()

    private val userId: String?
        get() = auth.currentUser?.uid

    init {
        loadCurrentPreferences()
        loadStates()
    }

    private fun loadCurrentPreferences() {
        val uid = userId ?: return

        viewModelScope.launch {
            try {
                val preferences = repository.getUserPreferences(uid)
                if (preferences != null && preferences.isValid()) {
                    _uiState.value = _uiState.value.copy(
                        hasPreferences = true,
                        savedPreferences = preferences,
                        selectedState = preferences.state,
                        selectedDistrict = preferences.district
                    )
                    // Load districts for the saved state
                    loadDistricts(preferences.state)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun loadStates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingStates = true)
            try {
                val states = repository.getStates()
                _uiState.value = _uiState.value.copy(
                    states = states,
                    isLoadingStates = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoadingStates = false
                )
            }
        }
    }

    fun onStateSelected(state: String?) {
        _uiState.value = _uiState.value.copy(
            selectedState = state,
            selectedDistrict = null,
            districts = emptyList()
        )
        state?.let { loadDistricts(it) }
    }

    private fun loadDistricts(state: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDistricts = true)
            try {
                val districts = repository.getDistricts(state)
                _uiState.value = _uiState.value.copy(
                    districts = districts,
                    isLoadingDistricts = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoadingDistricts = false
                )
            }
        }
    }

    fun onDistrictSelected(district: String?) {
        _uiState.value = _uiState.value.copy(selectedDistrict = district)
    }

    fun savePreferences() {
        val uid = userId ?: return
        val state = _uiState.value.selectedState ?: return
        val district = _uiState.value.selectedDistrict ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val preferences = MandiPreferences(
                    state = state,
                    district = district
                )
                val success = repository.saveUserPreferences(uid, preferences)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        hasPreferences = true,
                        savedPreferences = preferences
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Failed to save preferences"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message
                )
            }
        }
    }
}

