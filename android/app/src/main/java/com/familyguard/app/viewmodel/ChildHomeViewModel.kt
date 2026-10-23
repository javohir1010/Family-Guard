package com.familyguard.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.model.Contact
import com.familyguard.app.data.model.ContactLabelRequest
import com.familyguard.app.data.model.MemberPosition
import com.familyguard.app.data.model.UserProfile
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class ChildHomeUiState(
    val profile: UserProfile? = null,
    val contacts: List<Contact> = emptyList(),
    val positions: List<MemberPosition> = emptyList(),
    val message: String? = null
)

/** The child's home: own profile, the family's contacts and their places on the map. */
@HiltViewModel
class ChildHomeViewModel @Inject constructor(
    private val apiService: ApiService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildHomeUiState())
    val uiState: StateFlow<ChildHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(60_000)
            }
        }
    }

    private suspend fun refresh() {
        try {
            // coroutineScope: a failed request surfaces here instead of crashing the app
            coroutineScope {
                val profile = async { apiService.getProfile() }
                val contacts = async { apiService.getContacts() }
                val positions = async { apiService.getCurrentPositions() }
                _uiState.value = _uiState.value.copy(
                    profile = profile.await(),
                    contacts = contacts.await(),
                    positions = positions.await()
                )
            }
        } catch (e: Exception) {
            // Offline: keep what was shown; SOS works on its own
        }
    }

    /** An empty label brings the automatic one ("Мама 1") back. */
    fun renameContact(contact: Contact, label: String) {
        viewModelScope.launch {
            try {
                val res = apiService.renameContact(contact.id, ContactLabelRequest(label.trim()))
                _uiState.value = _uiState.value.copy(
                    contacts = _uiState.value.contacts.map { if (it.id == res.id) it.copy(label = res.label) else it }
                )
            } catch (e: Exception) {
                showMessage("Не удалось переименовать. Проверьте соединение.")
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                if (bytes.size > MAX_AVATAR_BYTES) return@launch showMessage("Фото слишком большое (больше 5 МБ).")
                val type = context.contentResolver.getType(uri) ?: "image/jpeg"
                val part = MultipartBody.Part.createFormData(
                    "avatar", "avatar.${type.substringAfter('/')}", bytes.toRequestBody(type.toMediaTypeOrNull())
                )
                _uiState.value = _uiState.value.copy(profile = apiService.uploadAvatar(part))
            } catch (e: Exception) {
                showMessage("Не удалось загрузить фото.")
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun showMessage(text: String) {
        _uiState.value = _uiState.value.copy(message = text)
    }

    companion object {
        private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
    }
}
