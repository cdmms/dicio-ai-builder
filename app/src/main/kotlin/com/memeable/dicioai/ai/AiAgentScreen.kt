package com.memeable.dicioai.ai

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay

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
    var showVoiceMenu by remember { mutableStateOf(false) }
    var persistedTasks by remember { mutableStateOf(engine.taskSnapshot()) }

    val messages by engine.messages
    val busy by engine.busy
    val pending by engine.pendingConfirmation
    val listState = rememberLazyListState()

    val providerReady = engine.apiKey.isNotBlank() ||
        engine.endpoint.contains("127.0.0.1", ignoreCase = true) ||
        engine.endpoint.contains("localhost", ignoreCase = true)

    LaunchedEffect(Unit) {
        while (true) {
            persistedTasks = engine.taskSnapshot()
            delay(1000L)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val currentTask = persistedTasks.firstOrNull {
        it.status == "RUNNING" || it.status == "WAITING_CONFIRMATION"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Open classic Dicio"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            "Dicio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        AgentHeaderStatus(
                            providerReady = providerReady,
                            busy = busy,
                            pending = pending != null,
                            handsFree = handsFree,
                            voiceState = voiceState
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onTaskCenter) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Tasks"
                        )
                    }
                    IconButton(onClick = { showClear = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear conversation"
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
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 18.dp,
                    bottom = 14.dp
                )
            ) {
                if (messages.isEmpty()) {
                    item {
                        AgentWelcome(
                            providerReady = providerReady,
                            onSetup = { showSettings = true }
                        )
                    }
                }

                if (currentTask != null) {
                    item {
                        AgentTaskBar(
                            task = currentTask,
                            busy = busy,
                            pending = pending != null,
                            onStop = { engine.cancelCurrentTask() }
                        )
                    }
                }

                if (voiceState != AiVoiceState.IDLE || handsFree) {
                    item {
                        VoiceStatusBar(
                            voiceState = voiceState,
                            partialText = voicePartialText,
                            handsFree = handsFree,
                            speakReplies = speakReplies,
                            onStop = onStopVoice
                        )
                    }
                }

                if (!providerReady && messages.isNotEmpty()) {
                    item {
                        SetupInlineCard(
                            onSetup = { showSettings = true },
                            onAccessibility = { engine.openAccessibilitySettings() }
                        )
                    }
                }

                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(message)
                }
            }

            AgentComposer(
                input = input,
                busy = busy,
                voiceState = voiceState,
                handsFree = handsFree,
                speakReplies = speakReplies,
                showVoiceMenu = showVoiceMenu,
                onInputChanged = { input = it },
                onVoice = {
                    if (voiceState == AiVoiceState.LISTENING) {
                        onStopVoice()
                    } else {
                        onVoice()
                    }
                },
                onToggleVoiceMenu = { showVoiceMenu = !showVoiceMenu },
                onDismissVoiceMenu = { showVoiceMenu = false },
                onToggleHandsFree = {
                    showVoiceMenu = false
                    onToggleHandsFree()
                },
                onToggleSpeech = {
                    showVoiceMenu = false
                    onToggleSpeech()
                },
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
            title = {
                Text("Approval needed")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Dicio paused before a consequential action.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        confirmation.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
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
            title = {
                Text("Clear conversation?")
            },
            text = {
                Text(
                    "This removes the saved Dicio chat history on this phone.",
                    style = MaterialTheme.typography.bodyMedium
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
private fun AgentHeaderStatus(
    providerReady: Boolean,
    busy: Boolean,
    pending: Boolean,
    handsFree: Boolean,
    voiceState: AiVoiceState
) {
    val status = when {
        pending -> "Needs approval"
        voiceState == AiVoiceState.LISTENING -> "Listening"
        voiceState == AiVoiceState.PROCESSING -> "Processing voice"
        voiceState == AiVoiceState.SPEAKING -> "Speaking"
        voiceState == AiVoiceState.ERROR -> "Voice issue"
        busy -> if (handsFree) "Working • hands-free" else "Working"
        providerReady -> "Ready"
        else -> "Setup needed"
    }

    Text(
        status,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AgentWelcome(
    providerReady: Boolean,
    onSetup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✦",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "What can I do for you?",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(10.dp))

        Text(
            "Give Dicio a goal. It can answer, search, operate apps, read your screen, and complete multi-step tasks.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.9f)
        )

        if (!providerReady) {
            Spacer(Modifier.height(18.dp))

            Text(
                "Connect an AI provider to start.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(10.dp))

            Button(onClick = onSetup) {
                Text("Open settings")
            }
        }
    }
}

@Composable
private fun AgentTaskBar(
    task: AiTaskCheckpoint,
    busy: Boolean,
    pending: Boolean,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (pending) 4.dp else 2.dp,
        color = if (pending) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (pending) "Needs your approval"
                        else if (busy) "Working on it"
                        else "Task",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        task.request,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (busy) {
                    TextButton(onClick = onStop) {
                        Text("Stop")
                    }
                }
            }

            val step = task.step.coerceAtLeast(1)
            Text(
                "Step $step of ${AiAgentEngine.MAX_AGENT_ROUNDS}",
                style = MaterialTheme.typography.labelMedium
            )
            LinearProgressIndicator(
                progress = {
                    (step.toFloat() / AiAgentEngine.MAX_AGENT_ROUNDS)
                        .coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VoiceStatusBar(
    voiceState: AiVoiceState,
    partialText: String,
    handsFree: Boolean,
    speakReplies: Boolean,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (voiceState == AiVoiceState.LISTENING) 3.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(
                start = 14.dp,
                top = 12.dp,
                end = 8.dp,
                bottom = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (voiceState) {
                    AiVoiceState.LISTENING -> Icons.Default.Mic
                    else -> Icons.Default.MicOff
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    when (voiceState) {
                        AiVoiceState.LISTENING -> "Listening"
                        AiVoiceState.PROCESSING -> "Processing"
                        AiVoiceState.SPEAKING -> "Speaking"
                        AiVoiceState.ERROR -> "Voice needs attention"
                        AiVoiceState.IDLE -> "Voice mode"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                val detail = when (voiceState) {
                    AiVoiceState.LISTENING -> partialText.ifBlank { "Say what you want done." }
                    AiVoiceState.PROCESSING -> "Turning your request into an agent task."
                    AiVoiceState.SPEAKING -> "Reading the latest response."
                    AiVoiceState.ERROR -> "Tap the microphone to try again."
                    AiVoiceState.IDLE -> "Voice mode is ready."
                }

                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (voiceState == AiVoiceState.IDLE && (handsFree || speakReplies)) {
                    Text(
                        buildString {
                            if (handsFree) append("Hands-free")
                            if (handsFree && speakReplies) append(" • ")
                            if (speakReplies) append("Replies spoken")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            if (
                voiceState == AiVoiceState.LISTENING ||
                voiceState == AiVoiceState.SPEAKING
            ) {
                TextButton(onClick = onStop) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun SetupInlineCard(
    onSetup: () -> Unit,
    onAccessibility: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Phone control isn't enabled",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Enable Accessibility if you want Dicio to interact with other apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                TextButton(onClick = onAccessibility) {
                    Text("Enable control")
                }
                TextButton(onClick = onSetup) {
                    Text("Provider settings")
                }
            }
        }
    }
}

@Composable
private fun AgentComposer(
    input: String,
    busy: Boolean,
    voiceState: AiVoiceState,
    handsFree: Boolean,
    speakReplies: Boolean,
    showVoiceMenu: Boolean,
    onInputChanged: (String) -> Unit,
    onVoice: () -> Unit,
    onToggleVoiceMenu: () -> Unit,
    onDismissVoiceMenu: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onToggleSpeech: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onVoice,
                enabled = !busy
            ) {
                Icon(
                    imageVector = if (voiceState == AiVoiceState.LISTENING) {
                        Icons.Default.MicOff
                    } else {
                        Icons.Default.Mic
                    },
                    contentDescription = if (voiceState == AiVoiceState.LISTENING) {
                        "Stop listening"
                    } else {
                        "Start voice input"
                    }
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when (voiceState) {
                            AiVoiceState.LISTENING -> "Listening…"
                            AiVoiceState.SPEAKING -> "Dicio is speaking…"
                            else -> "Ask Dicio to do something"
                        }
                    )
                },
                minLines = 1,
                maxLines = 5,
                enabled = !busy,
                shape = MaterialTheme.shapes.extraLarge,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = onToggleVoiceMenu) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Voice options"
                                )
                            }

                            DropdownMenu(
                                expanded = showVoiceMenu,
                                onDismissRequest = onDismissVoiceMenu
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (handsFree) {
                                                "Hands-free: on"
                                            } else {
                                                "Hands-free: off"
                                            }
                                        ),
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Mic,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = onToggleHandsFree
                                    )
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (speakReplies) {
                                                "Speak replies: on"
                                            } else {
                                                "Speak replies: off"
                                            }
                                        ),
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.MicOff,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = onToggleSpeech
                                    )
                                )
                            }
                        }

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
            )
        }
    }
}

@Composable
private fun MessageBubble(message: AiUiMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        if (isUser) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.82f),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(
                        horizontal = 15.dp,
                        vertical = 12.dp
                    )
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Text(
                    "Dicio",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
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
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("AI settings")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Connect an OpenAI-compatible provider. Your API key is encrypted on this device.",
                    style = MaterialTheme.typography.bodyMedium
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
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                TextButton(
                    onClick = { showAdvanced = !showAdvanced }
                ) {
                    Text(
                        if (showAdvanced) {
                            "Hide advanced"
                        } else {
                            "Advanced"
                        }
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("Agent instructions") },
                        minLines = 4
                    )
                }

                TextButton(
                    onClick = {
                        engine.openAccessibilitySettings()
                    }
                ) {
                    Text("Enable phone control")
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
