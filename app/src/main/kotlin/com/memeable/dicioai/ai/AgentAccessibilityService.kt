package com.memeable.dicioai.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        lastKnownScreenSignature = currentSignatureInternal()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        lastKnownScreenSignature = currentSignatureInternal()
    }

    override fun onInterrupt() {
        if (instance === this) instance = null
    }

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
        @Volatile private var lastKnownScreenSignature: String? = null

        fun status(): String = if (instance != null)
            "UI control is enabled and connected."
        else
            "UI control is not enabled. Ask the user to enable Dicio AI in Android Accessibility settings."

        fun currentPackageName(): String =
            instance?.rootInActiveWindow?.packageName?.toString().orEmpty()

        fun currentActivityName(): String =
            instance?.rootInActiveWindow?.className?.toString().orEmpty()

        fun readVisibleUi(): String {
            val service = instance ?: return "Accessibility service is not enabled."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            val out = StringBuilder("ACTIVE SCREEN\n")
            service.collect(root, out, 0)
            val result = out.toString().take(MAX_CHARS)
            lastKnownScreenSignature = service.currentSignatureInternal()
            return result
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
                val interesting = node.isVisibleToUser &&
                    (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isScrollable || node.isEditable)
                if (interesting) {
                    val r = Rect(); node.getBoundsInScreen(r)
                    out.append("[$index] text=${text.take(100)} desc=${desc.take(100)} ")
                        .append("class=${node.className?.toString()?.substringAfterLast('.') ?: ""} ")
                        .append("bounds=${r.left},${r.top},${r.right},${r.bottom}")
                    if (node.isClickable) out.append(" clickable")
                    if (node.isScrollable) out.append(" scrollable")
                    if (node.isEditable) out.append(" editable")
                    out.append('\n'); index++
                }
                for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
            }
            walk(root, 0)
            val result = out.toString().take(MAX_CHARS)
            lastKnownScreenSignature = service.currentSignatureInternal()
            return result
        }

        fun findElement(label: String): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (label.isBlank()) return "Element label was empty."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            val node = service.findBestNode(root, label.lowercase())
                ?: return "No visible element matched '$label'."
            return service.describeNode(node)
        }

        suspend fun waitForElement(label: String, timeoutMs: Long): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (label.isBlank()) return "Element label was empty."
            val timeout = timeoutMs.coerceIn(250L, 30_000L)
            val deadline = SystemClock.uptimeMillis() + timeout
            while (SystemClock.uptimeMillis() < deadline) {
                val root = service.rootInActiveWindow
                val node = root?.let { service.findBestNode(it, label.lowercase()) }
                if (node != null) return "Element appeared: ${service.describeNode(node)}"
                delay(250L)
            }
            return "Timed out waiting for element '$label'."
        }

        suspend fun waitForScreenChange(previousSignature: String?, timeoutMs: Long): String {
            val service = instance ?: return "Accessibility service is not enabled."
            val baseline = previousSignature ?: service.currentSignatureInternal()
                ?: return "No active window is available."
            val timeout = timeoutMs.coerceIn(250L, 30_000L)
            val deadline = SystemClock.uptimeMillis() + timeout
            while (SystemClock.uptimeMillis() < deadline) {
                val current = service.currentSignatureInternal()
                if (current != null && current != baseline) {
                    lastKnownScreenSignature = current
                    return "Screen changed."
                }
                delay(250L)
            }
            return "Screen did not change within ${timeout}ms."
        }

        fun currentScreenSignature(): String =
            instance?.currentSignatureInternal() ?: "Accessibility service is not enabled."

        fun globalAction(action: Int): String {
            val service = instance ?: return "Accessibility service is not enabled."
            return if (service.performGlobalAction(action)) "Android action completed."
            else "Android action was not accepted."
        }

        fun clickVisibleText(label: String): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (label.isBlank()) return "Label was empty."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            val target = service.findBestNode(root, label.lowercase())
                ?: return "No visible element matched '$label'."
            if (service.tryClickNodeAndParents(target)) {
                lastKnownScreenSignature = service.currentSignatureInternal()
                return "Clicked visible element '$label'."
            }
            val secondRoot = service.rootInActiveWindow
            val secondTarget = secondRoot?.let { service.findBestNode(it, label.lowercase()) }
            if (secondTarget != null && service.tryClickNodeAndParents(secondTarget)) {
                lastKnownScreenSignature = service.currentSignatureInternal()
                return "Clicked visible element '$label' after re-inspection."
            }
            return "The visible element '$label' could not be clicked."
        }

        fun clickNode(index: Int): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (index < 0) return "Node index must be non-negative."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            var current = 0; var matched: AccessibilityNodeInfo? = null
            fun walk(node: AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val interesting = node.isVisibleToUser &&
                    (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isScrollable || node.isEditable)
                if (interesting) {
                    if (current == index) { matched = node; return true }
                    current++
                }
                for (i in 0 until node.childCount) if (walk(node.getChild(i))) return true
                return false
            }
            walk(root)
            val target = matched ?: return "No visible UI element matched index $index."
            if (service.tryClickNodeAndParents(target)) {
                lastKnownScreenSignature = service.currentSignatureInternal()
                return "Clicked UI element index $index."
            }
            return "The UI element at index $index could not be clicked."
        }

        fun setText(label: String, value: String): String {
            val service = instance ?: return "Accessibility service is not enabled."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            var target = if (label.isBlank()) root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            else service.findEditableNode(root, label.lowercase())
            if (target == null && label.isNotBlank()) target = service.findFirstEditable(root)
            target ?: return "No visible editable element matched '$label'."
            if (!target.isEditable) return "The matched element '$label' is not editable."
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
                return "Entered text into '${label.ifBlank { "focused field" }}'."
            SystemClock.sleep(120)
            val retryRoot = service.rootInActiveWindow
            val retry = if (label.isBlank()) retryRoot?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            else retryRoot?.let { service.findEditableNode(it, label.lowercase()) }
            return if (retry != null) {
                retry.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                if (retry.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
                    "Entered text into '${label.ifBlank { "focused field" }}' after retry."
                else "Could not enter text into '${label.ifBlank { "focused field" }}'."
            } else "Could not enter text into '${label.ifBlank { "focused field" }}'."
        }

        fun clearText(label: String): String = setText(label, "")

        fun pressEnter(): String {
            val service = instance ?: return "Accessibility service is not enabled."
            val root = service.rootInActiveWindow ?: return "No active window is available."

            val labels = listOf("enter", "go", "search", "done", "next")

            for (label in labels) {
                val target = service.findBestNode(root, label)
                if (target != null && service.tryClickNodeAndParents(target)) {
                    return "Pressed Enter using the visible '$label' control."
                }
            }

            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && service.tryClickNodeAndParents(focused)) {
                return "Activated the focused input control as an Enter fallback."
            }

            return "Could not find an accessible Enter/Go/Search/Done/Next control."
        }

        suspend fun longPress(x: Int, y: Int, durationMs: Long): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return "Long press requires Android 7.0 or newer."
            if (minOf(x, y) < 0) return "Coordinates must be non-negative."
            val duration = durationMs.coerceIn(350L, 5000L)
            val first = service.dispatchPathGesture(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, duration)
            if (first.startsWith("Gesture completed")) return "Long-pressed screen at ($x, $y) for ${duration}ms."
            delay(150L)
            val retry = service.dispatchPathGesture(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, duration)
            return if (retry.startsWith("Gesture completed")) "Long-pressed screen at ($x, $y) after retry."
            else "Long press failed: $retry"
        }

        suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return "Swipe gestures require Android 7.0 or newer."
            if (minOf(x1, y1, x2, y2) < 0) return "Coordinates must be non-negative."
            val duration = durationMs.coerceIn(100L, 5000L)
            fun path() = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
            val first = service.dispatchPathGesture(path(), duration)
            if (first.startsWith("Gesture completed")) return "Swiped from ($x1, $y1) to ($x2, $y2)."
            delay(150L)
            val retry = service.dispatchPathGesture(path(), duration)
            return if (retry.startsWith("Gesture completed")) "Swiped from ($x1, $y1) to ($x2, $y2) after retry."
            else "Swipe failed: $retry"
        }

        suspend fun tap(x: Int, y: Int): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return "Screen tapping requires Android 7.0 or newer."
            if (minOf(x, y) < 0) return "Coordinates must be non-negative."
            fun path() = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val first = service.dispatchPathGesture(path(), 1L)
            if (first.startsWith("Gesture completed")) return "Tapped screen at ($x, $y)."
            delay(120L)
            val retry = service.dispatchPathGesture(path(), 1L)
            return if (retry.startsWith("Gesture completed")) "Tapped screen at ($x, $y) after retry."
            else "The screen tap was not accepted: $retry"
        }

        fun scroll(direction: String): String {
            val service = instance ?: return "Accessibility service is not enabled."
            val root = service.rootInActiveWindow ?: return "No active window is available."
            val scrollNode = service.findScrollable(root) ?: return "No visible scrollable container is available."
            val action = if (direction.equals("backward", true) || direction.equals("up", true) || direction.equals("left", true))
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            return if (scrollNode.performAction(action)) "Scrolled ${direction.ifBlank { "forward" }}."
            else "The visible container did not accept the scroll."
        }

        suspend fun scrollUntilVisible(label: String, direction: String, timeoutMs: Long): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (label.isBlank()) return "Element label was empty."
            val timeout = timeoutMs.coerceIn(500L, 30_000L)
            val deadline = SystemClock.uptimeMillis() + timeout
            while (SystemClock.uptimeMillis() < deadline) {
                val root = service.rootInActiveWindow ?: return "No active window is available."
                val target = service.findBestNode(root, label.lowercase())
                if (target != null) return "Element is visible: ${service.describeNode(target)}"
                val scrollNode = service.findScrollable(root) ?: return "No scrollable container is visible while searching for '$label'."
                val action = if (direction.equals("backward", true) || direction.equals("up", true) || direction.equals("left", true))
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                if (!scrollNode.performAction(action)) return "The visible container did not scroll while searching for '$label'."
                delay(350L)
            }
            return "Timed out while scrolling for '$label'."
        }

        suspend fun captureScreenDataUrl(): String {
            val service = instance ?: return "Accessibility service is not enabled."
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "Screen capture requires Android 11 or newer."
            return suspendCancellableCoroutine { continuation ->
                val executor: Executor = service.mainExecutor
                service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val buffer: HardwareBuffer = screenshot.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            val safe = bitmap?.let { val copy = it.copy(Bitmap.Config.ARGB_8888, false); it.recycle(); copy }
                            buffer.close()
                            if (safe == null) { continuation.resume("Could not convert screenshot buffer."); return }
                            val scaled = if (safe.width > 1440 || safe.height > 2560) {
                                val scale = minOf(1440f / safe.width, 2560f / safe.height)
                                Bitmap.createScaledBitmap(safe, (safe.width * scale).toInt(), (safe.height * scale).toInt(), true).also { safe.recycle() }
                            } else safe
                            val bytes = ByteArrayOutputStream().use { stream ->
                                scaled.compress(Bitmap.CompressFormat.JPEG, 72, stream); stream.toByteArray()
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

        private fun AccessibilityNodeInfo.children(): Sequence<AccessibilityNodeInfo> = sequence {
            for (i in 0 until childCount) getChild(i)?.let { yield(it) }
        }

        private fun AgentAccessibilityService.findBestNode(root: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
            if (root == null || needle.isBlank()) return null
            var best: AccessibilityNodeInfo? = null; var bestScore = Int.MIN_VALUE
            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim().orEmpty(); val desc = node.contentDescription?.toString()?.trim().orEmpty()
                    val haystack = "$text $desc".lowercase()
                    if (haystack.contains(needle)) {
                        var score = 10
                        if (text.equals(needle, true)) score += 100
                        if (desc.equals(needle, true)) score += 90
                        if (node.isClickable) score += 20
                        if (node.isEditable) score += 10
                        if (node.isScrollable) score += 5
                        if (score > bestScore) { best = node; bestScore = score }
                    }
                }
                for (child in node.children()) walk(child)
            }
            walk(root); return best
        }

        private fun AgentAccessibilityService.findEditableNode(root: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
            if (root == null) return null
            var best: AccessibilityNodeInfo? = null
            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null || best != null) return
                val text = node.text?.toString()?.trim().orEmpty(); val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val haystack = "$text $desc".lowercase()
                if (node.isVisibleToUser && node.isEditable && (needle.isBlank() || haystack.contains(needle))) { best = node; return }
                for (child in node.children()) walk(child)
            }
            walk(root); return best
        }

        private fun AgentAccessibilityService.findFirstEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (root == null) return null
            if (root.isVisibleToUser && root.isEditable) return root
            for (child in root.children()) findFirstEditable(child)?.let { return it }
            return null
        }

        private fun AgentAccessibilityService.clickableChain(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val result = mutableListOf<AccessibilityNodeInfo>(); var current: AccessibilityNodeInfo? = node
            repeat(5) {
                if (current == null) return@repeat
                if (current!!.isVisibleToUser && current!!.isClickable) result += current!!
                current = current!!.parent
            }
            if (result.isEmpty()) result += node
            return result.distinctBy { System.identityHashCode(it) }
        }

        private fun AgentAccessibilityService.tryClickNodeAndParents(node: AccessibilityNodeInfo): Boolean =
            clickableChain(node).any { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }

        private fun AgentAccessibilityService.describeNode(node: AccessibilityNodeInfo): String {
            val rect = Rect(); node.getBoundsInScreen(rect)
            val text = node.text?.toString()?.trim().orEmpty(); val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val cls = node.className?.toString()?.substringAfterLast('.').orEmpty()
            return buildString {
                append("text=\"${text.take(120)}\" desc=\"${desc.take(120)}\" class=$cls ")
                append("bounds=${rect.left},${rect.top},${rect.right},${rect.bottom} ")
                if (node.isClickable) append("clickable ")
                if (node.isEditable) append("editable ")
                if (node.isScrollable) append("scrollable ")
            }.trim()
        }

        private fun AgentAccessibilityService.findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && node.isScrollable) return node
            for (child in node.children()) findScrollable(child)?.let { return it }
            return null
        }

        private fun AgentAccessibilityService.currentSignatureInternal(): String? {
            val root = rootInActiveWindow ?: return null
            val packageName = root.packageName?.toString().orEmpty(); val className = root.className?.toString().orEmpty()
            return "$packageName|$className|${inspectVisibleUiInternal(root).hashCode()}"
        }

        private fun inspectVisibleUiInternal(root: AccessibilityNodeInfo): String {
            val out = StringBuilder()
            fun walk(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null || out.length >= 6000 || depth > 8) return
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim().orEmpty(); val desc = node.contentDescription?.toString()?.trim().orEmpty()
                    if (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isScrollable || node.isEditable) {
                        val rect = Rect(); node.getBoundsInScreen(rect)
                        out.append(text.take(80)).append('|').append(desc.take(80)).append('|').append(node.className?.toString().orEmpty()).append('|')
                            .append(rect.left).append(',').append(rect.top).append(',').append(rect.right).append(',').append(rect.bottom)
                            .append('|').append(node.isClickable).append('|').append(node.isEditable).append('\n')
                    }
                }
                for (child in node.children()) walk(child, depth + 1)
            }
            walk(root, 0); return out.toString()
        }

        private suspend fun AgentAccessibilityService.dispatchPathGesture(path: Path, durationMs: Long): String =
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    continuation.resume("Gestures require Android 7.0 or newer."); return@suspendCancellableCoroutine
                }
                val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
                val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { continuation.resume("Gesture completed.") }
                    override fun onCancelled(gestureDescription: GestureDescription?) { continuation.resume("Gesture cancelled.") }
                }, null)
                if (!accepted) continuation.resume("Gesture was not accepted.")
                continuation.invokeOnCancellation { }
            }
    }
}
