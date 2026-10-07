package com.memeable.dicioai.ai

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

class AiAgentForegroundService : Service() {
    private lateinit var engine: AiAgentEngine

    override fun onCreate() {
        super.onCreate()
        instance = this
        engine = AiAgentRuntime.get(applicationContext)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            AiAgentNotifications.foregroundNotification(this),
            type
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                intent.getStringExtra(EXTRA_REQUEST)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(engine::startNewTaskFromService)
            }

            ACTION_RESUME -> {
                engine.startExistingTaskFromService(
                    intent.getStringExtra(EXTRA_TASK_ID)
                )
            }

            ACTION_APPROVE -> {
                engine.confirmPendingFromService(
                    intent.getBooleanExtra(EXTRA_APPROVED, false)
                )
            }

            ACTION_CANCEL -> {
                engine.cancelCurrentTask()
            }

            null -> {
                // START_STICKY recovery after the Android process/service was recreated.
                engine.restoreInterruptedTaskFromService()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        AiAgentNotifications.clearRunning(this)
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.memeable.dicioai.ai.action.START"
        const val ACTION_RESUME = "com.memeable.dicioai.ai.action.RESUME"
        const val ACTION_APPROVE = "com.memeable.dicioai.ai.action.APPROVE"
        const val ACTION_CANCEL = "com.memeable.dicioai.ai.action.CANCEL"

        const val EXTRA_REQUEST = "dicio_ai_agent_request"
        const val EXTRA_TASK_ID = "dicio_ai_agent_task_id"
        const val EXTRA_APPROVED = "dicio_ai_agent_approved"

        private const val NOTIFICATION_ID = 8700

        @Volatile
        private var instance: AiAgentForegroundService? = null

        fun isRunning(): Boolean = instance != null

        fun startTask(context: Context, request: String) {
            start(
                context,
                Intent(context, AiAgentForegroundService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_REQUEST, request)
            )
        }

        fun startExistingTask(context: Context, taskId: String) {
            start(
                context,
                Intent(context, AiAgentForegroundService::class.java)
                    .setAction(ACTION_RESUME)
                    .putExtra(EXTRA_TASK_ID, taskId)
            )
        }

        fun approvePending(context: Context, approved: Boolean) {
            start(
                context,
                Intent(context, AiAgentForegroundService::class.java)
                    .setAction(ACTION_APPROVE)
                    .putExtra(EXTRA_APPROVED, approved)
            )
        }

        fun restore(context: Context) {
            start(
                context,
                Intent(context, AiAgentForegroundService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, AiAgentForegroundService::class.java)
            )
        }

        private fun start(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: SecurityException) {
                // The task remains persisted and can be resumed when the app is opened.
            } catch (_: IllegalStateException) {
                // Background-start restrictions can reject a request; persisted state is safe.
            }
        }
    }
}
