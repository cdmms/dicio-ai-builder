package com.memeable.dicioai.ai

import android.content.Context

/**
 * One engine instance per app process.
 * The foreground service and launcher activity therefore observe the same
 * in-memory task while the persistent task store remains the source of truth
 * across process restarts.
 */
object AiAgentRuntime {
    @Volatile
    private var instance: AiAgentEngine? = null

    fun get(context: Context): AiAgentEngine {
        val existing = instance
        if (existing != null) return existing

        return synchronized(this) {
            instance ?: AiAgentEngine(context.applicationContext).also { instance = it }
        }
    }
}
