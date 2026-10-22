package com.familyguard.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.local.TokenStorage
import com.familyguard.app.data.model.JoinFamilyRequest
import com.familyguard.app.services.DnsVpnService
import com.familyguard.app.services.LocationForegroundService
import com.familyguard.app.services.PushTokenSyncWorker
import com.familyguard.app.services.UsageStatsSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val error: String? = null,
    // Parent login: waiting for the code sent by email
    val otpChallengeId: String? = null,
    val otpEmailHint: String? = null,
    // Joined with an invite code, a parent has not approved yet
    val waitingApproval: Boolean = false,
    // Child logout: shown in a dialog while a parent decides
    val logoutPending: Boolean = false,
    val logoutMessage: String? = null
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

    private var pollJob: Job? = null

    init {
        checkLoginState()
    }

    private fun checkLoginState() {
        viewModelScope.launch {
            val loggedIn = tokenStorage.isLoggedIn()
            if (loggedIn) PushTokenSyncWorker.enqueue(context)
            var familyId = tokenStorage.getFamilyId()
            if (loggedIn && familyId.isNullOrEmpty()) {
                // A parent may have approved the join request while the app was closed
                familyId = try {
                    val profile = apiService.getProfile()
                    profile.family?.also {
                        tokenStorage.saveUserInfo(profile.id, it, android.os.Build.MODEL)
                    }
                } catch (e: Exception) { null }
            }
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

                val response = apiService.joinFamily(
                    JoinFamilyRequest(code = code, deviceName = deviceName)
                )
                val request = response.request
                if (request == null) {
                    onJoinApproved(deviceName)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, waitingApproval = true)
                    waitForJoinApproval(request.id, deviceName)
                }
            } catch (e: CancellationException) {
                throw e
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

    private suspend fun onJoinApproved(deviceName: String) {
        val profile = apiService.getProfile()
        tokenStorage.saveUserInfo(
            userId = profile.id,
            familyId = profile.family,
            deviceName = deviceName
        )
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            waitingApproval = false,
            isLoggedIn = true,
            hasFamily = !profile.family.isNullOrEmpty()
        )
    }

    private fun waitForJoinApproval(requestId: String, deviceName: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            when (pollRequest(requestId)) {
                "approved" -> try {
                    onJoinApproved(deviceName)
                } catch (e: Exception) {
                    // Picked up from the profile on the next app start
                    _uiState.value = _uiState.value.copy(waitingApproval = false,
                        error = "Заявку подтвердили. Перезапустите приложение.")
                }
                "rejected" -> _uiState.value = _uiState.value.copy(waitingApproval = false,
                    inviteCode = "", error = "Родитель отклонил заявку. Попросите новый код.")
                else -> _uiState.value = _uiState.value.copy(waitingApproval = false,
                    inviteCode = "", error = "Заявка устарела. Попросите новый код.")
            }
        }
    }

    /** Polls a join/logout request until a parent decides; network errors are retried. */
    private suspend fun pollRequest(requestId: String): String {
        var status: String
        do {
            delay(3000)
            status = try {
                apiService.getMemberRequest(requestId).status
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "pending"
            }
        } while (status == "pending")
        return status
    }

    fun loginWithCredentials(email: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val response = apiService.login(
                    com.familyguard.app.data.model.LoginRequest(email, password)
                )
                if (response.otpRequired && response.challengeId != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        otpChallengeId = response.challengeId,
                        otpEmailHint = response.emailHint
                    )
                } else {
                    finishLogin(response.access!!, response.refresh!!)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = when ((e as? HttpException)?.code()) {
                        401 -> "Неверный email или пароль"
                        429 -> "Слишком много попыток. Подождите минуту."
                        503 -> "Не удалось отправить код на почту. Попробуйте позже."
                        null -> "Нет связи с сервером. Проверьте интернет и адрес сервера."
                        else -> "Ошибка сервера (${(e as HttpException).code()})"
                    }
                )
            }
        }
    }

    /** Second step of a parent login: the 6-digit code from the email. */
    fun verifyLoginCode(code: String) {
        val challengeId = _uiState.value.otpChallengeId ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val tokens = apiService.verifyLogin(
                    com.familyguard.app.data.model.LoginVerifyRequest(challengeId, code)
                )
                finishLogin(tokens.access, tokens.refresh)
            } catch (e: Exception) {
                val http = e as? HttpException
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = when (http?.code()) {
                        400 -> serverErrorText(http)
                        429 -> "Слишком много попыток. Подождите минуту."
                        null -> "Нет связи с сервером. Проверьте интернет."
                        else -> "Ошибка сервера (${http.code()})"
                    }
                )
            }
        }
    }

    fun cancelLoginCode() {
        _uiState.value = _uiState.value.copy(otpChallengeId = null, otpEmailHint = null, error = null)
    }

    private suspend fun finishLogin(access: String, refresh: String) {
        tokenStorage.saveTokens(access, refresh)
        // Fetch profile to get family status
        val profile = apiService.getProfile()
        tokenStorage.saveUserInfo(
            userId = profile.id,
            familyId = profile.family,
            deviceName = android.os.Build.MODEL
        )
        PushTokenSyncWorker.enqueue(context)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            otpChallengeId = null,
            otpEmailHint = null,
            isLoggedIn = true,
            hasFamily = !profile.family.isNullOrEmpty()
        )
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
                PushTokenSyncWorker.enqueue(context)
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

    /**
     * A child device logs out only after a parent approves it in the dashboard;
     * parents (and devices without a family) leave right away. Needs the network:
     * otherwise turning on flight mode would be a way around the approval.
     */
    fun logout() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            showLogout("Выходим…")
            try {
                if (serverLogout()) return@launch finishLogout()
                val request = apiService.requestLogout()
                showLogout("Запрос отправлен. Ждём, когда родитель подтвердит выход…")
                when (pollRequest(request.id)) {
                    "approved" -> if (serverLogout()) finishLogout()
                        else showLogout("Подтверждение не найдено. Попробуйте ещё раз.", pending = false)
                    "rejected" -> showLogout("Родитель отклонил выход.", pending = false)
                    else -> showLogout("Запрос устарел. Попробуйте ещё раз.", pending = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showLogout("Нет связи с сервером. Выйти можно только онлайн.", pending = false)
            }
        }
    }

    fun dismissLogout() {
        pollJob?.cancel()
        _uiState.value = _uiState.value.copy(logoutPending = false, logoutMessage = null)
    }

    private fun showLogout(message: String, pending: Boolean = true) {
        _uiState.value = _uiState.value.copy(logoutPending = pending, logoutMessage = message)
    }

    /** true = logged out on the server, false = a parent has to approve first. */
    private suspend fun serverLogout(): Boolean {
        val refresh = tokenStorage.getRefreshToken() ?: return true
        return try {
            apiService.logout(com.familyguard.app.data.model.LogoutRequest(refresh))
            true
        } catch (e: HttpException) {
            e.code() != 403  // e.g. 400 for an already dead token: nothing to keep
        }
    }

    private suspend fun finishLogout() {
        // Stop background work first — it would only fail with 401 afterwards
        LocationForegroundService.stopService(context)
        DnsVpnService.stopVpn(context)
        UsageStatsSyncWorker.cancel(context)
        tokenStorage.clear()
        _uiState.value = AuthUiState(isReady = true)
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
