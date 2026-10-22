package com.familyguard.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.model.AppNotification
import com.familyguard.app.data.model.MemberPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val role: String? = null,          // "parent" | "child"; null until known
    val firstName: String = "",
    val children: List<MemberPosition> = emptyList(),
    val events: List<AppNotification> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)

/**
 * Decides which home screen to show and loads the parent's overview.
 * The role is cached so a child without internet still gets the SOS screen
 * immediately.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val apiService: ApiService,
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("fg_ui", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        HomeUiState(
            role = prefs.getString(KEY_ROLE, null),
            firstName = prefs.getString(KEY_NAME, "") ?: ""
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val profile = apiService.getProfile()
                prefs.edit()
                    .putString(KEY_ROLE, profile.role)
                    .putString(KEY_NAME, profile.firstName)
                    .apply()
                _uiState.value = _uiState.value.copy(role = profile.role, firstName = profile.firstName)
            } catch (e: Exception) {
                // Offline: keep the cached role, SOS screen is the safe default
                if (_uiState.value.role == null) {
                    _uiState.value = _uiState.value.copy(role = ROLE_CHILD)
                }
            }
            if (_uiState.value.role == ROLE_PARENT) {
                while (isActive) {
                    refresh()
                    delay(60_000)
                }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                // coroutineScope: a failed request surfaces here instead of crashing the app
                val (positions, events) = coroutineScope {
                    val positions = async { apiService.getCurrentPositions() }
                    val events = async { apiService.getNotifications().results.take(10) }
                    positions.await() to events.await()
                }
                _uiState.value = _uiState.value.copy(
                    children = positions.filter { it.role == ROLE_CHILD },
                    events = events,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = "Не удалось обновить данные. Проверьте соединение."
                )
            }
        }
    }

    /** Logs the child's phone out; the child stays in the family. */
    fun unlinkDevice(child: MemberPosition) {
        viewModelScope.launch {
            try {
                apiService.unlinkDevice(child.userId)
                _uiState.value = _uiState.value.copy(
                    children = _uiState.value.children.filter { it.userId != child.userId },
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Не удалось отвязать устройство. Проверьте соединение.")
            }
        }
    }

    companion object {
        const val ROLE_PARENT = "parent"
        const val ROLE_CHILD = "child"
        private const val KEY_ROLE = "role"
        private const val KEY_NAME = "first_name"
    }
}
