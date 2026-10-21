package com.familyguard.app.services

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.local.TokenStorage
import com.familyguard.app.data.model.PermissionAlertRequest
import com.familyguard.app.ui.screens.isDnsFilterRequested
import com.familyguard.app.ui.screens.readProtectionStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Tells the parent when the child turns protection off (or back on):
 * DNS filter VPN, accessibility service, location. Runs every 15 minutes
 * and right after the VPN is revoked; only state changes are reported,
 * the backend pushes an alert to parents for each "revoked" one.
 */
@HiltWorker
class ProtectionWatchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: ApiService,
    private val tokenStorage: TokenStorage
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "protection_watch"
        private const val WORK_NAME_NOW = "protection_watch_now"
        private const val PREFS = "fg_protection_watch"

        // Must match PermissionAlert.PermissionType on the backend
        private const val VPN = "BIND_VPN_SERVICE"
        private const val ACCESSIBILITY = "BIND_ACCESSIBILITY_SERVICE"
        private const val LOCATION = "ACCESS_FINE_LOCATION"
        private const val PRIVATE_DNS = "PRIVATE_DNS"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProtectionWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** One-off check, e.g. from VpnService.onRevoke(). The delay lets the VPN network go down. */
        fun checkSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProtectionWatchWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW, ExistingWorkPolicy.REPLACE, request
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (tokenStorage.getFamilyId() == null) {
            // Logged out / not paired: forget old state so a new login starts clean
            prefs.edit().clear().apply()
            return Result.success()
        }

        val status = readProtectionStatus(applicationContext)
        val current = buildMap {
            put(LOCATION, status.location && isLocationEnabled())
            put(ACCESSIBILITY, status.accessibility)
            // Only watched once the DNS filter was set up on this device
            if (isDnsFilterRequested(applicationContext)) {
                put(VPN, DnsVpnService.isRunning)
                // "Enabled" = the filter can't be bypassed via strict Private DNS
                put(PRIVATE_DNS, !isStrictPrivateDnsOn())
            }
        }

        return try {
            for ((permission, enabled) in current) {
                val key = "state_$permission"
                // The first observation is only remembered: nothing has changed yet
                if (prefs.contains(key) && prefs.getBoolean(key, enabled) != enabled) {
                    apiService.reportPermissionChange(PermissionAlertRequest(permission, enabled))
                }
                prefs.edit().putBoolean(key, enabled).apply()
            }
            Result.success()
        } catch (e: Exception) {
            // State of the failed item is not saved, so the change is reported on retry
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * Strict Private DNS (Settings → Private DNS → hostname) sends DNS-over-TLS
     * straight to that host, past the VPN, so the DNS filter sees nothing.
     * Automatic mode is fine: it falls back to our plain DNS server.
     */
    private fun isStrictPrivateDnsOn(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { cm.getLinkProperties(it)?.privateDnsServerName != null }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }
}
