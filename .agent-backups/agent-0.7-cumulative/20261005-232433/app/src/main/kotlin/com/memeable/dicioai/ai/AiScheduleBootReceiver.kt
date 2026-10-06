package com.memeable.dicioai.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AiScheduleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val now = System.currentTimeMillis()
        val store = AiScheduleStore(context)

        store.list().forEach { task ->
            when {
                task.whenMs >= now -> AiScheduledTaskReceiver.schedule(context, task)
                task.repeatMs > 0L -> {
                    val next = task.copy(whenMs = now + task.repeatMs)
                    store.save(next)
                    AiScheduledTaskReceiver.schedule(context, next)
                }
                else -> store.remove(task.id)
            }
        }
    }
}
