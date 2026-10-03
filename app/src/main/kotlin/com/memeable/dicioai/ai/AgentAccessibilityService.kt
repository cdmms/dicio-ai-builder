package com.memeable.dicioai.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.view.Display
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) { /* State is read on demand. */ }
    override fun onInterrupt() { if (instance === this) instance = null }

    private fun collect(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || out.length >= MAX_CHARS || depth > 8) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val cls = node.className?.toString()?.substringAfterLast('.')?.trim().orEmpty()
        if (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isScrollable) {
            out.append("  ".repeat(depth))
            if (text.isNotBlank()) out.append("text=\"${text.take(140)}\" ")
            if (desc.isNotBlank()) out.append("desc=\"${desc.take(140)}\" ")
            if (cls.isNotBlank()) out.append("class=$cls ")
            if (node.isClickable) out.append("clickable ")
            if (node.isScrollable) out.append("scrollable ")
            out.append('\n')
        }
        for (i in 0 until node.childCount) collect(node.getChild(i), out, depth + 1)
    }

    companion object {
        private const val MAX_CHARS = 9000
        @Volatile private var instance: AgentAccessibilityService? = null

        fun status(): String = if (instance != null)
            "UI control is enabled and connected." else
            "UI control is not enabled. Ask the user to enable Dicio AI in Android Accessibility settings."

        fun readVisibleUi(): String {
            val service = instance ?: return "Accessibility service is not enabled."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            val out = StringBuilder()
            out.append("ACTIVE SCREEN\n")
            service.collect(root, out, 0)
            return out.toString().take(MAX_CHARS)
        }

        fun globalAction(action: Int): String {
            val service = instance ?: return "Accessibility service is not enabled."
            return if (service.performGlobalAction(action)) "Android action completed." else "Android action was not accepted."
        }

        fun clickVisibleText(label: String): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (label.isBlank()) return "Label was empty."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            val target = findNode(root, label.lowercase()) ?: return "No visible element matched '$label'."
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "Clicked visible element '$label'."
            return "The visible element '$label' could not be clicked."
        }

        fun scroll(direction: String): String {
            val service = instance ?: return "Accessibility service is not enabled."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            val action = if (direction.equals("backward", ignoreCase = true) || direction.equals("up", ignoreCase = true))
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            val node = findScrollable(root) ?: return "No scrollable container is visible."
            return if (node.performAction(action)) "Scrolled ${if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) "backward" else "forward"}." else "The visible container did not scroll."
        }

        fun inspectVisibleUi(): String {
            val service = instance ?: return "Accessibility service is not enabled."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            val out = StringBuilder("VISIBLE UI ELEMENTS\n")
            var index = 0
            fun walk(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null || out.length >= MAX_CHARS || depth > 10) return
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val interesting = node.isVisibleToUser && (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isScrollable || node.isEditable)
                if (interesting) {
                    val r = android.graphics.Rect()
                    node.getBoundsInScreen(r)
                    out.append("[$index] text=${text.take(100)} desc=${desc.take(100)} class=${node.className?.toString()?.substringAfterLast('.') ?: ""} bounds=${r.left},${r.top},${r.right},${r.bottom}")
                    if (node.isClickable) out.append(" clickable")
                    if (node.isScrollable) out.append(" scrollable")
                    if (node.isEditable) out.append(" editable")
                    out.append('\n')
                    index++
                }
                for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
            }
            walk(root, 0)
            return out.toString().take(MAX_CHARS)
        }

        suspend fun captureScreenDataUrl(): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "Screen capture requires Android 11 or newer."
            return suspendCancellableCoroutine { continuation ->
                val executor: Executor = service.mainExecutor
                service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback() {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val buffer: HardwareBuffer = screenshot.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            val safe = bitmap?.let {
                                val copy = it.copy(Bitmap.Config.ARGB_8888, false)
                                it.recycle()
                                copy
                            }
                            buffer.close()
                            if (safe == null) {
                                continuation.resume("Could not convert screenshot buffer.")
                                return
                            }
                            val scaled = if (safe.width > 1440 || safe.height > 2560) {
                                val scale = minOf(1440f / safe.width, 2560f / safe.height)
                                Bitmap.createScaledBitmap(safe, (safe.width * scale).toInt(), (safe.height * scale).toInt(), true).also { safe.recycle() }
                            } else safe
                            val bytes = ByteArrayOutputStream().use { stream ->
                                scaled.compress(Bitmap.CompressFormat.JPEG, 72, stream)
                                stream.toByteArray()
                            }
                            scaled.recycle()
                            continuation.resume("data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}")
                        } catch (t: Throwable) {
                            continuation.resume("Screenshot failed: ${t.message ?: "unknown error"}")
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        continuation.resume("Screenshot failed with Android error code $errorCode.")
                    }
                })
            }
        }

        fun clickNode(index: Int): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (index < 0) return "Node index must be non-negative."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            var current = 0
            fun walk(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val interesting = node.isVisibleToUser && (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isScrollable || node.isEditable)
                if (interesting) {
                    if (current == index) return node
                    current++
                }
                for (i in 0 until node.childCount) walk(node.getChild(i))?.let { return it }
                return null
            }
            val target = walk(root) ?: return "No visible UI element matched index $index."
            return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) "Clicked UI element index $index." else "The UI element at index $index could not be clicked."
        }

        fun setText(label: String, value: String): String {
            val service = instance ?: return "Accessibility service is not enabled."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            val target = findNode(root, label.lowercase()) ?: return "No visible editable element matched '$label'."
            if (!target.isEditable) return "The matched element '$label' is not editable."
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Entered text into '$label'." else "Could not enter text into '$label'."
        }

        private fun findNode(node: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
            if (node == null) return null
            val text = (node.text?.toString().orEmpty() + " " + node.contentDescription?.toString().orEmpty()).lowercase()
            if (text.contains(needle) && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) {
                findNode(node.getChild(i), needle)?.let { return it }
            }
            return null
        }

        private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && node.isScrollable) return node
            for (i in 0 until node.childCount) {
                findScrollable(node.getChild(i))?.let { return it }
            }
            return null
        }

        suspend fun tap(x: Int, y: Int): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return "Screen tapping requires Android 7.0 or newer."
            if (x < 0 || y < 0) return "Coordinates must be non-negative."
            return suspendCancellableCoroutine { continuation ->
                val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
                val stroke = GestureDescription.StrokeDescription(path, 0, 1)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val accepted = service.dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { continuation.resume("Tapped screen at ($x, $y).") }
                    override fun onCancelled(gestureDescription: GestureDescription?) { continuation.resume("The screen tap was cancelled.") }
                }, null)
                if (!accepted) continuation.resume("The screen tap was not accepted.")
                continuation.invokeOnCancellation { /* system owns the gesture */ }
            }
        }
    }
}
