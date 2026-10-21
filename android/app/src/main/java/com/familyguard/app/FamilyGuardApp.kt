package com.familyguard.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.familyguard.app.services.PushMessagingService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * WorkManager is initialised on demand with Hilt's worker factory, so
 * @HiltWorker classes (UsageStatsSyncWorker) get their dependencies injected.
 * The default startup initializer is removed in AndroidManifest.xml.
 */
@HiltAndroidApp
class FamilyGuardApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Background pushes are shown by FCM in this channel, so it must exist early
        PushMessagingService.ensureChannel(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
