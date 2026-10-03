package com.memeable.dicioai.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(engine: AiAgentEngine, onBack: () -> Unit, onVoice: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    val messages by engine.messages
    val events by engine.events
    val busy by engine.busy
    val pending by engine.pendingConfirmation
    val listState = rememberLazyListState()
    val resumableTask = remember { engine.taskSnapshot().firstOrNull { it.status == "RUNNING" || it.status == "WAITING_CONFIRMATION" } }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text("Dicio AI", fontWeight = FontWeight.Bold); Text(if (engine.isConfigured) "agent ready" else "setup needed", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { showClear = true }) { Icon(Icons.Default.Delete, "Clear chat") }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "AI settings") }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (!engine.isConfigured) {
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Free-first setup", fontWeight = FontWeight.Bold)
                        Text("Use OpenRouter free models, Ollama on your network, or any OpenAI-compatible endpoint.", modifier = Modifier.padding(top = 6.dp))
                        Button(onClick = { showSettings = true }, modifier = Modifier.padding(top = 10.dp)) { Text("Configure") }
                    }
                }
            }

            resumableTask?.let { task ->
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Interrupted task", fontWeight = FontWeight.Bold)
                            Text(task.request, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { engine.resumeLastInterruptedTask() }) { Text("Resume") }
                    }
                }
            }

            if (events.isNotEmpty()) {
                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Agent activity", fontWeight = FontWeight.Bold)
                        events.takeLast(5).forEach { Text("${statusIcon(it.status)} ${it.tool}: ${it.detail}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp)) {
                items(messages, key = { it.id }) { MessageBubble(it) }
                if (busy) item { Text("Dicio AI is working…", style = MaterialTheme.typography.labelMedium) }
            }

            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                IconButton(onClick = onVoice, enabled = !busy) { Icon(Icons.Default.Mic, "Voice") }
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask Dicio to do something…") }, maxLines = 5, enabled = !busy)
                IconButton(onClick = { val text = input.trim(); if (text.isNotBlank()) { input = ""; engine.enqueue(text) } }, enabled = !busy && input.isNotBlank()) { Icon(Icons.Default.Send, "Send") }
            }
        }
    }

    pending?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { engine.confirmPending(false) },
            title = { Text("Confirm action") },
            text = { Text("Dicio AI wants to run ${confirmation.tool}:\n\n${confirmation.summary}") },
            confirmButton = { Button(onClick = { engine.confirmPending(true) }) { Text("Approve") } },
            dismissButton = { TextButton(onClick = { engine.confirmPending(false) }) { Text("Cancel") } },
        )
    }

    if (showSettings) AiSettingsDialog(engine, { showSettings = false })
    if (showClear) AlertDialog(onDismissRequest = { showClear = false }, title = { Text("Clear conversation?") }, text = { Text("This removes the saved Dicio AI chat history on this phone.") }, confirmButton = { TextButton(onClick = { engine.clearHistory(); showClear = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancel") } })
}

private fun statusIcon(status: String) = when (status) { "completed" -> "✓"; "requested" -> "→"; else -> "•" }

@Composable
private fun MessageBubble(message: AiUiMessage) {
    val isUser = message.role == "user"
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = if (isUser) 1.dp else 3.dp, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp)) {
            Text(if (isUser) "You" else "Dicio AI", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(message.content, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AiSettingsDialog(engine: AiAgentEngine, onDismiss: () -> Unit) {
    var endpoint by remember(engine.configRevision) { mutableStateOf(engine.endpoint) }
    var model by remember(engine.configRevision) { mutableStateOf(engine.model) }
    var apiKey by remember(engine.configRevision) { mutableStateOf(engine.apiKey) }
    var systemPrompt by remember(engine.configRevision) { mutableStateOf(engine.systemPrompt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI provider") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("OpenRouter free router or any OpenAI-compatible endpoint. UI control uses Android Accessibility.")
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Base URL") }, singleLine = true)
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
            OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key (encrypted on device)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(systemPrompt, { systemPrompt = it }, label = { Text("Agent instructions") }, minLines = 4)
            TextButton(onClick = { engine.openAccessibilitySettings() }) { Text("Enable phone-control agent") }
        } },
        confirmButton = { TextButton(onClick = { engine.saveConfig(endpoint.trim(), model.trim(), apiKey.trim(), systemPrompt.trim()); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
