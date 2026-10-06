package com.memeable.dicioai.ai

data class AiSkillInfo(val id: String, val name: String, val purpose: String)

object AiSkillRegistry {
    val skills = listOf(
        AiSkillInfo("android_ui", "Android UI", "Inspect and control the visible Android interface."),
        AiSkillInfo("computer_use", "Computer Use", "Observe, act, wait and verify across Android apps."),
        AiSkillInfo("apps", "Apps", "Launch apps and identify the foreground app."),
        AiSkillInfo("web", "Web", "Search the public web and open web URLs."),
        AiSkillInfo("notifications", "Notifications", "Inspect, open and dismiss Android notifications."),
        AiSkillInfo("files", "Files", "Work with Dicio-managed files and selected documents."),
        AiSkillInfo("memory", "Memory", "Store, recall and forget durable user context."),
        AiSkillInfo("tasks", "Tasks", "Checkpoint, resume and inspect agent tasks."),
        AiSkillInfo("scheduling", "Scheduling", "Schedule one-shot and recurring agent requests."),
        AiSkillInfo("device", "Device", "Inspect safe device state."),
        AiSkillInfo("voice", "Voice", "Launch voice input into the agent."),
    )

    fun summary(): String = skills.joinToString("\n") { "${it.id}: ${it.name} — ${it.purpose}" }
}
