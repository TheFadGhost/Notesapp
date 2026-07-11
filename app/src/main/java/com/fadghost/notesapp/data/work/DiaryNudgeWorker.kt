package com.fadghost.notesapp.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fadghost.notesapp.MainActivity
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Daily journaling nudge (PLAN.md §7). Implemented with WorkManager periodic work
 * ONLY — inexact timing is acceptable per plan, and exact alarms are the calendar
 * milestone's domain. The notification deep-links to today's diary entry.
 *
 * Uses a Hilt-free [CoroutineWorker] (matches [TrashPurgeWorker]); it needs no
 * injected deps — just the app context to post a notification.
 */
class DiaryNudgeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        // Respect POST_NOTIFICATIONS (Android 13+). If not granted, quietly succeed —
        // the runtime-request flow is built elsewhere; we only check here (PLAN.md §7).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        ensureChannel(ctx)

        val openDiary = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DIARY, true)
        }
        val pending = PendingIntent.getActivity(
            ctx, 0, openDiary,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Time to journal")
            .setContentText("Take a moment to write today's entry.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "nudges"
        const val EXTRA_OPEN_DIARY = "open_diary"
        private const val CHANNEL_NAME = "Nudges"
        private const val NOTIFICATION_ID = 4201
        private const val WORK_NAME = "diary_nudge"

        /** Idempotent channel creation (safe even if another component also creates "Nudges"). */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Gentle daily reminders to write in your diary"
                }
            )
        }

        /** (Re)schedule the daily nudge for [hour]:[minute] local time. */
        fun schedule(context: Context, hour: Int, minute: Int) {
            val delay = initialDelayMillis(ZonedDateTime.now(), hour, minute)
            val request = PeriodicWorkRequestBuilder<DiaryNudgeWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Millis from [now] until the next occurrence of [hour]:[minute]. */
        fun initialDelayMillis(now: ZonedDateTime, hour: Int, minute: Int): Long {
            var next = now.withHour(hour.coerceIn(0, 23))
                .withMinute(minute.coerceIn(0, 59))
                .withSecond(0)
                .withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMillis()
        }
    }
}
