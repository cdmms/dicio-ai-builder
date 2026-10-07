package com.memeable.dicioai.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiTaskCenterActivity : ComponentActivity() {
    private lateinit var engine: AiAgentEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = AiAgentRuntime.get(applicationContext)
        setContent {
            AiAgentTheme {
                AiTaskCenterScreen(
                    engine = engine,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiTaskCenterScreen(
    engine: AiAgentEngine,
    onBack: () -> Unit
) {
    var tasks by remember { mutableStateOf(engine.taskSnapshot()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            tasks = engine.taskSnapshot()
            delay(1000L)
        }
    }

    val current = tasks.firstOrNull {
        it.status == "RUNNING" || it.status == "WAITING_CONFIRMATION"
    }
    val history = tasks.filterNot { it.id == current?.id }
    val selected = selectedId?.let { id -> tasks.firstOrNull { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Tasks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (selected != null) {
            TaskDetailView(
                task = selected,
                engine = engine,
                onBack = { selectedId = null }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(18.dp)
            ) {
                current?.let { task ->
                    item {
                        SectionLabel("Current task")
                    }
                    item {
                        TaskCard(
                            task = task,
                            emphasized = true,
                            onOpen = { selectedId = task.id },
                            onResume = { engine.resumeTaskFromCenter(task.id) },
                            onStop = { engine.stopTaskFromCenter(task.id) },
                            onRetry = null,
                            onDelete = null
                        )
                    }
                }

                item {
                    SectionLabel("History")
                }

                if (history.isEmpty()) {
                    item {
                        EmptyTaskState()
                    }
                } else {
                    items(history, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            emphasized = false,
                            onOpen = { selectedId = task.id },
                            onResume = if (
                                task.status in setOf("RUNNING", "WAITING_CONFIRMATION")
                            ) {
                                { engine.resumeTaskFromCenter(task.id) }
                            } else {
                                null
                            },
                            onStop = null,
                            onRetry = if (
                                task.status !in setOf("RUNNING", "WAITING_CONFIRMATION")
                            ) {
                                { engine.retryTaskFromCenter(task.id) }
                            } else {
                                null
                            },
                            onDelete = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun EmptyTaskState() {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            "Completed, failed, and cancelled tasks will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun TaskCard(
    task: AiTaskCheckpoint,
    emphasized: Boolean,
    onOpen: () -> Unit,
    onResume: (() -> Unit)?,
    onStop: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Surface(
        tonalElevation = if (emphasized) 4.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        statusTitle(task.status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        task.request,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val step = task.step.coerceAtLeast(1)
            Text(
                "Step $step of ${AiAgentEngine.MAX_AGENT_ROUNDS}",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = {
                    (step.toFloat() / AiAgentEngine.MAX_AGENT_ROUNDS).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth()
            )

            task.lastTool?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Last action: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Text(
                formatTime(task.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = onOpen) {
                    Text("Details")
                }
                onResume?.let { TextButton(onClick = it) { Text("Resume") } }
                onStop?.let { TextButton(onClick = it) { Text("Stop") } }
                onRetry?.let { TextButton(onClick = it) { Text("Retry") } }
                onDelete?.let { TextButton(onClick = it) { Text("Delete") } }
            }
        }
    }
}

@Composable
private fun TaskDetailView(
    task: AiTaskCheckpoint,
    engine: AiAgentEngine,
    onBack: () -> Unit
) {
    val events = task.timeline.asReversed()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(18.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("← Task list")
            }
        }

        item {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        statusTitle(task.status),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        task.request,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "Step ${task.step.coerceAtLeast(1)} of ${AiAgentEngine.MAX_AGENT_ROUNDS}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    task.lastTool?.let {
                        Text(
                            "Last action: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                    Text(
                        "Updated ${formatTime(task.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.status in setOf("RUNNING", "WAITING_CONFIRMATION")) {
                    FilledTonalButton(
                        onClick = { engine.stopTaskFromCenter(task.id) }
                    ) {
                        Text("Stop")
                    }
                    TextButton(
                        onClick = { engine.resumeTaskFromCenter(task.id) }
                    ) {
                        Text("Resume")
                    }
                } else {
                    Button(
                        onClick = { engine.retryTaskFromCenter(task.id) }
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        item {
            SectionLabel("Execution timeline")
        }

        if (events.isEmpty()) {
            item {
                Text(
                    "No recorded agent steps yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(events, key = { it.id }) { event ->
                TimelineRow(event)
            }
        }
    }
}

@Composable
private fun TimelineRow(event: AiTaskTimelineEntry) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    statusIcon(event.status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (event.status) {
                        "failed" -> MaterialTheme.colorScheme.error
                        "completed", "approved" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Column(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1f)
                ) {
                    Text(
                        "Step ${event.step} • ${event.tool}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        event.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Text(
                "${event.status} • ${formatTime(event.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 26.dp, top = 6.dp)
            )
        }
    }
}

private fun statusTitle(status: String): String = when (status) {
    "RUNNING" -> "Running"
    "WAITING_CONFIRMATION" -> "Needs approval"
    "COMPLETED" -> "Completed"
    "FAILED" -> "Failed"
    "CANCELLED" -> "Cancelled"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
}

private fun statusIcon(status: String): String = when (status) {
    "COMPLETED", "completed", "approved" -> "✓"
    "RUNNING", "thinking", "requested" -> "→"
    "WAITING_CONFIRMATION" -> "!"
    "FAILED" -> "×"
    "CANCELLED" -> "■"
    "recovery" -> "↻"
    else -> "•"
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
