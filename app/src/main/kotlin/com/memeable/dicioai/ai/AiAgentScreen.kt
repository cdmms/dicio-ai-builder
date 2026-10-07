package com.memeable.dicioai.ai

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(
    engine: AiAgentEngine,
    onBack: () -> Unit,
    onVoice: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    val messages by engine.messages
    val events by engine.events
    val busy by engine.busy
    val pending by engine.pendingConfirmation
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // UI-only readiness calculation.
    // Agent 0.7 engine/configuration logic remains untouched.
    val providerReady =
        engine.apiKey.isNotBlank() ||
            engine.endpoint.contains("127.0.0.1", ignoreCase = true) ||
            engine.endpoint.contains("localhost", ignoreCase = true)

    val resumableTask = engine.taskSnapshot()
        .firstOrNull {
            it.status == "RUNNING" ||
                it.status == "WAITING_CONFIRMATION"
        }

    LaunchedEffect(messages.size, events.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Dicio AI",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (providerReady) "agent online" else "finish setup",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Open classic Dicio"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onBack) {
                        Text("Classic")
                    }

                    IconButton(
                        onClick = { showClear = true }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear chat"
                        )
                    }

                    IconButton(
                        onClick = { showSettings = true }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "AI settings"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp
                )
            ) {
                /*
                 * FIRST-LAUNCH AGENT HOME
                 */
                if (messages.isEmpty()) {
                    item {
                        AgentWelcomeCard(
                            providerReady = providerReady,
                            onSetup = { showSettings = true }
                        )
                    }

                    item {
                        QuickActionsCard(
                            enabled = !busy && providerReady,
                            onReadScreen = {
                                engine.enqueue(
                                    "Read my screen and tell me what is currently visible."
                                )
                            },
                            onOpenSettings = {
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS)
                                )
                            }
                        )
                    }
                } else {
                    item {
                        AgentStatusCard(
                            ready = providerReady,
                            busy = busy,
                            onSetup = { showSettings = true },
                            onCancel = {
                                engine.cancelCurrentTask()
                            }
                        )
                    }
                }

                /*
                 * SETUP
                 */
                if (!providerReady) {
                    item {
                        SetupCard(
                            onConfigure = { showSettings = true },
                            onAccessibility = {
                                engine.openAccessibilitySettings()
                            }
                        )
                    }
                }

                /*
                 * INTERRUPTED TASK
                 */
                resumableTask?.let { task ->
                    item {
                        Surface(
                            tonalElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "Resume where you left off",
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(
                                        Modifier.height(4.dp)
                                    )

                                    Text(
                                        task.request,
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        engine.resumeLastInterruptedTask()
                                    }
                                ) {
                                    Text("Resume")
                                }
                            }
                        }
                    }
                }

                /*
                 * LIVE AGENT ACTIVITY
                 */
                if (events.isNotEmpty() || busy) {
                    item {
                        AgentActivityCard(
                            events = events,
                            busy = busy
                        )
                    }
                }

                /*
                 * CONVERSATION
                 */
                items(
                    messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(message)
                }

                if (busy) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )

                            Text(
                                "Dicio AI is working…",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            /*
             * ALWAYS-PRESENT AGENT INPUT
             */
            AgentInputBar(
                input = input,
                busy = busy,
                onInputChanged = { input = it },
                onVoice = onVoice,
                onSend = {
                    val text = input.trim()

                    if (text.isNotBlank()) {
                        input = ""
                        engine.enqueue(text)
                    }
                }
            )
        }
    }

    /*
     * CONSEQUENT ACTION CONFIRMATION
     */
    pending?.let { confirmation ->
        AlertDialog(
            onDismissRequest = {
                engine.confirmPending(false)
            },
            title = {
                Text("Confirm action")
            },
            text = {
                Text(
                    "Dicio AI wants to run ${confirmation.tool}:\n\n" +
                        confirmation.summary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        engine.confirmPending(true)
                    }
                ) {
                    Text("Approve")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        engine.confirmPending(false)
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettings) {
        AiSettingsDialog(
            engine = engine,
            onDismiss = {
                showSettings = false
            }
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = {
                showClear = false
            },
            title = {
                Text("Clear conversation?")
            },
            text = {
                Text(
                    "This removes the saved Dicio AI chat history on this phone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        engine.clearHistory()
                        showClear = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClear = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AgentWelcomeCard(
    providerReady: Boolean,
    onSetup: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(22.dp)
        ) {
            /*
             * Agent emblem
             */
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✦",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Your Android AI agent",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Ask Dicio to use your phone for you — search, open apps, " +
                    "read the screen, tap, type, remember things, manage files, " +
                    "and complete multi-step tasks.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    if (providerReady) {
                        "●  agent online"
                    } else {
                        "○  finish setup to start"
                    },
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!providerReady) {
                Spacer(Modifier.height(14.dp))

                Button(onClick = onSetup) {
                    Text("Finish setup")
                }
            }
        }
    }
}

@Composable
private fun AgentStatusCard(
    ready: Boolean,
    busy: Boolean,
    onSetup: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        tonalElevation = if (busy) 4.dp else 2.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (busy) {
                        "Agent is working"
                    } else {
                        "Agent ready"
                    },
                    fontWeight = FontWeight.Bold
                )

                Text(
                    when {
                        busy ->
                            "Watching tool results and verifying each action."

                        ready ->
                            "Ready for your next task."

                        else ->
                            "Connect a provider to run tasks."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            if (busy) {
                TextButton(onClick = onCancel) {
                    Text("Stop")
                }
            } else if (!ready) {
                TextButton(onClick = onSetup) {
                    Text("Setup")
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    enabled: Boolean,
    onReadScreen: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                "Quick actions",
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onReadScreen,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Read my screen")
                }

                FilledTonalButton(
                    onClick = onOpenSettings,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open Settings")
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    onConfigure: () -> Unit,
    onAccessibility: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                "Phone-control setup",
                fontWeight = FontWeight.Bold
            )

            Text(
                "Enable the agent's accessibility service so Dicio can inspect " +
                    "and interact with other apps.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                FilledTonalButton(
                    onClick = onAccessibility
                ) {
                    Text("Enable control")
                }

                TextButton(
                    onClick = onConfigure
                ) {
                    Text("Provider settings")
                }
            }
        }
    }
}

@Composable
private fun AgentActivityCard(
    events: List<AiAgentEvent>,
    busy: Boolean
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Live agent activity",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    if (busy) "LIVE" else "RECENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            if (events.isEmpty()) {
                Text(
                    "Preparing the next agent step…",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                events.takeLast(5).forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            statusIcon(event.status),
                            modifier = Modifier.widthIn(min = 20.dp),
                            fontWeight = FontWeight.Bold
                        )

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                event.tool,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                event.detail,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentInputBar(
    input: String,
    busy: Boolean,
    onInputChanged: (String) -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onVoice,
                enabled = !busy
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice input"
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                placeholder = {
                    Text("Ask Dicio to do something…")
                },
                maxLines = 5,
                enabled = !busy
            )

            IconButton(
                onClick = onSend,
                enabled = !busy && input.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}

private fun statusIcon(status: String) = when (status) {
    "completed" -> "✓"
    "requested" -> "→"
    "recovery" -> "↻"
    else -> "•"
}

@Composable
private fun MessageBubble(
    message: AiUiMessage
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            tonalElevation = if (isUser) 1.dp else 3.dp,
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                Modifier.padding(12.dp)
            ) {
                Text(
                    if (isUser) "You" else "Dicio AI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    message.content,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun AiSettingsDialog(
    engine: AiAgentEngine,
    onDismiss: () -> Unit
) {
    var endpoint by remember(engine.configRevision) {
        mutableStateOf(engine.endpoint)
    }

    var model by remember(engine.configRevision) {
        mutableStateOf(engine.model)
    }

    var apiKey by remember(engine.configRevision) {
        mutableStateOf(engine.apiKey)
    }

    var systemPrompt by remember(engine.configRevision) {
        mutableStateOf(engine.systemPrompt)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("AI provider")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "OpenRouter free router or any OpenAI-compatible endpoint. " +
                        "UI control uses Android Accessibility."
                )

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Base URL") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text("API key (encrypted on device)")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("Agent instructions") },
                    minLines = 4
                )

                TextButton(
                    onClick = {
                        engine.openAccessibilitySettings()
                    }
                ) {
                    Text("Enable phone-control agent")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    engine.saveConfig(
                        endpoint.trim(),
                        model.trim(),
                        apiKey.trim(),
                        systemPrompt.trim()
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
