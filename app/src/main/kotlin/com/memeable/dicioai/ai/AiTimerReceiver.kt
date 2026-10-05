package com.memeable.dicioai.ai

import org.stypox.dicio.R

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AiTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val channelId = "dicio_ai_timers"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(channelId, "Dicio AI timers", NotificationManager.IMPORTANCE_HIGH))
        val launch = PendingIntent.getActivity(context, 10, Intent(context, AiAgentActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) android.app.Notification.Builder(context, channelId) else @Suppress("DEPRECATION") android.app.Notification.Builder(context)
        manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Dicio AI").setContentText("Your timer is done.").setContentIntent(launch).setAutoCancel(true).build())
    }
}
