package com.memeable.dicioai.ai

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object AiAgentNotifications {
    private const val RUNNING_CHANNEL = "dicio_ai_agent_running"
    private const val ALERT_CHANNEL = "dicio_ai_agent_alerts"
    private const val RUNNING_ID = 8701

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = manager(context)
        manager.createNotificationChannel(
            NotificationChannel(
                RUNNING_CHANNEL,
                "Dicio AI agent activity",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL,
                "Dicio AI agent alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun canPostAlerts(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAgent(context: Context): PendingIntent {
        val intent = Intent(context, AiAgentActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        return PendingIntent.getActivity(
            context,
            8702,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun foregroundNotification(context: Context): Notification {
        ensureChannels(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, RUNNING_CHANNEL)
        } else {
            Notification.Builder(context)
        }

        val stopIntent = Intent(context, AiAgentForegroundService::class.java)
            .setAction(AiAgentForegroundService.ACTION_CANCEL)
        val stopPendingIntent = PendingIntent.getService(
            context,
            8703,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return builder
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Dicio AI is working")
            .setContentText("Your Android AI agent is running in the background.")
            .setContentIntent(openAgent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    fun showRunning(
        context: Context,
        task: AiTaskCheckpoint,
        detail: String
    ) {
        if (!canPostAlerts(context)) return
        ensureChannels(context)
        val step = task.step.coerceAtLeast(1)
        val lastTool = task.lastTool?.takeIf { it.isNotBlank() }
        val content = buildString {
            append("Step $step of ${AiAgentEngine.MAX_AGENT_ROUNDS}")
            if (lastTool != null) append(" • $lastTool")
            if (detail.isNotBlank()) append(" • ").append(detail.take(100))
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, RUNNING_CHANNEL)
        } else {
            Notification.Builder(context)
        }
        manager(context).notify(
            RUNNING_ID,
            builder
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Dicio AI working")
                .setContentText(content)
                .setContentIntent(openAgent(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    fun clearRunning(context: Context) {
        manager(context).cancel(RUNNING_ID)
    }

    fun showConfirmation(
        context: Context,
        task: AiTaskCheckpoint,
        summary: String
    ) {
        if (!canPostAlerts(context)) return
        ensureChannels(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ALERT_CHANNEL)
        } else {
            Notification.Builder(context)
        }
        val id = task.id.hashCode() and 0x7fffffff
        manager(context).notify(
            id,
            builder
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Dicio AI needs your approval")
                .setContentText(summary.take(180))
                .setStyle(Notification.BigTextStyle().bigText(summary))
                .setContentIntent(openAgent(context))
                .setAutoCancel(true)
                .build()
        )
        clearRunning(context)
    }

    fun showResult(
        context: Context,
        task: AiTaskCheckpoint,
        title: String,
        detail: String
    ) {
        if (!canPostAlerts(context)) return
        ensureChannels(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ALERT_CHANNEL)
        } else {
            Notification.Builder(context)
        }
        val id = (task.id.hashCode() xor 0x5f3759df) and 0x7fffffff
        manager(context).notify(
            id,
            builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(detail.take(180))
                .setStyle(Notification.BigTextStyle().bigText(detail))
                .setContentIntent(openAgent(context))
                .setAutoCancel(true)
                .build()
        )
        clearRunning(context)
    }
}
