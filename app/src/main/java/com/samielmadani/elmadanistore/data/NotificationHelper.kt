package com.samielmadani.elmadanistore.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.samielmadani.elmadanistore.R

object NotificationHelper {
    private const val CHANNEL = "release_updates"
    fun showUpdate(context: Context, app: StoreApp) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Release updates", NotificationManager.IMPORTANCE_DEFAULT))
        val detailIntent = Intent(context, com.samielmadani.elmadanistore.MainActivity::class.java)
            .putExtra("open_repo", app.repo)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(context, app.repo.hashCode(), detailIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("${app.name} has an update")
            .setContentText("${app.version} is ready to install")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(app.repo.hashCode(), notification) }
    }
}
