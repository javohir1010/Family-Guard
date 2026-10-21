package com.familyguard.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
