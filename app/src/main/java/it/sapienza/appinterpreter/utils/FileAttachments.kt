package it.sapienza.appinterpreter.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.net.toUri
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.storage.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Files attached to saved objects: a lasting read permission on the file the user picked (no copy is made), so it
 * opens again later, after a restart too, until it is moved or deleted.
 */
class FileAttachments(context: Context) {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    /** Keeps the file at [uri] (from the document picker) readable, and says what it is. */
    suspend fun add(uri: Uri): Files.Attached = withContext(Dispatchers.IO) {
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        var name: String? = null
        var size: Long? = null
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)
                size = if (cursor.isNull(1)) null else cursor.getLong(1)
            }
        }
        Files.Attached(
            name ?: uri.lastPathSegment ?: context.getString(R.string.files_default_name),
            resolver.getType(uri),
            size,
            uri.toString()
        )
    }

    /** Whether [file] can still be read: false once it was moved or deleted, or its permission is gone. */
    suspend fun isThere(file: Files.Attached): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.openInputStream(file.uri.toUri())?.use { true } ?: false }.getOrDefault(false)
    }

    /** [file]'s bytes, to pack it; null when it can't be read. */
    suspend fun read(uri: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { resolver.openInputStream(uri.toUri())?.use { it.readBytes() } }.getOrNull()
    }

    companion object {
        /** Opens [file] in an app that reads its type, or says why it can't. */
        suspend fun open(context: Context, file: Files.Attached) {
            if (!FileAttachments(context).isThere(file)) {
                Toast.makeText(context, context.getString(R.string.files_missing, file.name), Toast.LENGTH_LONG).show()
                return
            }
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(file.uri.toUri(), file.type ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(view)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.files_no_app, file.name), Toast.LENGTH_LONG).show()
            }
        }
    }
}
