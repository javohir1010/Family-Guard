package com.familyguard.app.data.model

import com.google.gson.annotations.SerializedName

// Auth
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(
    val email: String,
    val username: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val password: String,
    @SerializedName("password2") val passwordConfirm: String,
    val role: String,
    @SerializedName("phone_number") val phoneNumber: String = "",
    @SerializedName("device_name") val deviceName: String = ""
)
data class RegisterResponse(
    val message: String,
    val access: String,
    val refresh: String,
    val user: UserProfile
)
data class LoginResponse(val access: String, val refresh: String)
data class DeviceTokenRequest(@SerializedName("device_token") val deviceToken: String)
data class UserProfile(
    val id: String, val email: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val role: String, val family: String?
)

// Family
data class JoinFamilyRequest(
    val code: String,
    @SerializedName("device_name") val deviceName: String?
)
data class JoinFamilyResponse(val message: String)

// Location
data class LocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val altitude: Double?,
    val speed: Double?,
    @SerializedName("battery_level") val batteryLevel: Int?,
    @SerializedName("recorded_at") val recordedAt: String
)

// SOS
data class SosTriggerRequest(
    @SerializedName("press_count") val pressCount: Int,
    val latitude: Double?,
    val longitude: Double?,
    val message: String = ""
)
data class SosTriggerResponse(
    val message: String,
    @SerializedName("sos_id") val sosId: String,
    @SerializedName("sos_type") val sosType: String
)

// DNS
data class BlocklistResponse(val domains: List<String>, val count: Int)
data class DnsQueryBatch(val queries: List<Query>) {
    data class Query(
        val domain: String,
        @SerializedName("query_type") val queryType: String = "A",
        @SerializedName("was_blocked") val wasBlocked: Boolean,
        val timestamp: String
    )
}

// App Control
data class AppRule(
    val id: String,
    val child: String,
    @SerializedName("package_name") val packageName: String,
    @SerializedName("app_name") val appName: String?,
    @SerializedName("rule_type") val ruleType: String,   // "block" | "limit"
    @SerializedName("daily_limit_minutes") val dailyLimitMinutes: Int?,
    @SerializedName("is_active") val isActive: Boolean
)

data class AppUsageBatch(val usages: List<Usage>) {
    data class Usage(
        @SerializedName("package_name") val packageName: String,
        @SerializedName("app_name") val appName: String,
        @SerializedName("duration_seconds") val durationSeconds: Long,
        val date: String,
        @SerializedName("last_used") val lastUsed: String?
    )
}

data class PermissionAlertRequest(
    val permission: String,
    @SerializedName("was_granted") val wasGranted: Boolean
)

data class ScreenSchedule(
    val id: String,
    val name: String,
    @SerializedName("days_bitmask") val daysBitmask: Int,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("is_active") val isActive: Boolean
)
