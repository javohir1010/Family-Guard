package com.familyguard.app.data.api

import com.familyguard.app.data.model.*
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/register/")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("auth/login/")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("auth/profile/")
    suspend fun getProfile(): UserProfile

    @POST("auth/device-token/")
    suspend fun updateDeviceToken(@Body request: DeviceTokenRequest)

    // Family
    @POST("family/invite/join/")
    suspend fun joinFamily(@Body request: JoinFamilyRequest): JoinFamilyResponse

    // Location
    @POST("location/")
    suspend fun sendLocation(@Body request: LocationRequest)

    // SOS
    @POST("sos/trigger/")
    suspend fun triggerSos(@Body request: SosTriggerRequest): SosTriggerResponse

    // DNS
    @GET("dns/blocklist/")
    suspend fun getBlocklist(): BlocklistResponse

    @POST("dns/queries/batch/")
    suspend fun uploadDnsQueries(@Body batch: DnsQueryBatch)

    // App Control
    @GET("apps/rules/device/")
    suspend fun getActiveRules(): List<AppRule>

    @POST("apps/usage/batch/")
    suspend fun uploadAppUsage(@Body batch: AppUsageBatch)

    @POST("apps/permissions/")
    suspend fun reportPermissionChange(@Body request: PermissionAlertRequest)

    @GET("apps/schedule/device/")
    suspend fun getScreenSchedule(): List<ScreenSchedule>
}
