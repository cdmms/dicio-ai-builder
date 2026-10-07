package com.memeable.dicioai.ai
import kotlinx.coroutines.delay

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
import androidx.compose.material.icons.filled.MicOff
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
    onVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onTaskCenter: () -> Unit,
    voiceState: AiVoiceState,
    voicePartialText: String,
    handsFree: Boolean,
    speakReplies: Boolean,
    onToggleHandsFree: () -> Unit,
    onToggleSpeech: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var persistedTasks by remember { mutableStateOf(engine.taskSnapshot()) }
    var foregroundServiceRunning by remember { mutableStateOf(false) }

    val messages by engine.messages
    val events by engine.events
    val busy by engine.busy
    val pending by engine.pendingConfirmation
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val providerReady = engine.apiKey.isNotBlank() ||
        engine.endpoint.contains("127.0.0.1", ignoreCase = true) ||
        engine.endpoint.contains("localhost", ignoreCase = true)

    val currentTask = persistedTasks.firstOrNull {
        it.status == "RUNNING"
    }
    val interruptedTask = persistedTasks.firstOrNull {
        it.status == "RUNNING" || it.status == "WAITING_CONFIRMATION"
    }
    val latestEvent = events.lastOrNull()

    LaunchedEffect(Unit) {
        while (true) {
            persistedTasks = engine.taskSnapshot()
            foregroundServiceRunning = AiAgentForegroundService.isRunning()
            delay(750L)
        }
    }

    LaunchedEffect(messages.size) {
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
                        AgentStatusPill(
                            providerReady = providerReady,
                            busy = busy,
                            pending = pending != null,
                            serviceRunning = foregroundServiceRunning,
                            voiceState = voiceState
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
                    TextButton(onClick = onTaskCenter) {
                        Text("Tasks")
                    }
                    TextButton(onClick = onBack) {
                        Text("Classic")
                    }
                    IconButton(onClick = { showClear = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear chat"
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
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
                if (messages.isEmpty()) {
                    item {
                        AgentWelcomeCard(
                            providerReady = providerReady,
                            onSetup = { showSettings = true }
                        )
                    }

                    item {
                        VoiceControlCard(
                            voiceState = voiceState,
                            partialText = voicePartialText,
                            handsFree = handsFree,
                            speakReplies = speakReplies,
                            onVoice = onVoice,
                            onStopVoice = onStopVoice,
                            onToggleHandsFree = onToggleHandsFree,
                            onToggleSpeech = onToggleSpeech
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
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        )
                    }
                } else {
                    item {
                        LiveTaskCard(
                            providerReady = providerReady,
                            busy = busy,
                            task = currentTask,
                            pending = pending,
                            latestEvent = latestEvent,
                            serviceRunning = foregroundServiceRunning,
                            onSetup = { showSettings = true },
                            onStop = { engine.cancelCurrentTask() }
                        )
                    }
                }

                if (messages.isNotEmpty() && (voiceState != AiVoiceState.IDLE || handsFree)) {
                    item {
                        VoiceControlCard(
                            voiceState = voiceState,
                            partialText = voicePartialText,
                            handsFree = handsFree,
                            speakReplies = speakReplies,
                            onVoice = onVoice,
                            onStopVoice = onStopVoice,
                            onToggleHandsFree = onToggleHandsFree,
                            onToggleSpeech = onToggleSpeech
                        )
                    }
                }

                if (!providerReady) {
                    item {
                        SetupCard(
                            onConfigure = { showSettings = true },
                            onAccessibility = { engine.openAccessibilitySettings() }
                        )
                    }
                }

                interruptedTask?.let { task ->
                    if (!busy) {
                        item {
                            RecoveryCard(
                                task = task,
                                onResume = { engine.resumeLastInterruptedTask() }
                            )
                        }
                    }
                }

                if (events.isNotEmpty() && busy) {
                    item {
                        CompactActivityCard(events = events)
                    }
                }

                items(
                    messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(message)
                }
            }

            AgentInputBar(
                input = input,
                busy = busy,
                voiceState = voiceState,
                handsFree = handsFree,
                speakReplies = speakReplies,
                onInputChanged = { input = it },
                onVoice = {
                    if (voiceState == AiVoiceState.LISTENING) onStopVoice() else onVoice()
                },
                onToggleHandsFree = onToggleHandsFree,
                onToggleSpeech = onToggleSpeech,
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

    pending?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { engine.confirmPending(false) },
            title = { Text("Approval needed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Dicio AI paused the task before a consequential action."
                    )
                    Text(
                        confirmation.summary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { engine.confirmPending(true) }) {
                    Text("Approve")
                }
            },
            dismissButton = {
                TextButton(onClick = { engine.confirmPending(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettings) {
        AiSettingsDialog(
            engine = engine,
            onDismiss = { showSettings = false }
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("Clear conversation?") },
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
                TextButton(onClick = { showClear = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AgentStatusPill(
    providerReady: Boolean,
    busy: Boolean,
    pending: Boolean,
    serviceRunning: Boolean,
    voiceState: AiVoiceState
) {
    val label = when {
        voiceState == AiVoiceState.LISTENING -> "listening"
        voiceState == AiVoiceState.PROCESSING -> "processing voice"
        voiceState == AiVoiceState.SPEAKING -> "speaking"
        voiceState == AiVoiceState.ERROR -> "voice issue"
        pending -> "needs approval"
        busy && serviceRunning -> "working in background"
        busy -> "working"
        providerReady -> "online"
        else -> "setup needed"
    }

    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun LiveTaskCard(
    providerReady: Boolean,
    busy: Boolean,
    task: AiTaskCheckpoint?,
    pending: PendingConfirmation?,
    latestEvent: AiAgentEvent?,
    serviceRunning: Boolean,
    onSetup: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        tonalElevation = if (busy) 4.dp else 2.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            pending != null -> "Approval needed"
                            busy -> "Agent is working"
                            providerReady -> "Agent ready"
                            else -> "Finish setup"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            pending != null -> "Task paused until you approve the next action."
                            busy && serviceRunning -> "Running in the background."
                            busy -> "Watching the task and verifying actions."
                            providerReady -> "Ready for your next task."
                            else -> "Connect an AI provider to start."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                if (busy) {
                    TextButton(onClick = onStop) {
                        Text("Stop")
                    }
                } else if (!providerReady) {
                    TextButton(onClick = onSetup) {
                        Text("Setup")
                    }
                }
            }

            task?.let {
                Spacer(Modifier.height(12.dp))

                Text(
                    it.request,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(10.dp))

                val step = it.step.coerceAtLeast(1)
                Text(
                    "Step $step / ${AiAgentEngine.MAX_AGENT_ROUNDS}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(6.dp))

                androidx.compose.material3.LinearProgressIndicator(
                    progress = {
                        (step.toFloat() / AiAgentEngine.MAX_AGENT_ROUNDS)
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                it.lastTool?.takeIf { value -> value.isNotBlank() }?.let { tool ->
                    Text(
                        "Last action: $tool",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            latestEvent?.let { event ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            statusIcon(event.status),
                            fontWeight = FontWeight.Bold
                        )
                        Column(
                            modifier = Modifier.padding(start = 8.dp)
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
private fun RecoveryCard(
    task: AiTaskCheckpoint,
    onResume: () -> Unit
) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (task.status == "WAITING_CONFIRMATION") {
                        "Waiting for approval"
                    } else {
                        "Resume your task"
                    },
                    fontWeight = FontWeight.Bold
                )
                Text(
                    task.request,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "Step ${task.step.coerceAtLeast(1)} / ${AiAgentEngine.MAX_AGENT_ROUNDS}" +
                        (task.lastTool?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            TextButton(onClick = onResume) {
                Text("Resume")
            }
        }
    }
}

@Composable
private fun CompactActivityCard(
    events: List<AiAgentEvent>
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Live activity",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))

            events.takeLast(3).forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        statusIcon(event.status),
                        modifier = Modifier.widthIn(min = 18.dp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${event.tool}: ${event.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }
        }
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
        Column(modifier = Modifier.padding(22.dp)) {
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
                "Ask Dicio to use your phone for you — search, open apps, read " +
                    "the screen, tap, type, remember things, manage files, and " +
                    "complete multi-step tasks.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    if (providerReady) "●  online" else "○  setup needed",
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
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Quick actions", fontWeight = FontWeight.Bold)
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
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Phone-control setup", fontWeight = FontWeight.Bold)
            Text(
                "Enable Accessibility so Dicio can inspect and interact with other apps.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                FilledTonalButton(onClick = onAccessibility) {
                    Text("Enable control")
                }
                TextButton(onClick = onConfigure) {
                    Text("Provider settings")
                }
            }
        }
    }
}

@Composable
private fun AgentInputBar(
    input: String,
    busy: Boolean,
    voiceState: AiVoiceState,
    handsFree: Boolean,
    speakReplies: Boolean,
    onInputChanged: (String) -> Unit,
    onVoice: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onToggleSpeech: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onVoice, enabled = !busy) {
                    Icon(
                        if (voiceState == AiVoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (voiceState == AiVoiceState.LISTENING) "Stop listening" else "Start voice input"
                    )
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChanged,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    placeholder = {
                        Text(
                            when (voiceState) {
                                AiVoiceState.LISTENING -> "Listening…"
                                AiVoiceState.SPEAKING -> "Dicio is speaking…"
                                else -> "Ask Dicio to do something…"
                            }
                        )
                    },
                    maxLines = 5,
                    enabled = !busy
                )

                IconButton(onClick = onSend, enabled = !busy && input.isNotBlank()) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleHandsFree) {
                    Text(if (handsFree) "Hands-free on" else "Hands-free")
                }
                TextButton(onClick = onToggleSpeech) {
                    Text(if (speakReplies) "Speak replies on" else "Speak replies")
                }
                if (voiceState == AiVoiceState.LISTENING) {
                    Text(
                        "Listening…",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceControlCard(
    voiceState: AiVoiceState,
    partialText: String,
    handsFree: Boolean,
    speakReplies: Boolean,
    onVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onToggleSpeech: () -> Unit
) {
    Surface(
        tonalElevation = if (voiceState == AiVoiceState.LISTENING || voiceState == AiVoiceState.SPEAKING) 5.dp else 3.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) {
                    IconButton(
                        onClick = {
                            if (voiceState == AiVoiceState.LISTENING || voiceState == AiVoiceState.SPEAKING) onStopVoice()
                            else onVoice()
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (voiceState == AiVoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice control"
                        )
                    }
                }

                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        when (voiceState) {
                            AiVoiceState.LISTENING -> "Listening"
                            AiVoiceState.PROCESSING -> "Processing"
                            AiVoiceState.SPEAKING -> "Speaking"
                            AiVoiceState.ERROR -> "Voice needs attention"
                            AiVoiceState.IDLE -> if (handsFree) "Hands-free ready" else "Talk to Dicio"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when (voiceState) {
                            AiVoiceState.LISTENING -> if (partialText.isBlank()) "Tell me what you want done." else partialText
                            AiVoiceState.PROCESSING -> "Turning your request into an agent task…"
                            AiVoiceState.SPEAKING -> "Dicio is reading the latest response."
                            AiVoiceState.ERROR -> "Tap the microphone to try again."
                            AiVoiceState.IDLE -> if (handsFree) "I’ll listen again after each response." else "Speak a task instead of typing it."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleHandsFree) {
                    Text(if (handsFree) "Turn hands-free off" else "Turn hands-free on")
                }
                TextButton(onClick = onToggleSpeech) {
                    Text(if (speakReplies) "Mute replies" else "Speak replies")
                }
            }
        }
    }
}

private fun statusIcon(status: String) = when (status) {
    "completed" -> "✓"
    "requested" -> "→"
    "approved" -> "✓"
    "recovery" -> "↻"
    else -> "•"
}

@Composable
private fun MessageBubble(message: AiUiMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            tonalElevation = if (isUser) 1.dp else 3.dp,
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(12.dp)) {
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
