package com.maswadkar.developers.androidify.ui.screens

import android.util.Patterns
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.data.Crop
import com.maswadkar.developers.androidify.data.FarmerProfile
import com.maswadkar.developers.androidify.data.FarmerProfileRepository
import com.maswadkar.developers.androidify.data.IndianStatesAndDistricts
import com.maswadkar.developers.androidify.data.KnowledgeRepository
import com.maswadkar.developers.androidify.weather.WeatherCacheStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FarmerProfileUiState(
    // Loading states
    val isLoading: Boolean = true,
    val isLoadingStates: Boolean = false,
    val isLoadingDistricts: Boolean = false,
    val isLoadingCrops: Boolean = false,
    val isSaving: Boolean = false,

    // Profile data
    val hasProfile: Boolean = false,
    val profileCompletionPercent: Int = 0,

    // Location fields
    val states: List<String> = emptyList(),
    val districts: List<String> = emptyList(),
    val selectedState: String? = null,
    val selectedDistrict: String? = null,

    // GPS Location (from Weather - read-only)
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationLabel: String? = null,

    // Farm details
    val totalFarmAcres: String = "",
    val irrigationAvailable: Boolean? = null,

    // Contact info
    val mobileNumber: String = "",
    val emailId: String = "",

    // Crops
    val availableCrops: List<Crop> = emptyList(),
    val selectedCrops: List<String> = emptyList(),

    // Validation errors
    val farmAcresError: String? = null,
    val mobileNumberError: String? = null,
    val emailIdError: String? = null,

    // General state
    val error: String? = null,
    val saveSuccess: Boolean = false
)

