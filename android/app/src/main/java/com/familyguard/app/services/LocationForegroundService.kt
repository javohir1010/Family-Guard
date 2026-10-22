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
import com.familyguard.app.data.local.PendingLocation
import com.familyguard.app.data.local.PendingLocationDao
import com.familyguard.app.data.local.TokenStorage
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class LocationForegroundService : Service() {

    @Inject lateinit var apiService: ApiService
    @Inject lateinit var tokenStorage: TokenStorage
    @Inject lateinit var locationBuffer: PendingLocationDao

    private val flushMutex = Mutex()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationCallback: LocationCallback? = null

    companion object {
        const val CHANNEL_ID = "fg_location_channel"
        const val NOTIFICATION_ID = 1001
        const val LOCATION_INTERVAL_MS = 10 * 60 * 1000L  // 10 minutes
        const val LOCATION_FASTEST_INTERVAL_MS = 5 * 60 * 1000L

        private const val TAG = "LocationService"
        private const val MAX_BUFFERED_LOCATIONS = 1000 // ~1 week at one point per 10 min

        fun hasLocationPermission(context: Context): Boolean =
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

        // An adult may pause sharing their position (a child cannot: the server refuses)
        private const val PREFS = "fg_location"
        private const val KEY_PAUSED = "paused"

        fun isPaused(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PAUSED, false)

        /** Remembers the choice (also for BootReceiver) and stops or starts tracking. */
        fun setPaused(context: Context, paused: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_PAUSED, paused).apply()
            if (paused) stopService(context) else startService(context)
        }

        /** Starts tracking if location permission is granted and sharing is not paused. */
        fun startService(context: Context) {
            if (!hasLocationPermission(context) || isPaused(context)) return
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

    /**
     * Every point goes through the Room buffer first; the buffer is then sent
     * oldest-first, so points recorded offline reach the server in order and
     * the newest one ends up as the current position on the map.
     */
    private suspend fun sendLocationToServer(location: Location) {
        try {
            locationBuffer.insert(
                PendingLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                    batteryLevel = getBatteryLevel(),
                    recordedAt = java.time.Instant.ofEpochMilli(location.time).toString()
                )
            )
            locationBuffer.trim(MAX_BUFFERED_LOCATIONS)
        } catch (e: Exception) {
            Log.w(TAG, "Could not buffer location", e)
        }
        flushLocationBuffer()
    }

    private suspend fun flushLocationBuffer() = flushMutex.withLock {
        while (true) {
            val batch = locationBuffer.oldest(50)
            if (batch.isEmpty()) return@withLock
            for (point in batch) {
                try {
                    apiService.sendLocation(point.toRequest())
                } catch (e: retrofit2.HttpException) {
                    // A point the server rejects would block the queue forever: drop it.
                    // Auth, timeout and rate-limit errors are retried later.
                    if (e.code() in 400..499 && e.code() !in setOf(401, 408, 429)) {
                        Log.w(TAG, "Server rejected buffered location: ${e.code()}")
                    } else return@withLock
                } catch (e: Exception) {
                    return@withLock // Offline: keep the rest for the next fix
                }
                locationBuffer.delete(point.id)
            }
        }
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FamilyGuard активен")
            .setContentText("Местоположение видно вашей семье")
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
