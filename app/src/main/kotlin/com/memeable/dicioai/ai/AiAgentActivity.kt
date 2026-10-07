package com.memeable.dicioai.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import org.stypox.dicio.ui.theme.AppTheme

class AiAgentActivity : ComponentActivity() {
    private lateinit var engine: AiAgentEngine
    private lateinit var voiceController: AiVoiceController
    private val mainHandler = Handler(Looper.getMainLooper())

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                voiceController.startListening()
            } else {
                engine.publishSystem("Microphone permission is needed for voice control.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val autoListen = intent?.getBooleanExtra(EXTRA_AUTO_LISTEN, false) == true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 && autoListen) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        engine = AiAgentRuntime.get(applicationContext)
        voiceController = AiVoiceController(this)
        voiceController.onResult = { spoken -> handleVoiceInput(spoken) }
        voiceController.onError = { message -> engine.publishSystem(message) }
        voiceController.onSpeechFinished = { continueHandsFreeIfNeeded() }

        val scheduledRequest = intent?.getStringExtra(EXTRA_RUN_TASK)

        setContent {
            AppTheme {
                val agent = remember { engine }
                val voiceState by voiceController.state
                val voicePartial by voiceController.partialText
                val handsFree by voiceController.handsFreeEnabled
                val speakReplies by voiceController.speakRepliesEnabled
                val messages by agent.messages
                val pending by agent.pendingConfirmation

                val initialMessageId = remember { messages.lastOrNull()?.id }
                var lastSpokenMessageId by rememberSaveable {
                    androidx.compose.runtime.mutableStateOf(initialMessageId)
                }

                LaunchedEffect(Unit) {
                    if (autoListen) {
                        voiceController.setHandsFreeEnabled(true)
                        requestOrStartListening()
                    } else if (voiceController.handsFreeEnabled) {
                        requestOrStartListening()
                    }
                }

                LaunchedEffect(scheduledRequest) {
                    if (!scheduledRequest.isNullOrBlank()) {
                        engine.enqueue(scheduledRequest)
                    }
                }

                LaunchedEffect(messages.lastOrNull()?.id) {
                    val last = messages.lastOrNull()
                    if (last?.role == "assistant" && last.id != lastSpokenMessageId) {
                        lastSpokenMessageId = last.id
                        if (voiceController.speakRepliesEnabled) {
                            voiceController.speak(last.content)
                        } else {
                            continueHandsFreeIfNeeded()
                        }
                    }
                }

                LaunchedEffect(pending?.summary) {
                    val current = pending ?: return@LaunchedEffect
                    if (voiceController.speakRepliesEnabled) {
                        voiceController.speak("I need your approval. ${current.summary}")
                    } else {
                        continueHandsFreeIfNeeded()
                    }
                }

                AiAgentScreen(
                    engine = agent,
                    onBack = {
                        startActivity(
                            Intent(
                                this@AiAgentActivity,
                                org.stypox.dicio.MainActivity::class.java
                            )
                        )
                        finish()
                    },
                    onVoice = { requestOrStartListening() },
                    onStopVoice = { voiceController.stop() },
                    onTaskCenter = {
                        startActivity(
                            Intent(
                                this@AiAgentActivity,
                                AiTaskCenterActivity::class.java
                            )
                        )
                    },
                    voiceState = voiceState,
                    voicePartialText = voicePartial,
                    handsFree = handsFree,
                    speakReplies = speakReplies,
                    onToggleHandsFree = {
                        val enabled = voiceController.toggleHandsFree()
                        if (enabled) {
                            requestOrStartListening()
                        }
                    },
                    onToggleSpeech = { voiceController.toggleSpeakReplies() }
                )
            }
        }
    }

    private fun requestOrStartListening() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        voiceController.startListening()
    }

    private fun handleVoiceInput(spoken: String) {
        val pending = engine.pendingConfirmation.value
        if (pending != null) {
            when (spoken.trim().lowercase()) {
                "approve", "approved", "yes", "yes approve", "do it", "continue", "confirm" ->
                    engine.confirmPending(true)
                "cancel", "cancel it", "no", "deny", "stop", "don't do it", "dont do it" ->
                    engine.confirmPending(false)
                else -> engine.publishSystem("Please say approve or cancel.")
            }
            return
        }
        engine.enqueue(spoken)
    }

    private fun continueHandsFreeIfNeeded() {
        mainHandler.postDelayed({
            if (
                !isFinishing &&
                !isDestroyed &&
                voiceController.handsFreeEnabled &&
                voiceController.state.value == AiVoiceState.IDLE
            ) {
                requestOrStartListening()
            }
        }, 350L)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (::voiceController.isInitialized) voiceController.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTO_LISTEN = "dicio_ai_auto_listen"
        const val EXTRA_RUN_TASK = "dicio_ai_run_task"
    }
}
