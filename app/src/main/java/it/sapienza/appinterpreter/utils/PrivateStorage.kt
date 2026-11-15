package it.sapienza.appinterpreter.utils

import android.content.Context
import android.net.Uri
import java.io.File

/** Prevents external `file://` intents from reading files inside Appkin's own private data directories. */
object PrivateStorage {
    fun isPrivateFileUri(context: Context, uri: Uri): Boolean {
        if (!uri.scheme.equals("file", ignoreCase = true)) return false
        val rawPath = uri.path ?: return true
        val target = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return true
        val roots = listOfNotNull(
            context.applicationInfo?.dataDir?.let(::File),
            context.createDeviceProtectedStorageContext().dataDir,
            context.filesDir,
            context.cacheDir
        ).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        return roots.any { root ->
            target == root || target.path.startsWith(root.path + File.separator)
        }
    }
}
