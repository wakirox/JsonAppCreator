package it.sapienza.appinterpreter.runtime.usecase

import it.sapienza.appinterpreter.json.formValueToJson
import it.sapienza.appinterpreter.model.view_model.FormView
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Checks a form's values: either the required fields still empty, or the JSON its event receives. */
class SubmitFormUseCase {

    sealed interface Result {
        /** The titles of the required fields still empty. */
        class Missing(val fields: List<String>) : Result
        class Ready(val json: JsonObject) : Result
    }

    operator fun invoke(form: FormView, values: Map<String, Any?>): Result {
        val missing = form.formElements.filterNot { it.isSet(values[it.mapping]) }
        if (missing.isNotEmpty()) return Result.Missing(missing.map { it.title })
        // Dotted mappings ("a.b") become nested objects; a field without a value is left out.
        val root = LinkedHashMap<String, Any>()
        form.formElements.forEach { element ->
            val parts = element.mapping.split('.')
            var target = root
            parts.dropLast(1).forEach { key ->
                @Suppress("UNCHECKED_CAST")
                target = target.getOrPut(key) { LinkedHashMap<String, Any>() } as? LinkedHashMap<String, Any>
                    ?: throw IllegalStateException("Value at $key cannot be converted to JSONObject")
            }
            val value = values[element.mapping]
            if (value == null) target.remove(parts.last()) else target[parts.last()] = value
        }
        return Result.Ready(toJson(root))
    }

    private fun toJson(map: Map<String, Any>): JsonObject = JsonObject(
        map.mapValues { (_, value) ->
            @Suppress("UNCHECKED_CAST")
            if (value is Map<*, *>) toJson(value as Map<String, Any>) else formValueToJson(value)
        }
    )
}
