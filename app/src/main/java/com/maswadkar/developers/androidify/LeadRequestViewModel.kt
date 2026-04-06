package com.maswadkar.developers.androidify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctionsException
import com.maswadkar.developers.androidify.data.FarmerProfile
import com.maswadkar.developers.androidify.data.FarmerProfileRepository
import com.maswadkar.developers.androidify.data.LeadProfileDraft
import com.maswadkar.developers.androidify.data.ProductRecommendation
import com.maswadkar.developers.androidify.data.SalesLeadRepository
import com.maswadkar.developers.androidify.data.SalesLeadRequest
import com.maswadkar.developers.androidify.data.sanitizeLeadMobileInput
import com.maswadkar.developers.androidify.data.withLeadContactFallbacks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LeadRequestViewModel(
    private val farmerProfileRepository: FarmerProfileRepository = FarmerProfileRepository.getInstance(),
    private val salesLeadRepository: SalesLeadRepository = SalesLeadRepository.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    companion object {
        private const val LEAD_REQUEST_SOURCE = "chat_recommendation"
    }

    private val _uiState = MutableStateFlow(LeadRequestUiState())
    val uiState: StateFlow<LeadRequestUiState> = _uiState.asStateFlow()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    fun onRecommendationLeadClick(
        recommendation: ProductRecommendation,
        chatMessageText: String,
        conversationId: String
    ) {
        val uid = currentUserId ?: run {
            _uiState.update { it.copy(errorMessage = "Please sign in to continue") }
            return
        }

        val pendingRequest = PendingLeadRequest(
            recommendation = recommendation,
            chatMessageText = chatMessageText,
            conversationId = conversationId
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    errorMessage = null,
                    confirmationRequestNumber = null,
                    pendingRequest = pendingRequest
                )
            }

            try {
                val existingProfile = farmerProfileRepository.getUserProfile(uid)
                val profile = (existingProfile ?: FarmerProfile()).copy(
                    name = existingProfile?.name ?: auth.currentUser?.displayName
                ).withLeadContactFallbacks(
                    auth.currentUser?.phoneNumber,
                    auth.currentUser?.email,
                )

                if (!profile.hasLeadRequiredFields()) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            showProfileDialog = true,
                            profileDraft = LeadProfileDraft(
                                name = profile.name.orEmpty(),
                                mobileNumber = profile.mobileNumber.orEmpty(),
                                village = profile.village.orEmpty(),
                                tehsil = profile.tehsil.orEmpty(),
                                district = profile.district,
                                totalFarmAcres = profile.totalFarmAcres?.let { acres ->
                                    if (acres % 1.0 == 0.0) acres.toInt().toString() else acres.toString()
                                }.orEmpty()
                            )
                        )
                    }
                    return@launch
                }

                submitLeadInternal(pendingRequest)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = e.message ?: "Unable to load your profile right now"
                    )
                }
            }
        }
    }

    fun onLeadNameChanged(value: String) {
        _uiState.update { it.copy(profileDraft = it.profileDraft.copy(name = value), nameError = null) }
    }

    fun onLeadMobileNumberChanged(value: String) {
        _uiState.update {
            it.copy(
                profileDraft = it.profileDraft.copy(mobileNumber = sanitizeLeadMobileInput(value)),
                mobileNumberError = null
            )
        }
    }

    fun onLeadVillageChanged(value: String) {
        _uiState.update { it.copy(profileDraft = it.profileDraft.copy(village = value), villageError = null) }
    }

    fun onLeadTehsilChanged(value: String) {
        _uiState.update { it.copy(profileDraft = it.profileDraft.copy(tehsil = value), tehsilError = null) }
    }

    fun onLeadDistrictChanged(value: String) {
        _uiState.update { it.copy(profileDraft = it.profileDraft.copy(district = value), districtError = null) }
    }

    fun onLeadTotalFarmAcresChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update {
            it.copy(
                profileDraft = it.profileDraft.copy(totalFarmAcres = filtered),
                totalFarmAcresError = null
            )
        }
    }

    fun dismissLeadProfileDialog() {
        _uiState.update {
            it.copy(
                isSubmitting = false,
                showProfileDialog = false,
                pendingRequest = null,
                nameError = null,
                mobileNumberError = null,
                villageError = null,
                tehsilError = null,
                districtError = null,
                totalFarmAcresError = null
            )
        }
    }

    fun dismissLeadConfirmation() {
        _uiState.update { it.copy(confirmationRequestNumber = null) }
    }

    fun clearLeadError() {
        _uiState.update { it.copy(errorMessage = null, isSubmitting = false) }
    }

    fun submitLeadAfterProfileUpdate() {
        val uid = currentUserId ?: run {
            _uiState.update { it.copy(errorMessage = "Please sign in to continue") }
            return
        }
        val pendingRequest = _uiState.value.pendingRequest ?: run {
            _uiState.update { it.copy(errorMessage = "No active request found") }
            return
        }

        val draft = _uiState.value.profileDraft.normalized()
        val nameError = if (draft.name.isBlank()) "Name is required" else null
        val mobileNumberError = when {
            draft.mobileNumber.isBlank() -> "Mobile number is required"
            draft.mobileNumber.length != 10 -> "Enter a valid 10-digit mobile number"
            else -> null
        }
        val villageError = if (draft.village.isBlank()) "Village is required" else null
        val tehsilError = if (draft.tehsil.isBlank()) "Tehsil is required" else null
        val districtError = if (draft.district.isBlank()) "District is required" else null
        val totalFarmAcresError = when {
            draft.totalFarmAcres.isBlank() -> "Land size is required"
            draft.totalFarmAcres.toDoubleOrNull()?.let { it > 0 } != true -> "Enter a valid land size"
            else -> null
        }

        if (listOf(nameError, mobileNumberError, villageError, tehsilError, districtError, totalFarmAcresError).any { it != null }) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    mobileNumberError = mobileNumberError,
                    villageError = villageError,
                    tehsilError = tehsilError,
                    districtError = districtError,
                    totalFarmAcresError = totalFarmAcresError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                val existingProfile = (farmerProfileRepository.getUserProfile(uid) ?: FarmerProfile())
                    .withLeadContactFallbacks(auth.currentUser?.phoneNumber, auth.currentUser?.email)
                val mergedProfile = existingProfile.copy(
                    name = draft.name,
                    mobileNumber = draft.mobileNumber,
                    village = draft.village,
                    tehsil = draft.tehsil,
                    district = draft.district,
                    totalFarmAcres = draft.totalFarmAcres.toDoubleOrNull()
                )

                val saveSuccess = farmerProfileRepository.saveUserProfile(uid, mergedProfile)
                if (!saveSuccess) {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Failed to save your details") }
                    return@launch
                }

                submitLeadInternal(pendingRequest)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message ?: "Failed to save your details") }
            }
        }
    }

    private fun mapLeadSubmissionError(e: FirebaseFunctionsException): String = when (e.code) {
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "Please complete your profile to continue"
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "We could not register this request. Please try again."
        FirebaseFunctionsException.Code.NOT_FOUND,
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> "We could not register your request right now. Please try again in a moment."
        else -> "Unable to register your request right now"
    }

    private fun mapLeadSubmissionErrorMessage(message: String?): String {
        val normalized = message?.trim().orEmpty()
        return when {
            normalized.contains("NOT_FOUND", ignoreCase = true) -> "We could not register your request right now. Please try again in a moment."
            normalized.contains("PERMISSION_DENIED", ignoreCase = true) -> "We could not register your request right now. Please try again in a moment."
            normalized.equals("Please complete your profile to continue", ignoreCase = true) -> "Please complete your profile to continue"
            normalized.isBlank() -> "Unable to register your request right now"
            else -> normalized
        }
    }

    private suspend fun submitLeadInternal(pendingRequest: PendingLeadRequest) {
        try {
            val result = salesLeadRepository.submitLead(
                SalesLeadRequest(
                    conversationId = pendingRequest.conversationId,
                    productName = pendingRequest.recommendation.productName,
                    quantity = pendingRequest.recommendation.quantity,
                    unit = pendingRequest.recommendation.unit,
                    chatMessageText = pendingRequest.chatMessageText,
                    source = LEAD_REQUEST_SOURCE
                )
            )

            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    showProfileDialog = false,
                    pendingRequest = null,
                    confirmationRequestNumber = result.requestNumber,
                    errorMessage = null,
                    nameError = null,
                    mobileNumberError = null,
                    villageError = null,
                    tehsilError = null,
                    districtError = null,
                    totalFarmAcresError = null
                )
            }
        } catch (e: FirebaseFunctionsException) {
            val message = mapLeadSubmissionError(e)
            _uiState.update { it.copy(isSubmitting = false, errorMessage = message) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isSubmitting = false, errorMessage = mapLeadSubmissionErrorMessage(e.message)) }
        }
    }
}
