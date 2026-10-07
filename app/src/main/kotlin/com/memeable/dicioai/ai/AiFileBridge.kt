package com.memeable.dicioai.ai

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.charset.StandardCharsets

object AiFileBridge {
    private const val PREFS = "dicio_ai_files"
    private const val LAST_URI = "last_selected_uri"
    private const val LAST_NAME = "last_selected_name"
    private const val MAX_READ_BYTES = 1024 * 1024

    fun rememberSelectedUri(context: Context, uri: Uri, displayName: String? = null) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(LAST_URI, uri.toString())
            .putString(LAST_NAME, displayName ?: uri.lastPathSegment.orEmpty())
            .apply()
    }

    fun lastSelected(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uri = prefs.getString(LAST_URI, null) ?: return "No file has been selected yet."
        return "name=${prefs.getString(LAST_NAME, null).orEmpty()} uri=$uri"
    }

    fun readLastSelected(context: Context): String {
        val uri = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_URI, null)
            ?: return "No file has been selected yet."
        return readContentUri(context, Uri.parse(uri))
    }

    fun read(context: Context, target: String): String {
        if (target.startsWith("content://")) return readContentUri(context, Uri.parse(target))
        val file = resolveManaged(context, target) ?: return "File path must use Dicio managed storage: files/<path> or cache/<path>."
        if (!file.exists() || !file.isFile) return "File not found: $target"
        return readFile(file)
    }

    fun listManaged(context: Context, scope: String): String {
        val root = when (scope.lowercase()) {
            "files" -> context.filesDir
            "cache" -> context.cacheDir
            else -> return "Unknown scope '$scope'. Use files or cache."
        }
        val rows = mutableListOf<String>(); walk(root, root, rows, 0)
        return rows.joinToString("\n").ifBlank { "No files found in $scope." }
    }

    fun info(context: Context, target: String): String {
        if (target.startsWith("content://")) return "Selected document URI: $target"
        val file = resolveManaged(context, target) ?: return "File path is outside Dicio managed storage."
        if (!file.exists()) return "File not found: $target"
        return "path=${relative(context, file)} size=${file.length()} modified=${file.lastModified()} directory=${file.isDirectory}"
    }

    fun write(context: Context, target: String, text: String): String {
        val file = resolveManaged(context, target) ?: return "File path is outside Dicio managed storage."
        file.parentFile?.mkdirs()
        return runCatching { file.writeText(text, StandardCharsets.UTF_8); "Created ${relative(context, file)}." }
            .getOrElse { "Could not create file: ${it.message ?: "unknown error"}" }
    }

    fun delete(context: Context, target: String): String {
        val file = resolveManaged(context, target) ?: return "File path is outside Dicio managed storage."
        if (!file.exists()) return "File not found: $target"
        return if (file.deleteRecursively()) "Deleted ${relative(context, file)}." else "Could not delete $target."
    }

    fun copy(context: Context, source: String, destination: String, move: Boolean): String {
        val src = resolveManaged(context, source) ?: return "Source is outside Dicio managed storage."
        val dst = resolveManaged(context, destination) ?: return "Destination is outside Dicio managed storage."
        if (!src.exists() || !src.isFile) return "Source file not found: $source"
        dst.parentFile?.mkdirs()
        return runCatching {
            src.copyTo(dst, overwrite = true)
            if (move) src.delete()
            if (move) "Moved ${relative(context, src)} to ${relative(context, dst)}." else "Copied ${relative(context, src)} to ${relative(context, dst)}."
        }.getOrElse { "File operation failed: ${it.message ?: "unknown error"}" }
    }

    private fun readContentUri(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            String(readLimited(input), StandardCharsets.UTF_8)
        } ?: "Could not open document."
    }.getOrElse { "Could not read document: ${it.message ?: "unknown error"}" }

    private fun readFile(file: File): String = runCatching {
        file.inputStream().use { input ->
            String(readLimited(input), StandardCharsets.UTF_8)
        }
    }.getOrElse { "Could not read file: ${it.message ?: "unknown error"}" }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val buffer = ByteArray(MAX_READ_BYTES)
        var total = 0

        while (total < MAX_READ_BYTES) {
            val count = input.read(
                buffer,
                total,
                MAX_READ_BYTES - total
            )

            if (count <= 0) break
            total += count
        }

        return buffer.copyOf(total)
    }

    private fun resolveManaged(context: Context, target: String): File? {
        val clean = target.trim().replace('\\', '/').removePrefix("/")
        val scope = clean.substringBefore('/'); val relativePath = clean.substringAfter('/', "")
        val root = when (scope.lowercase()) { "files" -> context.filesDir; "cache" -> context.cacheDir; else -> return null }.canonicalFile
        val file = if (relativePath.isBlank()) root else File(root, relativePath).canonicalFile
        return if (file.path == root.path || file.path.startsWith(root.path + File.separator)) file else null
    }

    private fun relative(context: Context, file: File): String {
        val files = context.filesDir.canonicalPath; val cache = context.cacheDir.canonicalPath; val path = file.canonicalPath
        return when {
            path == files || path.startsWith(files + File.separator) -> "files/" + path.removePrefix(files).trimStart(File.separatorChar)
            path == cache || path.startsWith(cache + File.separator) -> "cache/" + path.removePrefix(cache).trimStart(File.separatorChar)
            else -> path
        }
    }

    private fun walk(root: File, current: File, out: MutableList<String>, depth: Int) {
        if (out.size >= 200 || depth > 4) return
        if (current.isFile) { out += "${relativeName(root, current)} (${current.length()} bytes)"; return }
        current.listFiles().orEmpty().sortedBy { it.name.lowercase() }.forEach { walk(root, it, out, depth + 1) }
    }

    private fun relativeName(root: File, file: File): String = file.canonicalPath.removePrefix(root.canonicalPath).trimStart(File.separatorChar)
}
