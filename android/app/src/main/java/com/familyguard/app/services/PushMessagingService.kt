package com.familyguard.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.familyguard.app.R
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.local.TokenStorage
import com.familyguard.app.data.model.DeviceTokenRequest
import com.familyguard.app.ui.MainActivity
import com.familyguard.app.viewmodel.AuthViewModel
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Firebase Cloud Messaging: receives pushes from the backend (SOS, geofence,
 * revoked permissions) and keeps the FCM token on the server up to date.
 *
 * In the background FCM shows "notification" messages by itself; this class
 * only has to display them while the app is in the foreground.
 */
class PushMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "fg_alerts_channel"
        private const val TAG = "PushMessaging"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Оповещения FamilyGuard",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "SOS, геозоны и отключение защиты" }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }
    }

    override fun onNewToken(token: String) {
        PushTokenSyncWorker.enqueue(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Silent data messages from the backend
        when (message.data["type"]) {
            "blocklist_updated" -> {
                DnsVpnService.refreshBlocklist(applicationContext)
                return
            }
            "rules_updated" -> {
                AppMonitorAccessibilityService.refreshNow()
                return
            }
            "unlinked" -> {
                // A parent logged this device out: drop the session right away
                // (an open app sees it in MainActivity and goes to login)
                AuthViewModel.stopBackgroundWork(applicationContext)
                runBlocking { TokenStorage(applicationContext).clear() }
                return
            }
        }
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        ensureChannel(this)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(this)
                .notify(message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted", e)
        }
    }
}

/** Sends the current FCM token to the backend once the user is logged in and online. */
@HiltWorker
class PushTokenSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: ApiService,
    private val tokenStorage: TokenStorage
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "push_token_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PushTokenSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        if (tokenStorage.getAccessToken().isNullOrEmpty()) return Result.success()
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: IllegalStateException) {
            return Result.success() // Built without google-services.json: push is off
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        return try {
            apiService.updateDeviceToken(DeviceTokenRequest(token))
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
