package com.memeable.dicioai.ai

import android.content.Context

data class AiExecutionLog(val timestamp: Long, val tool: String, val status: String, val detail: String)

class AiExecutionLogStore(context: Context) {
    private val prefs = context.getSharedPreferences("dicio_ai_execution_history", Context.MODE_PRIVATE)
    private val key = "events"

    @Synchronized fun add(tool: String, status: String, detail: String) {
        val entry = listOf(System.currentTimeMillis().toString(), tool.replace('|', ' '), status.replace('|', ' '), detail.replace('|', ' ').take(700)).joinToString("|")
        val values = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        values += entry
        prefs.edit().putStringSet(key, values.sortedByDescending { it.substringBefore('|').toLongOrNull() ?: 0L }.take(300).toSet()).apply()
    }

    @Synchronized fun list(limit: Int = 50): List<AiExecutionLog> = prefs.getStringSet(key, emptySet()).orEmpty()
        .sortedByDescending { it.substringBefore('|').toLongOrNull() ?: 0L }
        .take(limit.coerceIn(1, 300))
        .mapNotNull { raw ->
            val parts = raw.split('|', limit = 4)
            if (parts.size == 4) AiExecutionLog(parts[0].toLongOrNull() ?: 0L, parts[1], parts[2], parts[3]) else null
        }

    fun clear() { prefs.edit().remove(key).apply() }
}
