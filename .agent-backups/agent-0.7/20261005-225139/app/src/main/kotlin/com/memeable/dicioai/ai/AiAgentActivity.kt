package com.memeable.dicioai.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.io.input.stt_popup.SttPopupActivity

class AiAgentActivity : ComponentActivity() {
    private lateinit var engine: AiAgentEngine
    private var autoListen = false

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (text.isNotBlank()) engine.enqueue(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 && intent?.getBooleanExtra(EXTRA_AUTO_LISTEN, false) == true) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        engine = AiAgentEngine(applicationContext)
        autoListen = intent?.getBooleanExtra(EXTRA_AUTO_LISTEN, false) == true
        setContent {
            AppTheme {
                val agent = remember { engine }
                LaunchedEffect(autoListen) { if (autoListen) launchVoiceInput() }
                AiAgentScreen(engine = agent, onBack = { finish() }, onVoice = { launchVoiceInput() })
            }
        }
    }

    private fun launchVoiceInput() {
        val prompt = Intent(this, SttPopupActivity::class.java).apply {
            action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Dicio AI")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { voiceLauncher.launch(prompt) }.onFailure { engine.publishSystem("Voice input is unavailable on this device.") }
    }

    companion object { const val EXTRA_AUTO_LISTEN = "dicio_ai_auto_listen" }
}
