package com.familyguard.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "familyguard_tokens")

@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_ACCESS_TOKEN  = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID       = stringPreferencesKey("user_id")
        private val KEY_FAMILY_ID     = stringPreferencesKey("family_id")
        private val KEY_DEVICE_NAME   = stringPreferencesKey("device_name")
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }

    suspend fun getAccessToken(): String? =
        context.dataStore.data.first()[KEY_ACCESS_TOKEN]

    suspend fun getRefreshToken(): String? =
        context.dataStore.data.first()[KEY_REFRESH_TOKEN]

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN]  = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun saveUserInfo(userId: String, familyId: String?, deviceName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID]     = userId
            prefs[KEY_FAMILY_ID]   = familyId ?: ""
            prefs[KEY_DEVICE_NAME] = deviceName
        }
    }

    suspend fun getFamilyId(): String? =
        context.dataStore.data.first()[KEY_FAMILY_ID]?.takeIf { it.isNotEmpty() }

    suspend fun isLoggedIn(): Boolean =
        getAccessToken()?.isNotEmpty() == true

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
