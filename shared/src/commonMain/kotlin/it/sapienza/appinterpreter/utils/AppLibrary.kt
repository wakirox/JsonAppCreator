package it.sapienza.appinterpreter.utils

import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.optString
import it.sapienza.appinterpreter.storage.AppStore
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * The apps the user has made or opened, kept on the phone under their names so they can be reopened, pinned to the
 * home screen, or deleted. An app saved again under the same name replaces the earlier version. The bundled examples
 * aren't saved here: they have their own list.
 */
class AppLibrary(
    private val store: AppStore,
    /** Told when a saved app goes away: deleted, or replaced by [replacement] (a newer version, maybe under another id). */
    private val onRemoved: (removed: SavedApp, replacement: SavedApp?, library: AppLibrary) -> Unit = { _, _, _ -> },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    /** [configurationId] is kept so an app reopened later finds its saved or bundled API keys again. */
    data class SavedApp(
        val id: String,
        val name: String,
        val json: String,
        val savedAt: Long,
        val configurationId: String
    ) {
        /** The emoji the app chose for its icon, if any. */
        val icon: String? by lazy { iconOf(json) }
    }

    /** Saves [json] under its name; nothing happens for an app whose name is missing or blank. */
    fun save(json: String, configurationId: String): SavedApp? {
        val name = nameOf(json) ?: return null
        val app = SavedApp(idOf(name), name, json, now(), configurationId)
        val entry = JsonObject(
            mapOf(
                "name" to JsonPrimitive(app.name),
                "json" to JsonPrimitive(app.json),
                "savedAt" to JsonPrimitive(app.savedAt),
                "configurationId" to JsonPrimitive(configurationId)
            )
        )
        val previous = get(app.id)
        store.put(app.id, entry.toString())
        if (previous != null) onRemoved(previous, app, this)
        return app
    }

    /** The saved apps, most recently opened first. */
    fun all(): List<SavedApp> = store.keys().mapNotNull { get(it) }.sortedByDescending { it.savedAt }

    fun get(id: String): SavedApp? = store.get(id)?.let { value ->
        runCatching {
            val entry = JsonText.parse(value) as JsonObject
            SavedApp(
                id,
                entry.getValue("name").jsonPrimitive.content,
                entry.getValue("json").jsonPrimitive.content,
                entry.getValue("savedAt").jsonPrimitive.long,
                entry.optString("configurationId")
            )
        }.getOrNull()
    }

    fun delete(id: String) {
        val removed = get(id)
        store.remove(id)
        if (removed != null) onRemoved(removed, null, this)
    }

    /** "My app", or "My app 2", "My app 3"… when that name is taken, so a new app doesn't replace a saved one. */
    fun freeName(name: String): String =
        generateSequence(1) { it + 1 }.map { if (it == 1) name else "$name $it" }.first { get(idOf(it)) == null }

    companion object {
        /** A stable id for an app name: the shortcut and the library entry share it. Names differing only in case match. */
        fun idOf(name: String): String = "app:" + name.trim().lowercase()

        fun iconOf(json: String): String? = runCatching {
            (JsonText.parse(json) as JsonObject).optString("icon")
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        fun nameOf(json: String): String? = runCatching {
            (JsonText.parse(json) as JsonObject).optString("name")
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
