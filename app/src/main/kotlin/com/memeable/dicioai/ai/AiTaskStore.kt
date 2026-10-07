package com.memeable.dicioai.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AiTaskTimelineEntry(
    val id: Long,
    val timestamp: Long,
    val tool: String,
    val status: String,
    val detail: String,
    val step: Int = 0,
)

@Serializable
data class AiTaskCheckpoint(
    val id: String,
    val request: String,
    val status: String,
    val step: Int = 0,
    val lastTool: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val conversationJson: String? = null,
    val pendingCallId: String? = null,
    val pendingTool: String? = null,
    val pendingArguments: String? = null,
    val pendingSummary: String? = null,
    val timeline: List<AiTaskTimelineEntry> = emptyList(),
)

class AiTaskStore(context: Context) {
    private val prefs = context.getSharedPreferences("dicio_ai_tasks", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun list(): List<AiTaskCheckpoint> = runCatching {
        json.decodeFromString<List<AiTaskCheckpoint>>(
            prefs.getString(KEY, "[]") ?: "[]"
        ).sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    @Synchronized
    fun find(id: String): AiTaskCheckpoint? = list().firstOrNull { it.id == id }

    @Synchronized
    fun save(task: AiTaskCheckpoint) {
        val previous = list().firstOrNull { it.id == task.id }
        // Older Agent 0.8 call sites update checkpoints without carrying the
        // timeline field. Preserve an existing timeline instead of erasing it.
        val merged = if (
            previous != null &&
            task.timeline.isEmpty() &&
            previous.timeline.isNotEmpty()
        ) {
            task.copy(timeline = previous.timeline)
        } else {
            task
        }

        val current = (
            list().filterNot { it.id == merged.id } + merged
        ).sortedByDescending { it.updatedAt }.take(MAX_TASKS)
        prefs.edit().putString(KEY, json.encodeToString(current)).apply()
    }

    @Synchronized
    fun remove(id: String) {
        prefs.edit().putString(KEY, json.encodeToString(list().filterNot { it.id == id })).apply()
    }

    companion object {
        private const val KEY = "tasks"
        private const val MAX_TASKS = 40
        const val MAX_TIMELINE_ENTRIES = 120
    }
}
