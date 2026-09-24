package com.samielmadani.elmadanistore.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samielmadani.elmadanistore.data.StoreRepository
import com.samielmadani.elmadanistore.data.NotificationHelper

class ReleaseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        StoreRepository(applicationContext).loadApps().filter { it.hasUpdate }.forEach { NotificationHelper.showUpdate(applicationContext, it) }
        Result.success()
    }.getOrDefault(Result.retry())
}
