package com.memeable.dicioai.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AiScheduleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = AiScheduleStore(context)
        val now = System.currentTimeMillis()
        store.list().forEach { task ->
            if (task.whenMs >= now) {
                AiScheduledTaskReceiver.schedule(context, task)
            } else if (task.repeatMs > 0L) {
                val next = task.copy(whenMs = now + task.repeatMs)
                store.save(next)
                AiScheduledTaskReceiver.schedule(context, next)
            } else {
                val overdue = task.copy(whenMs = now + 1_000L)
                store.save(overdue)
                AiScheduledTaskReceiver.schedule(context, overdue)
            }
        }
    }
}
