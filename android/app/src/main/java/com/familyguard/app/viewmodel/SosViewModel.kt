package com.familyguard.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.local.TokenStorage
import com.familyguard.app.data.model.SosTriggerRequest
import com.familyguard.app.services.LocationForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import android.location.LocationManager
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class SosUiState(
    val isConnected: Boolean = false,
    val isSending: Boolean = false,
    val pressCount: Int = 0,
    val lastSentTime: String? = null,
    val error: String? = null
)

@HiltViewModel
class SosViewModel @Inject constructor(
    private val apiService: ApiService,
    private val tokenStorage: TokenStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SosUiState())
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    // Press detection: count presses within 3 second window
    private var pressResetJob: Job? = null
    private var pendingPressCount = 0

    init {
        // Start location service when ViewModel is created
        LocationForegroundService.startService(context)
        // Check connectivity
        viewModelScope.launch {
            checkConnection()
        }
    }

    fun onSosPressed() {
        if (_uiState.value.isSending) return

        pendingPressCount++
        _uiState.value = _uiState.value.copy(pressCount = pendingPressCount)

        // Reset window: send after 3 seconds of no presses
        pressResetJob?.cancel()
        pressResetJob = viewModelScope.launch {
            delay(3_000)
            triggerSos(pendingPressCount)
            pendingPressCount = 0
            _uiState.value = _uiState.value.copy(pressCount = 0)
        }
    }

    private suspend fun triggerSos(pressCount: Int) {
        _uiState.value = _uiState.value.copy(isSending = true, error = null)

        try {
            val location = getCurrentLocation()
            val request = SosTriggerRequest(
                pressCount = pressCount,
                latitude = location?.latitude,
                longitude = location?.longitude,
                message = ""
            )
            apiService.triggerSos(request)

            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            _uiState.value = _uiState.value.copy(
                isSending = false,
                lastSentTime = timeStr
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSending = false,
                error = "Ошибка отправки. Проверьте соединение."
            )
        }
    }

    private fun getCurrentLocation(): android.location.Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        }
    }

    private suspend fun checkConnection() {
        try {
            // Simple ping to API
            apiService.getProfile()
            _uiState.value = _uiState.value.copy(isConnected = true)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isConnected = false)
        }
    }

    override fun onCleared() {
        pressResetJob?.cancel()
        super.onCleared()
    }
}
