package com.familyguard.app.data.api

import com.familyguard.app.BuildConfig
import com.familyguard.app.data.local.TokenStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * On 401 refreshes the access token via /auth/refresh/ and retries the request.
 *
 * The backend rotates refresh tokens (ROTATE_REFRESH_TOKENS + blacklist), so
 * the new refresh token is stored and refreshes are serialised: two parallel
 * refreshes with the same token would invalidate each other.
 * If the refresh token itself is rejected the stored session is cleared,
 * which sends the UI back to the login screen.
 */
class TokenAuthenticator(private val tokenStorage: TokenStorage) : Authenticator {

    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.endsWith("auth/login/") || path.endsWith("auth/register/") ||
            path.endsWith("auth/refresh/")
        ) return null
        if (response.priorResponse != null) return null // already retried once

        synchronized(this) {
            val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val currentToken = runBlocking { tokenStorage.getAccessToken() }
            // Another request refreshed while we were waiting for the lock
            if (currentToken != null && currentToken != sentToken) {
                return response.request.withToken(currentToken)
            }

            val refresh = runBlocking { tokenStorage.getRefreshToken() } ?: return null
            val body = gson.toJson(mapOf("refresh" to refresh))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(BuildConfig.BASE_URL + "auth/refresh/")
                .post(body)
                .build()

            return try {
                refreshClient.newCall(request).execute().use { res ->
                    if (!res.isSuccessful) {
                        if (res.code == 400 || res.code == 401) {
                            runBlocking { tokenStorage.clear() }
                        }
                        return null
                    }
                    val json = gson.fromJson(res.body?.string(), JsonObject::class.java)
                    val access = json.get("access").asString
                    val newRefresh = json.get("refresh")?.asString ?: refresh
                    runBlocking { tokenStorage.saveTokens(access, newRefresh) }
                    response.request.withToken(access)
                }
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun Request.withToken(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()
}
