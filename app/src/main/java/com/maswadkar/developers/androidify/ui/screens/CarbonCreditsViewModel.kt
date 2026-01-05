package com.maswadkar.developers.androidify.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.data.CarbonCreditSubmission
import com.maswadkar.developers.androidify.data.CarbonCreditsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CarbonCreditsUiState(
    val showForm: Boolean = false,
    val landSizeAcres: String = "",
    val bigTreeCount: String = "",
    val interestedInGreenFarming: Boolean = false,
    val phoneNumber: String = "",
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val error: String? = null,
    // Validation errors
    val landSizeError: String? = null,
    val treeCountError: String? = null,
    val phoneNumberError: String? = null
)

class CarbonCreditsViewModel(
    private val repository: CarbonCreditsRepository = CarbonCreditsRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CarbonCreditsUiState())
    val uiState: StateFlow<CarbonCreditsUiState> = _uiState.asStateFlow()

    fun onCalculateClick() {
        _uiState.update { it.copy(showForm = true) }
    }

    fun onLandSizeChange(value: String) {
        _uiState.update {
            it.copy(
                landSizeAcres = value,
                landSizeError = null
            )
        }
    }

    fun onTreeCountChange(value: String) {
        _uiState.update {
            it.copy(
                bigTreeCount = value,
                treeCountError = null
            )
        }
    }

    fun onGreenFarmingChange(value: Boolean) {
        _uiState.update { it.copy(interestedInGreenFarming = value) }
    }

    fun onPhoneNumberChange(value: String) {
        _uiState.update {
            it.copy(
                phoneNumber = value,
                phoneNumberError = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onSubmit() {
        val currentState = _uiState.value

        // Validate inputs
        var hasErrors = false
        var landSizeError: String? = null
        var treeCountError: String? = null
        var phoneNumberError: String? = null

        if (currentState.landSizeAcres.isBlank()) {
            landSizeError = "This field is required"
            hasErrors = true
        } else if (currentState.landSizeAcres.toDoubleOrNull() == null) {
            landSizeError = "Please enter a valid number"
            hasErrors = true
        }

        if (currentState.bigTreeCount.isBlank()) {
            treeCountError = "This field is required"
            hasErrors = true
        } else if (currentState.bigTreeCount.toIntOrNull() == null) {
            treeCountError = "Please enter a valid number"
            hasErrors = true
        }

        if (currentState.phoneNumber.isBlank()) {
            phoneNumberError = "This field is required"
            hasErrors = true
        } else if (currentState.phoneNumber.length < 10) {
            phoneNumberError = "Please enter a valid phone number"
            hasErrors = true
        }

        if (hasErrors) {
            _uiState.update {
                it.copy(
                    landSizeError = landSizeError,
                    treeCountError = treeCountError,
                    phoneNumberError = phoneNumberError
                )
            }
            return
        }

        // Submit form
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val submission = CarbonCreditSubmission(
                userId = userId,
                landSizeAcres = currentState.landSizeAcres.toDoubleOrNull() ?: 0.0,
                bigTreeCount = currentState.bigTreeCount.toIntOrNull() ?: 0,
                interestedInGreenFarming = currentState.interestedInGreenFarming,
                phoneNumber = currentState.phoneNumber
            )

            val result = repository.submitCarbonCreditForm(submission)

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submitSuccess = true
                        )
                    }
                },
                onFailure = { exception ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = exception.message ?: "Failed to submit form"
                        )
                    }
                }
            )
        }
    }

    fun resetForm() {
        _uiState.update {
            CarbonCreditsUiState()
        }
    }
}

