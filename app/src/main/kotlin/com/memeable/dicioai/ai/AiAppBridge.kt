package com.memeable.dicioai.ai

import android.content.Context

object AiAppBridge {
    data class ForegroundInfo(
        val packageName: String,
        val activityName: String,
        val appName: String,
    )

    fun foreground(): ForegroundInfo? {
        val servicePackage = runCatching {
            AgentAccessibilityService.currentPackageName()
        }.getOrNull()

        val serviceActivity = runCatching {
            AgentAccessibilityService.currentActivityName()
        }.getOrNull()

        val packageName = servicePackage?.takeIf { it.isNotBlank() } ?: return null
        val activityName = serviceActivity.orEmpty()
        return ForegroundInfo(packageName, activityName, packageName)
    }

    fun foreground(context: Context): ForegroundInfo? {
        val base = foreground() ?: return null
        val appName = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(base.packageName, 0)
            ).toString()
        }.getOrDefault(base.packageName)
        return base.copy(appName = appName)
    }

    fun describe(context: Context): String {
        val info = foreground(context) ?: return "No foreground app is available through the accessibility service."
        return "package=${info.packageName} app=${info.appName} activity=${info.activityName.ifBlank { "unknown" }}"
    }

    fun isAppOpen(context: Context, name: String): Boolean {
        if (name.isBlank()) return false
        val current = foreground(context) ?: return false
        return current.packageName.equals(name, true) || current.appName.equals(name, true) ||
            current.appName.contains(name, true)
    }
}
