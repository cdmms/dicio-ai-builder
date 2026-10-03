package com.memeable.dicioai.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AiMemory(
    val id: Long,
    val text: String,
    val createdAt: Long,
)

class AiMemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("dicio_ai_memory", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun list(): List<AiMemory> = runCatching {
        json.decodeFromString<List<AiMemory>>(prefs.getString(KEY, "[]") ?: "[]")
            .sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    @Synchronized
    fun remember(text: String): AiMemory {
        val clean = text.trim().take(MAX_MEMORY_LENGTH)
        require(clean.isNotBlank()) { "Memory text cannot be empty." }
        val nextId = (list().maxOfOrNull { it.id } ?: 0L) + 1L
        val item = AiMemory(nextId, clean, System.currentTimeMillis())
        write((list() + item).sortedBy { it.createdAt }.takeLast(MAX_MEMORIES))
        return item
    }

    @Synchronized
    fun forget(id: Long): Boolean {
        val current = list()
        val filtered = current.filterNot { it.id == id }
        if (filtered.size == current.size) return false
        write(filtered)
        return true
    }

    private fun write(items: List<AiMemory>) {
        prefs.edit().putString(KEY, json.encodeToString(items)).apply()
    }

    companion object {
        private const val KEY = "memories"
        private const val MAX_MEMORIES = 100
        private const val MAX_MEMORY_LENGTH = 500
    }
}
