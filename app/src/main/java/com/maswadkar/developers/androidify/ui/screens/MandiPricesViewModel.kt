package com.maswadkar.developers.androidify.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.data.MandiPreferences
import com.maswadkar.developers.androidify.data.MandiPrice
import com.maswadkar.developers.androidify.data.MandiPriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MandiPricesUiState(
    val isLoading: Boolean = false,
    val isInitializing: Boolean = true,
    val hasPreferences: Boolean = false,
    val isCompactMode: Boolean = false,
    val savedPreferences: MandiPreferences? = null,
    val states: List<String> = emptyList(),
    val districts: List<String> = emptyList(),
    val commodities: List<String> = emptyList(),
    val markets: List<String> = emptyList(),
    val selectedState: String? = null,
    val selectedDistrict: String? = null,
    val selectedCommodity: String? = null,
    val selectedMarket: String? = null,
    val prices: List<MandiPrice> = emptyList(),
    val latestDataDate: String? = null,
    val error: String? = null
)

class MandiPricesViewModel : ViewModel() {

    private val repository = MandiPriceRepository.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(MandiPricesUiState())
    val uiState: StateFlow<MandiPricesUiState> = _uiState.asStateFlow()

    private val userId: String?
        get() = auth.currentUser?.uid

    init {
        loadUserPreferences()
    }

    private fun loadUserPreferences() {
        val uid = userId ?: run {
            // No user logged in, show full mode
            _uiState.value = _uiState.value.copy(isInitializing = false)
            loadStates()
            return
        }

        viewModelScope.launch {
            try {
                val preferences = repository.getUserPreferences(uid)
                if (preferences != null && preferences.isValid()) {
                    // User has saved preferences - show compact mode
                    _uiState.value = _uiState.value.copy(
                        isInitializing = false,
                        hasPreferences = true,
                        isCompactMode = true,
                        savedPreferences = preferences,
                        selectedState = preferences.state,
                        selectedDistrict = preferences.district,
                        selectedMarket = preferences.market,
                        selectedCommodity = preferences.lastCommodity
                    )
                    // Load commodities for saved location
                    loadCommoditiesForPreferences(preferences)
                } else {
                    // No preferences - show full mode
                    _uiState.value = _uiState.value.copy(isInitializing = false)
                    loadStates()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInitializing = false,
                    error = e.message
                )
                loadStates()
            }
        }
    }

    private fun loadCommoditiesForPreferences(preferences: MandiPreferences) {
        viewModelScope.launch {
            try {
                val commodities = repository.getCommodities(preferences.state, preferences.district)
                _uiState.value = _uiState.value.copy(
                    commodities = commodities,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    private fun loadStates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val states = repository.getStates()
                _uiState.value = _uiState.value.copy(
                    states = states,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun onStateSelected(state: String?) {
        _uiState.value = _uiState.value.copy(
            selectedState = state,
            selectedDistrict = null,
            selectedCommodity = null,
            selectedMarket = null,
            districts = emptyList(),
            commodities = emptyList(),
            markets = emptyList(),
            prices = emptyList()
        )

        state?.let { loadDistricts(it) }
    }

    private fun loadDistricts(state: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val districts = repository.getDistricts(state)
                _uiState.value = _uiState.value.copy(
                    districts = districts,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun onDistrictSelected(district: String?) {
        _uiState.value = _uiState.value.copy(
            selectedDistrict = district,
            selectedCommodity = null,
            selectedMarket = null,
            commodities = emptyList(),
            markets = emptyList(),
            prices = emptyList()
        )

        val state = _uiState.value.selectedState
        if (state != null && district != null) {
            loadCommoditiesAndMarkets(state, district)
        }
    }

    private fun loadCommoditiesAndMarkets(state: String, district: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val commodities = repository.getCommodities(state, district)
                val markets = repository.getMarkets(state, district)
                _uiState.value = _uiState.value.copy(
                    commodities = commodities,
                    markets = markets,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun onCommoditySelected(commodity: String?) {
        _uiState.value = _uiState.value.copy(selectedCommodity = commodity)
    }

    fun onMarketSelected(market: String?) {
        _uiState.value = _uiState.value.copy(selectedMarket = market)
    }

    fun loadPrices() {
        val state = _uiState.value.selectedState ?: return
        val district = _uiState.value.selectedDistrict

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val prices = repository.getMandiPrices(
                    state = state,
                    district = district,
                    market = _uiState.value.selectedMarket,
                    commodity = _uiState.value.selectedCommodity
                )

                // Fetch the latest data date for display
                val latestDate = if (district != null) {
                    repository.getLatestDataDate(state, district)
                } else null

                _uiState.value = _uiState.value.copy(
                    prices = prices,
                    latestDataDate = latestDate,
                    isLoading = false
                )

                // Auto-save preferences after first successful search with state+district
                if (!_uiState.value.hasPreferences && district != null) {
                    savePreferencesInternal()
                }

                // Update last commodity if user has preferences
                if (_uiState.value.hasPreferences && _uiState.value.selectedCommodity != null) {
                    updateLastCommodity()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    private suspend fun savePreferencesInternal() {
        val uid = userId ?: return
        val state = _uiState.value.selectedState ?: return
        val district = _uiState.value.selectedDistrict ?: return

        val preferences = MandiPreferences(
            state = state,
            district = district,
            market = _uiState.value.selectedMarket,
            lastCommodity = _uiState.value.selectedCommodity
        )

        if (repository.saveUserPreferences(uid, preferences)) {
            _uiState.value = _uiState.value.copy(
                hasPreferences = true,
                isCompactMode = true,
                savedPreferences = preferences
            )
        }
    }

    private suspend fun updateLastCommodity() {
        val uid = userId ?: return
        val commodity = _uiState.value.selectedCommodity ?: return
        repository.updateLastCommodity(uid, commodity)
    }

    fun savePreferences() {
        viewModelScope.launch {
            savePreferencesInternal()
        }
    }

    fun switchToFullMode() {
        _uiState.value = _uiState.value.copy(isCompactMode = false)
        if (_uiState.value.states.isEmpty()) {
            loadStates()
        }
    }

    fun switchToCompactMode() {
        val prefs = _uiState.value.savedPreferences
        if (prefs != null && prefs.isValid()) {
            _uiState.value = _uiState.value.copy(
                isCompactMode = true,
                selectedState = prefs.state,
                selectedDistrict = prefs.district,
                selectedMarket = prefs.market,
                selectedCommodity = prefs.lastCommodity
            )
            loadCommoditiesForPreferences(prefs)
        }
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedCommodity = null,
            selectedMarket = null,
            prices = emptyList(),
            latestDataDate = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
