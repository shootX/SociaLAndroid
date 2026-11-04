package com.shoto.socialsave.work

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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shoto.socialsave.R
import com.shoto.socialsave.container
import com.shoto.socialsave.data.AppSettings
import com.shoto.socialsave.data.SavedPost
import com.shoto.socialsave.ui.MainActivity
import com.shoto.socialsave.util.displayTitle
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * The weekly "look back" notification. One job is scheduled for the next slot; when it fires it builds
 * the text from the library at that moment, posts it, and schedules the following week.
 */
object ReminderScheduler {
    private const val UNIQUE_NAME = "weekly-reminder"
    const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 7

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** [fromWorker] chains the next week after the running job instead of cancelling it. */
    fun reschedule(context: Context, settings: AppSettings, fromWorker: Boolean = false) {
        val work = WorkManager.getInstance(context)
        if (!settings.remindersEnabled.value) {
            work.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val now = LocalDateTime.now()
        val next = nextFireTime(now, settings.reminderDay.value, settings.reminderMinutes.value)
        val delay = java.time.Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        work.enqueueUniqueWork(UNIQUE_NAME, policy, request)
    }

    fun nextFireTime(now: LocalDateTime, day: DayOfWeek, minutesOfDay: Int): LocalDateTime {
        val atTime = now.toLocalDate().atTime(minutesOfDay / 60, minutesOfDay % 60)
        val candidate = atTime.with(TemporalAdjusters.nextOrSame(day))
        return if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
    }

    internal fun show(context: Context, posts: List<SavedPost>) {
        if (!canNotify(context)) return
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val recent = posts.filter { it.createdAt >= weekAgo }

        val (title, body, postId) = when {
            recent.isNotEmpty() -> Triple(
                context.getString(R.string.reminder_week_title, recent.size),
                context.getString(
                    R.string.reminder_week_body,
                    recent.take(2).joinToString(", ") { "“${it.displayTitle(context)}”" },
                ),
                recent.first().id,
            )
            posts.isNotEmpty() -> {
                val pick = posts.filter { it.isFavorite }.randomOrNull() ?: posts.random()
                Triple(context.getString(R.string.reminder_resurface_title), "“${pick.displayTitle(context)}”", pick.id)
            }
            else -> Triple(
                context.getString(R.string.reminder_empty_title),
                context.getString(R.string.reminder_empty_body),
                null,
            )
        }

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (postId != null) putExtra(MainActivity.EXTRA_POST_ID, postId) }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        ReminderScheduler.show(applicationContext, container.repository.allPosts())
        ReminderScheduler.reschedule(applicationContext, container.settings, fromWorker = true)
        return Result.success()
    }
}
