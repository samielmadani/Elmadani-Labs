package com.samielmadani.elmadanistudio.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samielmadani.elmadanistudio.data.StoreRepository
import com.samielmadani.elmadanistudio.data.NotificationHelper

class ReleaseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val repository = StoreRepository(applicationContext)
        repository.loadApps().filter { it.needsInstall }.forEach { NotificationHelper.showUpdate(applicationContext, it) }
        repository.latestSelfUpdate?.takeIf { it.hasUpdate }?.let { NotificationHelper.showUpdate(applicationContext, it) }
        Result.success()
    }.getOrDefault(Result.retry())
}
