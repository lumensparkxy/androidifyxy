package com.maswadkar.developers.androidify.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maswadkar.developers.androidify.weather.WeatherForecast
import com.maswadkar.developers.androidify.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeatherUiState(
    val isLoading: Boolean = false,
    val forecast: WeatherForecast? = null,
    val error: String? = null,
    val needsPermission: Boolean = false
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = WeatherRepository.getInstance(application)

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    fun load(permissionGranted: Boolean, forceRefresh: Boolean = false) {
        if (!permissionGranted) {
            _uiState.value = WeatherUiState(needsPermission = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, needsPermission = false)
            try {
                val forecast = repo.get3DayForecast(forceRefresh = forceRefresh)
                _uiState.value = WeatherUiState(isLoading = false, forecast = forecast)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load weather",
                    needsPermission = false
                )
            }
        }
    }
}
