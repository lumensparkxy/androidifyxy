package com.maswadkar.developers.androidify

import com.maswadkar.developers.androidify.data.LeadProfileDraft
import com.maswadkar.developers.androidify.data.ProductRecommendation

data class PendingLeadRequest(
    val recommendation: ProductRecommendation,
    val chatMessageText: String,
    val conversationId: String
)

data class LeadRequestUiState(
    val isSubmitting: Boolean = false,
    val showProfileDialog: Boolean = false,
    val pendingRequest: PendingLeadRequest? = null,
    val profileDraft: LeadProfileDraft = LeadProfileDraft(),
    val nameError: String? = null,
    val mobileNumberError: String? = null,
    val villageError: String? = null,
    val tehsilError: String? = null,
    val districtError: String? = null,
    val totalFarmAcresError: String? = null,
    val confirmationRequestNumber: String? = null,
    val errorMessage: String? = null
)

