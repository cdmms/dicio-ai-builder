package com.memeable.dicioai.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AiScheduledTask(
    val id: Long,
    val whenMs: Long,
    val repeatMs: Long = 0L,
    val request: String,
    val createdAt: Long = System.currentTimeMillis(),
)

class AiScheduleStore(context: Context) {
    private val prefs = context.getSharedPreferences("dicio_ai_schedule", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = "tasks"

    @Synchronized fun list(): List<AiScheduledTask> = runCatching {
        json.decodeFromString<List<AiScheduledTask>>(prefs.getString(key, "[]") ?: "[]").sortedBy { it.whenMs }
    }.getOrDefault(emptyList())

    @Synchronized fun save(task: AiScheduledTask) {
        val next = (list().filterNot { it.id == task.id } + task).sortedBy { it.whenMs }.take(100)
        prefs.edit().putString(key, json.encodeToString(next)).apply()
    }

    @Synchronized fun remove(id: Long): Boolean {
        val before = list(); val after = before.filterNot { it.id == id }
        prefs.edit().putString(key, json.encodeToString(after)).apply()
        return before.size != after.size
    }

    @Synchronized fun find(id: Long): AiScheduledTask? = list().firstOrNull { it.id == id }
}
