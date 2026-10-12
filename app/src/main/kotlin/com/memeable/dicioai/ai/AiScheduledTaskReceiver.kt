package com.memeable.dicioai.ai

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class AiScheduledTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        if (id < 0L) return
        val store = AiScheduleStore(context)
        val task = store.find(id) ?: return
        if (task.repeatMs > 0L) {
            val next = task.copy(whenMs = System.currentTimeMillis() + task.repeatMs)
            store.save(next)
            schedule(context, next)
        } else {
            store.remove(id)
        }
        val started = AiAgentForegroundService.startTask(context, task.request)
        showNotification(context, task, started)
    }

    private fun showNotification(context: Context, task: AiScheduledTask, started: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Dicio AI scheduled agents", NotificationManager.IMPORTANCE_HIGH))
        }
        val runIntent = Intent(context, AiAgentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!started) {
            runIntent.putExtra(AiAgentActivity.EXTRA_RUN_TASK, task.request)
        }
        val pending = PendingIntent.getActivity(context, task.id.hashCode(), runIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context)
        manager.notify(task.id.hashCode(), builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(if (started) "Dicio AI scheduled task queued" else "Tap to run scheduled task")
                .setContentText((if (started) "Queued: " else "Ready: ") + task.request.take(120))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build())
    }

    companion object {
        const val EXTRA_ID = "dicio_ai_schedule_id"
        private const val CHANNEL_ID = "dicio_ai_scheduled"

        fun pendingIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
            context, id.hashCode(), Intent(context, AiScheduledTaskReceiver::class.java).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun schedule(context: Context, task: AiScheduledTask) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = pendingIntent(context, task.id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.whenMs, pending)
            else alarm.set(AlarmManager.RTC_WAKEUP, task.whenMs, pending)
        }

        fun cancel(context: Context, id: Long) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(pendingIntent(context, id))
        }
    }
}
