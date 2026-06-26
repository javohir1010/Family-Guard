package com.familyguard.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.local.TokenStorage
import com.familyguard.app.data.model.JoinFamilyRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val hasFamily: Boolean = false,
    val isLoading: Boolean = false,
    val inviteCode: String = "",
    val deviceName: String = "",
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val apiService: ApiService,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val isLoggedIn = tokenStorage.accessTokenFlow.map { !it.isNullOrEmpty() }

    init {
        checkLoginState()
    }

    private fun checkLoginState() {
        viewModelScope.launch {
            val loggedIn = tokenStorage.isLoggedIn()
            val familyId = tokenStorage.getFamilyId()
            _uiState.value = _uiState.value.copy(
                isLoggedIn = loggedIn,
                hasFamily = !familyId.isNullOrEmpty()
            )
        }
    }

    fun onInviteCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(inviteCode = code, error = null)
    }

    fun onDeviceNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(deviceName = name)
    }

    fun joinFamily() {
        val code = _uiState.value.inviteCode
        if (code.length != 6) return

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val deviceName = _uiState.value.deviceName.ifEmpty {
                    android.os.Build.MODEL
                }

                apiService.joinFamily(
                    JoinFamilyRequest(code = code, deviceName = deviceName)
                )

                // After joining, refresh profile to update family state
                val profile = apiService.getProfile()
                tokenStorage.saveUserInfo(
                    userId = profile.id,
                    familyId = profile.family,
                    deviceName = deviceName
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    hasFamily = true
                )
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("400") == true -> "Неверный или истёкший код"
                    e.message?.contains("404") == true -> "Семья не найдена"
                    else -> "Ошибка подключения. Проверьте интернет."
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMsg
                )
            }
        }
    }

    fun loginWithCredentials(email: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val response = apiService.login(
                    com.familyguard.app.data.model.LoginRequest(email, password)
                )
                tokenStorage.saveTokens(response.access, response.refresh)
                
                // Fetch profile to get family status
                val profile = apiService.getProfile()
                tokenStorage.saveUserInfo(
                    userId = profile.id,
                    familyId = profile.family,
                    deviceName = android.os.Build.MODEL
                )
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false, 
                    isLoggedIn = true,
                    hasFamily = !profile.family.isNullOrEmpty()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Неверный email или пароль"
                )
            }
        }
    }

    fun register(request: com.familyguard.app.data.model.RegisterRequest) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val response = apiService.register(request)
                tokenStorage.saveTokens(response.access, response.refresh)
                tokenStorage.saveUserInfo(
                    userId = response.user.id,
                    familyId = response.user.family,
                    deviceName = request.deviceName
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    hasFamily = !response.user.family.isNullOrEmpty()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка регистрации. Возможно, email уже занят."
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenStorage.clear()
            _uiState.value = AuthUiState()
        }
    }
}
