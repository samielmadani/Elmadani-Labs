package com.samielmadani.elmadanistore.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "release-check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReleaseCheckWorker>(12, TimeUnit.HOURS).build()
        )
    }
}
