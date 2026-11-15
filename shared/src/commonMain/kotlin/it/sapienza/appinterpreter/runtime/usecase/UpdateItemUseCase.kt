package it.sapienza.appinterpreter.runtime.usecase

import it.sapienza.appinterpreter.json.getObject
import it.sapienza.appinterpreter.json.matchReplace
import it.sapienza.appinterpreter.model.event.UpdateData
import it.sapienza.appinterpreter.runtime.AppRepository
import it.sapienza.appinterpreter.storage.AppData
import it.sapienza.appinterpreter.utils.AppLibrary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Changes a saved object of the current app, and gives it back changed. */
class UpdateItemUseCase(private val appData: AppData, private val repository: AppRepository) {

    /**
     * The object whose id the event names (filled from [context], then [values]) takes [values] (the part the mapping
     * selects) and the event's fields; its [cleared] fields (a form's, emptied by the user) go. Throws when no such
     * object is saved.
     */
    operator fun invoke(
        event: UpdateData,
        values: JsonObject?,
        context: JsonObject?,
        cleared: Set<String> = emptySet()
    ): JsonObject {
        val id = event.id.matchReplace(context).matchReplace(values)
        val changes = event.mapping?.let { values?.getObject(it) } ?: values ?: JsonObject(emptyMap())
        val fields = event.fields.orEmpty().mapValues { (_, value) ->
            if (value is JsonPrimitive &&
                value.isString
            ) {
                JsonPrimitive(value.content.matchReplace(context).matchReplace(values))
            } else {
                value
            }
        }
        return appData.update(
            AppLibrary.idOf(repository.current().name),
            event.collection,
            id,
            JsonObject(changes + fields),
            cleared - fields.keys
        )
            ?: throw NoLongerSavedException()
    }
}

/** The object to change was deleted meanwhile (on another screen, say). */
class NoLongerSavedException : NoSuchElementException("Nothing to change: this item is no longer saved")
