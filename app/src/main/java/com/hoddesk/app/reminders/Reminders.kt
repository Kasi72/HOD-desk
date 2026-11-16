package com.hoddesk.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hoddesk.app.MainActivity
import com.hoddesk.app.R
import com.hoddesk.app.data.HodDeskDatabase
import com.hoddesk.app.settings.DeskSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Local-only reminders: a daily morning brief and "due soon" alerts for open tasks.
 * Uses inexact, battery-friendly alarms (no exact-alarm permission); nothing leaves the phone.
 * Notification text is hidden on the lock screen.
 */
object Reminders {
    private const val TAG = "HOD_REMINDERS"
    const val CHANNEL_ID = "hod_desk_reminders"
    const val ACTION_BRIEF = "com.hoddesk.app.action.REMINDER_BRIEF"
    const val ACTION_TASK = "com.hoddesk.app.action.REMINDER_TASK"
    const val ACTION_SNOOZE = "com.hoddesk.app.action.REMINDER_SNOOZE"
    private const val SNOOZE_NOTIFICATION_ID = 2
    const val EXTRA_TASK_ID = "taskId"
    const val EXTRA_DUE_AT = "dueAt"
    private const val BRIEF_NOTIFICATION_ID = 1
    private const val STATE_PREFS = "hod_desk_reminder_state"
    private const val KEY_TASK_IDS = "scheduled_task_ids"
    /** Tasks in these states never ring and are not counted in the brief. */
    private val CLOSED_TASK_STATES = setOf("completed", "deleted", "archived")

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Morning brief and tasks that are due soon"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
    }

    /** Re-creates every alarm from the current settings and tasks. Safe to call often. */
    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runCatching {
            scheduleBrief(app)
            scheduleTasks(app)
            scheduleSnooze(app)
        }.onFailure { Log.e(TAG, "reschedule_failed", it) }
    }

    fun scheduleBrief(context: Context) {
        val settings = DeskSettingsStore(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = briefIntent(context)
        if (settings.briefEnabled) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ReminderPlan.nextBrief(System.currentTimeMillis(), settings.briefMinute), pending)
        } else {
            alarms.cancel(pending)
        }
    }

    private suspend fun scheduleTasks(context: Context) {
        val settings = DeskSettingsStore(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        // Cancel everything set last time; the fresh plan below replaces it.
        state.getStringSet(KEY_TASK_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.forEach { id ->
            alarms.cancel(taskIntent(context, id, 0L))
        }
        val scheduled = mutableSetOf<String>()
        if (settings.dueRemindersEnabled) {
            val tasks = HodDeskDatabase.get(context).dao().allTasks()
                .filter { it.status !in CLOSED_TASK_STATES && it.dueAt != null }
                .map { ReminderPlan.TaskDue(it.id, it.dueAt!!) }
            ReminderPlan.taskAlarms(tasks, System.currentTimeMillis(), settings.dueLeadMinutes).forEach { alarm ->
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.fireAt, taskIntent(context, alarm.taskId, alarm.dueAt))
                scheduled += alarm.taskId.toString()
            }
        }
        state.edit().putStringSet(KEY_TASK_IDS, scheduled).apply()
    }

    /** One alarm for the earliest snoozed Review item; it says how many items are back (never their titles). */
    private suspend fun scheduleSnooze(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = snoozeIntent(context)
        val now = System.currentTimeMillis()
        val next = HodDeskDatabase.get(context).dao().allSources()
            .filter { !it.reviewed && it.lifecycleState != "deleted" }
            .mapNotNull { it.snoozedUntil }.filter { it > now }.minOrNull()
        if (next != null) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending) else alarms.cancel(pending)
    }

    private fun snoozeIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun postSnoozed(context: Context) {
        val now = System.currentTimeMillis()
        // Items whose snooze ended in the last 12 hours and that still wait for review.
        val back = HodDeskDatabase.get(context).dao().allSources().count {
            !it.reviewed && it.lifecycleState != "deleted" && it.snoozedUntil != null && it.snoozedUntil in (now - 12 * 60 * 60_000L)..(now + 60_000L)
        }
        if (back > 0) notify(context, SNOOZE_NOTIFICATION_ID, "Back in Review", if (back == 1) "1 snoozed item is ready for review." else "$back snoozed items are ready for review.")
    }

    private fun briefIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_BRIEF),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // The data URI makes each task's alarm distinct (PendingIntents match on action + data, not extras).
    private fun taskIntent(context: Context, taskId: Long, dueAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_TASK).setData(Uri.parse("hoddesk://task/$taskId"))
            .putExtra(EXTRA_TASK_ID, taskId).putExtra(EXTRA_DUE_AT, dueAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun postBrief(context: Context) {
        val dao = HodDeskDatabase.get(context).dao()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val open = dao.allTasks().filter { it.status !in CLOSED_TASK_STATES }
        val overdue = open.count { it.dueAt != null && it.dueAt < now }
        val dueToday = open.count { it.dueAt != null && it.dueAt in now until dayEnd }
        val toReview = dao.allSources().count { !it.reviewed && it.lifecycleState != "deleted" && it.sensitivity != "T3" && it.sensitivity != "T4" }
        val meetings = dao.eventsBetween(dayStart, dayEnd).size
        val parts = buildList {
            if (overdue > 0) add("$overdue overdue")
            if (dueToday > 0) add("$dueToday due today")
            if (toReview > 0) add("$toReview to review")
            if (meetings > 0) add("$meetings meeting${if (meetings == 1) "" else "s"}")
        }
        notify(context, BRIEF_NOTIFICATION_ID, "Today’s brief", parts.joinToString(" · ").ifBlank { "Nothing urgent on your desk today." })
    }

    internal suspend fun postTask(context: Context, taskId: Long, dueAt: Long) {
        val task = HodDeskDatabase.get(context).dao().task(taskId) ?: return
        // Skip if the task was completed or rescheduled after this alarm was set.
        if (task.status in CLOSED_TASK_STATES || task.dueAt != dueAt) return
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(dueAt))
        notify(context, 1_000 + (taskId % 100_000).toInt(), task.title, "Due at $time" + (task.assignee?.let { " · with $it" } ?: ""))
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        // Same intent as the launcher icon: brings the running app forward instead of starting a second copy.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hidden = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder).setContentTitle("HOD Desk").setContentText("You have a reminder").build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "notification_permission_missing")
        }
    }
}

/** Fires the brief or a task reminder, then arranges the next brief. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_BRIEF -> {
                        Reminders.postBrief(app)
                        Reminders.scheduleBrief(app)
                    }
                    Reminders.ACTION_SNOOZE -> {
                        Reminders.postSnoozed(app)
                        Reminders.rescheduleAll(app) // arranges the next snoozed item, if any
                    }
                    Reminders.ACTION_TASK -> {
                        val taskId = intent.getLongExtra(Reminders.EXTRA_TASK_ID, -1L)
                        val dueAt = intent.getLongExtra(Reminders.EXTRA_DUE_AT, -1L)
                        if (taskId > 0 && dueAt > 0) Reminders.postTask(app, taskId, dueAt)
                    }
                }
            } catch (error: Throwable) {
                Log.e("HOD_REMINDERS", "reminder_failed", error)
            } finally {
                result.finish()
            }
        }
    }
}

/** Alarms do not survive a reboot, an app update or a clock change; this puts them back. */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Reminders.rescheduleAll(app) } finally { result.finish() }
        }
    }
}
