package it.sapienza.appinterpreter.utils

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Hands large text, such as an app's JSON, to another activity. An Intent holds about 1 MB at most, so the text goes
 * through a file in the app's cache and only an unguessable token path travels in the Intent.
 */
object TextHandover {
    private const val DIRECTORY = "incoming"
    private const val MAX_AGE_MILLIS = 60L * 60 * 1000
    private val VALID_NAME = Regex("^text-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.txt$")

    fun write(context: Context, text: String): String {
        val folder = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        pruneExpired(folder)
        return File(folder, "text-${UUID.randomUUID()}.txt").apply { writeText(text) }.absolutePath
    }

    /** Reads [pathOrName] only when it resolves to a valid handover file inside this app's incoming cache directory. */
    fun read(context: Context, pathOrName: String): String? = runCatching {
        val folder = File(context.cacheDir, DIRECTORY).canonicalFile
        val input = File(pathOrName)
        val name = input.name
        if (!VALID_NAME.matches(name)) return null
        val target = File(folder, name).canonicalFile
        if (input.parent != null && input.canonicalFile != target) return null
        if (target.parentFile != folder || !target.isFile) return null
        target.readText()
    }.getOrNull()

    private fun pruneExpired(folder: File, now: Long = System.currentTimeMillis()) {
        folder.listFiles()?.forEach { file ->
            if (now - file.lastModified() > MAX_AGE_MILLIS) file.delete()
        }
    }
}
