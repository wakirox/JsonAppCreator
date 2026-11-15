package it.sapienza.appinterpreter.security

import it.sapienza.appinterpreter.utils.AppLibrary

/**
 * Deletes the saved API keys nothing needs any more when a saved app is deleted or replaced by a newer version: the
 * keys of placeholders an edit removed, and all of an app's keys when it goes away or moves to another configuration.
 * Keys stay while another saved app shares the configuration id, and bundled configurations have no saved keys.
 */
class ReleaseSavedAppKeysUseCase(private val keys: ConfigurationKeys) {
    operator fun invoke(removed: AppLibrary.SavedApp, replacement: AppLibrary.SavedApp?, library: AppLibrary) {
        val unused = ApiKeyPlaceholders.names(removed.json).let { names ->
            if (replacement?.configurationId == removed.configurationId) {
                names - ApiKeyPlaceholders.names(replacement.json)
            } else {
                names
            }
        }
        if (unused.isEmpty()) return
        if (library.all().any { it.id != removed.id && it.configurationId == removed.configurationId }) return
        keys.delete(removed.configurationId, unused)
    }
}
