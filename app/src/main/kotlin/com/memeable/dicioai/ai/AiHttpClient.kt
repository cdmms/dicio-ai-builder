package com.memeable.dicioai.ai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AiHttpClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = okhttp3.OkHttpClient.Builder().callTimeout(45, java.util.concurrent.TimeUnit.SECONDS).build()

    fun chat(endpoint: String, apiKey: String, request: AiChatRequest): AiChatResponse = kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val base = endpoint.trimEnd('/')
            val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            val body = json.encodeToString(request).toRequestBody("application/json".toMediaType())
            val builder = Request.Builder().url(url).post(body).header("Content-Type", "application/json").header("X-Title", "Dicio AI")
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("AI provider returned HTTP ${response.code}: ${text.take(800).ifBlank { response.message }}")
                json.decodeFromString<AiChatResponse>(text)
            }
        }
    }

    fun vision(endpoint: String, apiKey: String, model: String, prompt: String, imageDataUrl: String): String = kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val base = endpoint.trimEnd('/')
            val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            val imagePart = buildJsonObject {
                put("type", "image_url")
                putJsonObject("image_url") { put("url", imageDataUrl) }
            }
            val textPart = buildJsonObject {
                put("type", "text")
                put("text", prompt)
            }
            val userMessage = buildJsonObject {
                put("role", "user")
                putJsonArray("content") { add(textPart); add(imagePart) }
            }
            val systemMessage = buildJsonObject {
                put("role", "system")
                put("content", "You analyze Android UI screenshots for a phone agent. Be factual and concise. Do not invent text or controls that are not visible.")
            }
            val request = buildJsonObject {
                put("model", model)
                putJsonArray("messages") { add(systemMessage); add(userMessage) }
                put("temperature", 0.1)
            }
            val body = json.encodeToString(JsonElement.serializer(), request).toRequestBody("application/json".toMediaType())
            val builder = Request.Builder().url(url).post(body).header("Content-Type", "application/json").header("X-Title", "Dicio AI")
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Vision provider returned HTTP ${response.code}: ${text.take(800).ifBlank { response.message }}")
                val obj = json.parseToJsonElement(text).jsonObject
                obj["choices"]?.let { choices ->
                    choices.jsonArray.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                }?.trim()?.ifBlank { "Vision model returned no text." } ?: "Vision model returned no text."
            }
        }
    }
}