class FarmerProfileViewModel(
    private val profileRepository: FarmerProfileRepository = FarmerProfileRepository.getInstance(),
    private val knowledgeRepository: KnowledgeRepository = KnowledgeRepository.getInstance(),
    private val appContext: Context? = null
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val weatherCacheStore: WeatherCacheStore? = appContext?.let { WeatherCacheStore(it) }

    private val _uiState = MutableStateFlow(FarmerProfileUiState())
    val uiState: StateFlow<FarmerProfileUiState> = _uiState.asStateFlow()

    private val userId: String?
        get() = auth.currentUser?.uid

    init {
        loadProfile()
        loadStates()
        loadAvailableCrops()
        loadCachedWeatherLocation()
    }

    private fun loadCachedWeatherLocation() {
        val cache = weatherCacheStore ?: return
        viewModelScope.launch {
            try {
                val cachedForecast = cache.read()
                if (cachedForecast != null) {
                    _uiState.update { state ->
                        state.copy(
                            lastLatitude = cachedForecast.lat,
                            lastLongitude = cachedForecast.lon,
                            lastLocationLabel = cachedForecast.locationLabel
                        )
                    }
                }
            } catch (_: Exception) {
                // Ignore errors reading weather cache - it's optional
            }
        }
    }

    private fun loadProfile() {
        val uid = userId ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            try {
                val profile = profileRepository.getUserProfile(uid)
                if (profile != null) {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            hasProfile = profile.hasValidLocation(),
                            profileCompletionPercent = profile.getCompletionPercentage(),
                            selectedState = profile.state.takeIf { it.isNotBlank() },
                            selectedDistrict = profile.district.takeIf { it.isNotBlank() },
                            totalFarmAcres = profile.totalFarmAcres?.toString() ?: "",
                            irrigationAvailable = profile.irrigationAvailable,
                            mobileNumber = profile.mobileNumber ?: "",
                            emailId = profile.emailId ?: "",
                            selectedCrops = profile.majorCrops
                        )
                    }
                    // Load districts for the saved state
                    profile.state.takeIf { it.isNotBlank() }?.let { loadDistricts(it) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadStates() {
        // Use static data - instant loading, no network call needed
        val states = IndianStatesAndDistricts.getStates()
        _uiState.update { it.copy(states = states, isLoadingStates = false) }
    }

    private fun loadDistricts(state: String) {
        // Use static data - instant loading, no network call needed
        val districts = IndianStatesAndDistricts.getDistricts(state)
        _uiState.update { it.copy(districts = districts, isLoadingDistricts = false) }
    }

    private fun loadAvailableCrops() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCrops = true) }
            try {
                val crops = knowledgeRepository.getCrops()
                _uiState.update { it.copy(availableCrops = crops, isLoadingCrops = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingCrops = false, error = e.message) }
            }
        }
    }

    // Location field handlers
    fun onStateSelected(state: String?) {
        _uiState.update {
            it.copy(
                selectedState = state,
                selectedDistrict = null,
                districts = emptyList()
            )
        }
        state?.let { loadDistricts(it) }
        updateCompletionPercent()
    }

    fun onDistrictSelected(district: String?) {
        _uiState.update { it.copy(selectedDistrict = district) }
        updateCompletionPercent()
    }

    // Farm details handlers
    fun onFarmAcresChanged(value: String) {
        // Allow only valid decimal input
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(totalFarmAcres = filtered, farmAcresError = null) }
        updateCompletionPercent()
    }

    fun onIrrigationChanged(available: Boolean) {
        _uiState.update { it.copy(irrigationAvailable = available) }
        updateCompletionPercent()
    }

    // Contact info handlers
    fun onMobileNumberChanged(value: String) {
        // Allow only digits, max 10
        val filtered = value.filter { it.isDigit() }.take(10)
        _uiState.update { it.copy(mobileNumber = filtered, mobileNumberError = null) }
        updateCompletionPercent()
    }

    fun onEmailIdChanged(value: String) {
        _uiState.update { it.copy(emailId = value.trim(), emailIdError = null) }
        updateCompletionPercent()
    }

    // Crops handlers
    fun onCropToggled(cropId: String) {
        _uiState.update { state ->
            val newSelection = if (cropId in state.selectedCrops) {
                state.selectedCrops - cropId
            } else {
                state.selectedCrops + cropId
            }
            state.copy(selectedCrops = newSelection)
        }
        updateCompletionPercent()
    }

    private fun updateCompletionPercent() {
        val state = _uiState.value
        var filledFields = 0
        val totalFields = 7

        if (!state.selectedState.isNullOrBlank()) filledFields++
        if (!state.selectedDistrict.isNullOrBlank()) filledFields++
        if (state.totalFarmAcres.isNotBlank() && state.totalFarmAcres.toDoubleOrNull()?.let { it > 0 } == true) filledFields++
        if (state.irrigationAvailable != null) filledFields++
        if (state.mobileNumber.isNotBlank()) filledFields++
        if (state.emailId.isNotBlank()) filledFields++
        if (state.selectedCrops.isNotEmpty()) filledFields++

        _uiState.update { it.copy(profileCompletionPercent = (filledFields * 100) / totalFields) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        val state = _uiState.value

        // Validate farm acres (optional, but if provided must be positive)
        if (state.totalFarmAcres.isNotBlank()) {
            val acres = state.totalFarmAcres.toDoubleOrNull()
            if (acres == null || acres <= 0) {
                _uiState.update { it.copy(farmAcresError = "Enter a valid farm size") }
                isValid = false
            }
        }

        // Validate mobile number (optional, but if provided must be 10 digits)
        if (state.mobileNumber.isNotBlank() && state.mobileNumber.length != 10) {
            _uiState.update { it.copy(mobileNumberError = "Mobile number must be 10 digits") }
            isValid = false
        }

        // Validate email (optional, but if provided must be valid format)
        if (state.emailId.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(state.emailId).matches()) {
            _uiState.update { it.copy(emailIdError = "Enter a valid email address") }
            isValid = false
        }

        return isValid
    }

    fun saveProfile() {
        val uid = userId ?: return
        val state = _uiState.value

        // Validate required fields
        if (state.selectedState.isNullOrBlank() || state.selectedDistrict.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Please select state and district") }
            return
        }

        // Validate optional fields
        if (!validateInputs()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val profile = FarmerProfile(
                    state = state.selectedState,
                    district = state.selectedDistrict,
                    lastLatitude = state.lastLatitude,
                    lastLongitude = state.lastLongitude,
                    lastLocationLabel = state.lastLocationLabel,
                    totalFarmAcres = state.totalFarmAcres.toDoubleOrNull(),
                    irrigationAvailable = state.irrigationAvailable,
                    mobileNumber = state.mobileNumber.takeIf { it.isNotBlank() },
                    emailId = state.emailId.takeIf { it.isNotBlank() },
                    majorCrops = state.selectedCrops
                )

                val success = profileRepository.saveUserProfile(uid, profile)
                if (success) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasProfile = true,
                            saveSuccess = true,
                            profileCompletionPercent = profile.getCompletionPercentage()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isSaving = false, error = "Failed to save profile")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}

/**
 * Factory to create FarmerProfileViewModel with Context for accessing WeatherCacheStore
 */
class FarmerProfileViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FarmerProfileViewModel::class.java)) {
            return FarmerProfileViewModel(
                appContext = context.applicationContext
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
