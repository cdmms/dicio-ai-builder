package com.memeable.dicioai.ai

import android.service.notification.StatusBarNotification
import org.stypox.dicio.skills.notify.NotifyHandler

object AiNotificationBridge {
    data class Item(
        val key: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val message: String,
        val whenMs: Long,
    )

    fun status(): String = if (NotifyHandler.Instance != null) {
        "Notification access is enabled and connected."
    } else {
        "Notification access is not enabled."
    }

    fun list(): List<Item> {
        val service = NotifyHandler.Instance ?: return emptyList()
        return runCatching {
            service.activeNotifications.orEmpty().mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence("android.title")?.toString().orEmpty()
                val message = extras.getCharSequence("android.text")?.toString().orEmpty()
                if (title.isBlank() && message.isBlank()) return@mapNotNull null

                val appName = runCatching {
                    service.packageManager.getApplicationLabel(
                        service.packageManager.getApplicationInfo(sbn.packageName, 0)
                    ).toString()
                }.getOrDefault(sbn.packageName)

                Item(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appName = appName,
                    title = title,
                    message = message,
                    whenMs = sbn.postTime,
                )
            }.sortedByDescending { it.whenMs }
        }.getOrDefault(emptyList())
    }

    fun listText(limit: Int = 30): String {
        return list().take(limit.coerceIn(1, 100)).mapIndexed { index, item ->
            "[$index] key=${item.key} app=${item.appName} title=${item.title} message=${item.message} when=${item.whenMs}"
        }.joinToString("\n").ifBlank { "No active notifications found." }
    }

    fun open(keyOrIndex: String): String {
        val service = NotifyHandler.Instance ?: return "Notification access is not enabled."
        val item = resolve(keyOrIndex) ?: return "No active notification matched '$keyOrIndex'."
        val sbn = service.activeNotifications.orEmpty().firstOrNull { it.key == item.key }
            ?: return "The notification is no longer active."

        return runCatching {
            sbn.notification.contentIntent?.send()
                ?: return "That notification has no open action."
            "Opened notification from ${item.appName}."
        }.getOrElse { "Could not open notification: ${it.message ?: "unknown error"}" }
    }

    fun dismiss(keyOrIndex: String): String {
        val service = NotifyHandler.Instance ?: return "Notification access is not enabled."
        val item = resolve(keyOrIndex) ?: return "No active notification matched '$keyOrIndex'."

        return runCatching {
            service.cancelNotification(item.key)
            "Dismissed notification from ${item.appName}."
        }.getOrElse { "Could not dismiss notification: ${it.message ?: "unknown error"}" }
    }

    private fun resolve(keyOrIndex: String): Item? {
        val all = list()
        val index = keyOrIndex.toIntOrNull()
        if (index != null) return all.getOrNull(index)
        return all.firstOrNull { it.key == keyOrIndex }
    }
}
