package com.familyguard.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.local.TokenStorage
import com.familyguard.app.data.model.JoinFamilyRequest
import com.familyguard.app.services.DnsVpnService
import com.familyguard.app.services.LocationForegroundService
import com.familyguard.app.services.UsageStatsSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isReady: Boolean = false,      // stored session has been read
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
    private val tokenStorage: TokenStorage,
    @ApplicationContext private val context: Context
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
                isReady = true,
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
                val errorMsg = when ((e as? HttpException)?.code()) {
                    400 -> "Неверный или истёкший код"
                    404 -> "Семья не найдена"
                    null -> "Ошибка подключения. Проверьте интернет."
                    else -> "Ошибка сервера (${(e as HttpException).code()})"
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
                    error = when ((e as? HttpException)?.code()) {
                        401 -> "Неверный email или пароль"
                        429 -> "Слишком много попыток. Подождите минуту."
                        null -> "Нет связи с сервером. Проверьте интернет и адрес сервера."
                        else -> "Ошибка сервера (${(e as HttpException).code()})"
                    }
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
                    error = (e as? HttpException)?.let { serverErrorText(it) }
                        ?: "Нет связи с сервером. Проверьте интернет и адрес сервера."
                )
            }
        }
    }

    fun logout() {
        // Stop background work first — it would only fail with 401 afterwards
        LocationForegroundService.stopService(context)
        DnsVpnService.stopVpn(context)
        UsageStatsSyncWorker.cancel(context)
        viewModelScope.launch {
            try {
                tokenStorage.getRefreshToken()?.let {
                    apiService.logout(com.familyguard.app.data.model.LogoutRequest(it))
                }
            } catch (e: Exception) {
                // Offline logout is fine: tokens are dropped locally anyway
            }
            tokenStorage.clear()
            _uiState.value = AuthUiState(isReady = true)
        }
    }

    /** Joins DRF validation messages, e.g. {"email": ["...already exists."]}. */
    private fun serverErrorText(e: HttpException): String {
        val body = try { e.response()?.errorBody()?.string() } catch (ignored: Exception) { null }
        val messages = try {
            com.google.gson.JsonParser.parseString(body ?: "").asJsonObject.entrySet()
                .flatMap { (_, v) -> if (v.isJsonArray) v.asJsonArray.map { it.asString }
                    else listOf(if (v.isJsonPrimitive) v.asString else v.toString()) }
        } catch (ignored: Exception) { emptyList() }
        return messages.joinToString("\n").ifEmpty { "Ошибка регистрации (${e.code()})" }
    }
}
