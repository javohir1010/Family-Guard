package com.familyguard.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.local.TokenStorage
import com.familyguard.app.data.model.SosTriggerRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
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

    // Press detection: presses within a short window add up to one signal
    private var pressResetJob: Job? = null
    private var pendingPressCount = 0

    init {
        // Location service is started by SosScreen once permission is granted.
        // Re-check connectivity periodically so the status badge doesn't go stale.
        viewModelScope.launch {
            while (isActive) {
                checkConnection()
                delay(60_000)
            }
        }
    }

    fun onSosPressed() {
        if (_uiState.value.isSending) return

        pendingPressCount++
        _uiState.value = _uiState.value.copy(pressCount = pendingPressCount)

        // Emergency: no point waiting for more presses, send right away.
        // Otherwise send after a short pause without presses.
        pressResetJob?.cancel()
        val wait = if (pendingPressCount >= EMERGENCY_PRESSES) 0L else PRESS_WINDOW_MS
        pressResetJob = viewModelScope.launch {
            delay(wait)
            val count = pendingPressCount
            pendingPressCount = 0
            _uiState.value = _uiState.value.copy(pressCount = 0)
            triggerSos(count)
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
        } catch (e: retrofit2.HttpException) {
            // The server answered: not a connection problem
            _uiState.value = _uiState.value.copy(
                isSending = false,
                error = "Сервер отклонил сигнал (код ${e.code()}). Сообщите родителю."
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSending = false,
                error = "Ошибка отправки. Проверьте соединение."
            )
        }
    }

    /**
     * A fresh high-accuracy GPS fix. The last known location can be minutes
     * old or come from a cell tower hundreds of metres away, so it is only a
     * fallback when no fix arrives in time (e.g. indoors).
     */
    private suspend fun getCurrentLocation(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(15_000)
                .setDurationMillis(LOCATION_TIMEOUT_MS)
                .build()
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) { client.getCurrentLocation(request, CancellationTokenSource().token).await() }
                ?: client.lastLocation.await()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
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

    companion object {
        private const val PRESS_WINDOW_MS = 1_500L
        private const val EMERGENCY_PRESSES = 8 // backend marks 8+ presses as EMERGENCY
        private const val LOCATION_TIMEOUT_MS = 6_000L
    }

    override fun onCleared() {
        pressResetJob?.cancel()
        super.onCleared()
    }
}
