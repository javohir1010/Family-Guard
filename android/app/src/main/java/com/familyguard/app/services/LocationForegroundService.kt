package com.familyguard.app.services

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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

        private const val TAG = "LocationService"

        fun hasLocationPermission(context: Context): Boolean =
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

        /** Starts tracking if location permission is granted; otherwise does nothing. */
        fun startService(context: Context) {
            if (!hasLocationPermission(context)) return
            val intent = Intent(context, LocationForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException when started from background
                Log.w(TAG, "Could not start location service", e)
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
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                else 0
            )
        } catch (e: Exception) {
            // Missing location permission (SecurityException on Android 14+)
            // or not allowed to start from background
            Log.w(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            // Nothing to report without a session (e.g. after logout or before login)
            if (!tokenStorage.isLoggedIn()) {
                stopSelf()
                return@launch
            }
            withContext(Dispatchers.Main) { startLocationUpdates() }
        }
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (locationCallback != null) return // onStartCommand can run several times
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
            locationCallback = null
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
