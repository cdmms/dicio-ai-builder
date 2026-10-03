package org.stypox.dicio.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AiTaskCheckpoint(
    val id: String,
    val request: String,
    val status: String,
    val step: Int = 0,
    val lastTool: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

class AiTaskStore(context: Context) {
    private val prefs = context.getSharedPreferences("dicio_ai_tasks", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized fun list(): List<AiTaskCheckpoint> = runCatching {
        json.decodeFromString<List<AiTaskCheckpoint>>(prefs.getString(KEY, "[]") ?: "[]")
            .sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    @Synchronized fun save(task: AiTaskCheckpoint) {
        val current = (list().filterNot { it.id == task.id } + task).sortedByDescending { it.updatedAt }.take(MAX_TASKS)
        prefs.edit().putString(KEY, json.encodeToString(current)).apply()
    }

    @Synchronized fun remove(id: String) {
        prefs.edit().putString(KEY, json.encodeToString(list().filterNot { it.id == id })).apply()
    }

    companion object {
        private const val KEY = "tasks"
        private const val MAX_TASKS = 40
    }
}
