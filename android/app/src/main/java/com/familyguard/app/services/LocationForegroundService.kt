package com.familyguard.app.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.*
import androidx.core.app.NotificationCompat
import com.familyguard.app.R
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.local.TokenStorage
import com.familyguard.app.data.model.LocationRequest
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class LocationForegroundService : Service() {

    @Inject lateinit var apiService: ApiService
    @Inject lateinit var tokenStorage: TokenStorage

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationCallback: LocationCallback? = null

    companion object {
        const val CHANNEL_ID = "fg_location_channel"
        const val NOTIFICATION_ID = 1001
        const val LOCATION_INTERVAL_MS = 10 * 60 * 1000L  // 10 minutes
        const val LOCATION_FASTEST_INTERVAL_MS = 5 * 60 * 1000L

        fun startService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                serviceScope.launch {
                    sendLocationToServer(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private suspend fun sendLocationToServer(location: Location) {
        try {
            val batteryLevel = getBatteryLevel()
            val request = LocationRequest(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                batteryLevel = batteryLevel,
                recordedAt = java.time.Instant.ofEpochMilli(location.time).toString()
            )
            apiService.sendLocation(request)
        } catch (e: Exception) {
            // Buffer locally via Room if network fails
            // (RoomLocationBuffer.insert(location) — see data layer)
        }
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FamilyGuard активен")
            .setContentText("Защита включена • местоположение отслеживается")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Служба защиты FamilyGuard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Отслеживание местоположения для семейной безопасности"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
