package com.animecliplabs.clipmark.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.animecliplabs.clipmark.MainActivity
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.core.batch.BatchJob
import com.animecliplabs.clipmark.core.batch.BatchProgress
import java.util.UUID

class BatchNotifications(context: Context) {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_progress_desc)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_done_desc)
            },
        )
    }

    fun foregroundInfo(workId: UUID, progress: BatchProgress): ForegroundInfo {
        val title = if (progress.total > 0) {
            context.getString(R.string.notif_progress_title, (progress.processed + 1).coerceAtMost(progress.total), progress.total)
        } else {
            context.getString(R.string.notif_progress_starting)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_watermark)
            .setContentTitle(title)
            .setContentText(progress.currentName ?: context.getString(R.string.notif_progress_text))
            .setProgress(progress.total, progress.processed, progress.total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.action_cancel), WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ForegroundInfo(ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else {
            ForegroundInfo(ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
    }

    fun showFinished(job: BatchJob) {
        if (!canPost()) return
        val res = context.resources
        val title = when {
            job.cancelled -> context.getString(R.string.notif_done_cancelled, job.savedCount)
            job.failedCount > 0 -> res.getQuantityString(R.plurals.notif_done_with_failures, job.savedCount, job.savedCount, job.failedCount)
            else -> res.getQuantityString(R.plurals.notif_done, job.savedCount, job.savedCount)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_watermark)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_done_text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        @Suppress("MissingPermission") // checked in canPost()
        manager.notify(ID_DONE, notification)
    }

    private fun canPost(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_BATCH, true)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_PROGRESS = "batch_progress"
        const val CHANNEL_DONE = "batch_done"
        private const val ID_PROGRESS = 1001
        private const val ID_DONE = 1002
    }
}
