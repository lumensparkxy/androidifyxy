package com.maswadkar.developers.androidify.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maswadkar.developers.androidify.data.FilterOption
import com.maswadkar.developers.androidify.data.Offer
import com.maswadkar.developers.androidify.data.OfferFilters
import com.maswadkar.developers.androidify.data.OfferRepository
import com.maswadkar.developers.androidify.data.Supplier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OffersUiState(
    val isLoading: Boolean = false,
    val offers: List<Offer> = emptyList(),
    val districts: List<FilterOption> = OfferFilters.districts,
    val categories: List<FilterOption> = OfferFilters.categories,
    val selectedDistrictId: String? = null,
    val selectedCategory: String? = null,
    val sortField: OfferRepository.SortField = OfferRepository.SortField.CREATED_AT,
    val sortDirection: OfferRepository.SortDirection = OfferRepository.SortDirection.DESCENDING,
    val selectedOffer: Offer? = null,
    val selectedOfferSupplier: Supplier? = null,
    val isLoadingSupplier: Boolean = false,
    val error: String? = null
)

class OffersViewModel : ViewModel() {

    private val repository = OfferRepository.getInstance()

    private val _uiState = MutableStateFlow(OffersUiState())
    val uiState: StateFlow<OffersUiState> = _uiState.asStateFlow()

    init {
        loadOffers()
    }

    fun onDistrictSelected(districtId: String?) {
        _uiState.value = _uiState.value.copy(selectedDistrictId = districtId)
        loadOffers()
    }

    fun onCategorySelected(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        loadOffers()
    }

    fun onSortByPrice() {
        val currentState = _uiState.value
        val newDirection = if (currentState.sortField == OfferRepository.SortField.PRICE_NORMALIZED) {
            // Toggle direction if already sorting by price
            if (currentState.sortDirection == OfferRepository.SortDirection.ASCENDING) {
                OfferRepository.SortDirection.DESCENDING
            } else {
                OfferRepository.SortDirection.ASCENDING
            }
        } else {
            // Default to ascending for price
            OfferRepository.SortDirection.ASCENDING
        }

        _uiState.value = currentState.copy(
            sortField = OfferRepository.SortField.PRICE_NORMALIZED,
            sortDirection = newDirection
        )
        loadOffers(
            sortField = OfferRepository.SortField.PRICE_NORMALIZED,
            sortDirection = newDirection
        )
    }

    fun onSortByDate() {
        val currentState = _uiState.value
        val newDirection = if (currentState.sortField == OfferRepository.SortField.CREATED_AT) {
            // Toggle direction if already sorting by date
            if (currentState.sortDirection == OfferRepository.SortDirection.DESCENDING) {
                OfferRepository.SortDirection.ASCENDING
            } else {
                OfferRepository.SortDirection.DESCENDING
            }
        } else {
            // Default to descending for date (newest first)
            OfferRepository.SortDirection.DESCENDING
        }

        _uiState.value = currentState.copy(
            sortField = OfferRepository.SortField.CREATED_AT,
            sortDirection = newDirection
        )
        loadOffers(
            sortField = OfferRepository.SortField.CREATED_AT,
            sortDirection = newDirection
        )
    }

    fun onOfferSelected(offer: Offer) {
        _uiState.value = _uiState.value.copy(
            selectedOffer = offer,
            selectedOfferSupplier = null,
            isLoadingSupplier = true
        )
        loadSupplierForOffer(offer.supplierId)
    }

    fun onDismissOfferDetail() {
        _uiState.value = _uiState.value.copy(
            selectedOffer = null,
            selectedOfferSupplier = null
        )
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedDistrictId = null,
            selectedCategory = null,
            sortField = OfferRepository.SortField.CREATED_AT,
            sortDirection = OfferRepository.SortDirection.DESCENDING
        )
        loadOffers()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun loadOffers(
        sortField: OfferRepository.SortField? = null,
        sortDirection: OfferRepository.SortDirection? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val currentState = _uiState.value
                val offers = repository.getOffers(
                    districtId = currentState.selectedDistrictId,
                    category = currentState.selectedCategory,
                    sortField = sortField ?: currentState.sortField,
                    sortDirection = sortDirection ?: currentState.sortDirection
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    offers = offers
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load offers"
                )
            }
        }
    }

    private fun loadSupplierForOffer(supplierId: String) {
        viewModelScope.launch {
            try {
                val supplier = repository.getSupplier(supplierId)
                _uiState.value = _uiState.value.copy(
                    selectedOfferSupplier = supplier,
                    isLoadingSupplier = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingSupplier = false,
                    error = e.message ?: "Failed to load supplier details"
                )
            }
        }
    }
}

