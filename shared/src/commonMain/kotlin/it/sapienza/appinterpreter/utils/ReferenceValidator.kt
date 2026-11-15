package it.sapienza.appinterpreter.utils

import it.sapienza.appinterpreter.json.JsonNumbers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Validates and indexes the reference graph before model conversion loses source paths. */
internal class ReferenceValidator {
    private data class LocatedReference(val id: String, val path: String)

    private val viewDefinitions = linkedMapOf<String, String>()
    private val actionDefinitions = linkedMapOf<String, String>()
    private val viewReferences = mutableListOf<LocatedReference>()
    private val actionReferences = mutableListOf<LocatedReference>()
    private val containmentEdges = linkedMapOf<String, MutableSet<String>>()

    fun validate(document: JsonElement?) {
        val root = document as? JsonObject ?: fail("$", "application configuration must be a JSON object")

        root["schemaVersion"]?.let { version ->
            val number = (version as? JsonPrimitive)?.takeUnless { it.isString }?.content
            if (number == null || JsonNumbers.isDecimal(number) || number.toIntOrNull() != 3) {
                fail("$.schemaVersion", "unsupported schema version; expected 3")
            }
        }

        root["views"]?.let { views ->
            if (views !is JsonArray) fail("$.views", "must be an array")
            views.forEachIndexed { index, view ->
                visitView(view, "$.views[$index]", parentId = null, definitionRequired = true)
            }
        }

        root["actions"]?.let { actions ->
            if (actions !is JsonArray) fail("$.actions", "must be an array")
            actions.forEachIndexed { index, action ->
                visitAction(action, "$.actions[$index]", definitionRequired = true)
            }
        }

        root["mainView"]?.let { visitView(it, "$.mainView", parentId = null) }

        viewReferences.firstOrNull { it.id !in viewDefinitions }?.let {
            fail(it.path, "unresolved view reference '${it.id}'")
        }
        actionReferences.firstOrNull { it.id !in actionDefinitions }?.let {
            fail(it.path, "unresolved action reference '${it.id}'")
        }

        validateContainmentCycles()
    }

    private fun visitView(element: JsonElement, path: String, parentId: String?, definitionRequired: Boolean = false) {
        val node = element as? JsonObject ?: fail(path, "view must be an object")

        if (node.containsKey("ref")) {
            if (definitionRequired) fail(path, "top-level views must be definitions, not references")
            // Where a reference is placed, it may say when it's shown.
            val id = exclusiveReference(node, path, "view", "ref", allowed = setOf("visibleIf"))
            addViewReference(id, "$path.ref", parentId)
            return
        }

        if (!node.containsKey("type")) {
            if (definitionRequired) fail(path, "top-level views must include a type")
            if (node.size != 1 || !node.containsKey("id")) {
                fail(
                    path,
                    "view must be a definition with type or a reference with ref (legacy id-only references are also accepted)"
                )
            }
            val id = requiredIdentifier(node["id"], "$path.id", "view reference")
            addViewReference(id, "$path.id", parentId)
            return
        }

        val id = node["id"]?.let { requiredIdentifier(it, "$path.id", "view definition") }
        if (id != null) {
            registerDefinition(viewDefinitions, id, path, "view")
            parentId?.let { containmentEdges.getOrPut(it) { linkedSetOf() }.add(id) }
        }
        val ownerId = id ?: parentId

        node["views"]?.let { children ->
            if (children !is JsonArray) fail("$path.views", "must be an array")
            children.forEachIndexed { index, child ->
                visitView(child, "$path.views[$index]", ownerId)
            }
        }
        node["itemView"]?.let { visitView(it, "$path.itemView", ownerId) }
        node["action"]?.let { visitAction(it, "$path.action") }
        node["swipeToStart"]?.let { visitAction(it, "$path.swipeToStart") }
        node["swipeToEnd"]?.let { visitAction(it, "$path.swipeToEnd") }
        node["initData"]?.let { visitEvent(it, "$path.initData") }
    }

    private fun visitAction(element: JsonElement, path: String, definitionRequired: Boolean = false) {
        val node = element as? JsonObject ?: fail(path, "action must be an object")

        if (node.containsKey("ref")) {
            if (definitionRequired) fail(path, "top-level actions must be definitions, not references")
            val id = exclusiveReference(node, path, "action", "ref")
            actionReferences += LocatedReference(id, "$path.ref")
            return
        }

        val isDefinition = node.containsKey("type") || node.containsKey("event")
        if (!isDefinition) {
            if (definitionRequired) fail(path, "top-level actions must include an event")
            if (node.size != 1 || !node.containsKey("id")) {
                fail(
                    path,
                    "action must be a definition with an event or a reference with ref (legacy id-only references are also accepted)"
                )
            }
            val id = requiredIdentifier(node["id"], "$path.id", "action reference")
            actionReferences += LocatedReference(id, "$path.id")
            return
        }

        val id = requiredIdentifier(node["id"], "$path.id", "action definition")
        registerDefinition(actionDefinitions, id, path, "action")
        val event = node["event"] ?: fail("$path.event", "action definition must include an event")
        visitEvent(event, "$path.event")
    }

    private fun visitEvent(element: JsonElement, path: String) {
        val node = element as? JsonObject ?: fail(path, "event must be an object")
        node["view"]?.let { visitView(it, "$path.view", parentId = null) }
        node["thenDo"]?.let { visitEvent(it, "$path.thenDo") }
        node["onError"]?.let { visitEvent(it, "$path.onError") }
        node["thenDoOK"]?.let { visitEvent(it, "$path.thenDoOK") }
        node["thenDoKO"]?.let { visitEvent(it, "$path.thenDoKO") }
    }

    private fun exclusiveReference(
        node: JsonObject,
        path: String,
        kind: String,
        field: String,
        allowed: Set<String> = emptySet()
    ): String {
        if (node.keys.any { it != field && it !in allowed }) {
            fail(path, "$kind reference must contain only '$field'; reference and definition fields cannot be mixed")
        }
        return requiredIdentifier(node[field], "$path.$field", "$kind reference")
    }

    private fun requiredIdentifier(node: JsonElement?, path: String, description: String): String {
        val text = (node as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (text == null || text.isBlank()) fail(path, "$description id must be a non-empty string")
        return text
    }

    private fun registerDefinition(definitions: MutableMap<String, String>, id: String, path: String, kind: String) {
        definitions[id]?.let { firstPath ->
            fail("$path.id", "duplicate $kind id '$id' (first defined at $firstPath)")
        }
        definitions[id] = path
    }

    private fun addViewReference(id: String, path: String, parentId: String?) {
        viewReferences += LocatedReference(id, path)
        parentId?.let { containmentEdges.getOrPut(it) { linkedSetOf() }.add(id) }
    }

    private fun validateContainmentCycles() {
        val visited = mutableSetOf<String>()
        val active = mutableListOf<String>()
        val activeSet = mutableSetOf<String>()

        fun visit(id: String) {
            if (id in activeSet) {
                val start = active.indexOf(id)
                val cycle = (active.subList(start, active.size) + id).joinToString(" -> ")
                fail(viewDefinitions[id] ?: "$", "view containment cycle detected: $cycle")
            }
            if (!visited.add(id)) return

            active += id
            activeSet += id
            containmentEdges[id].orEmpty().filter { it in viewDefinitions }.forEach(::visit)
            active.removeAt(active.lastIndex)
            activeSet -= id
        }

        viewDefinitions.keys.forEach(::visit)
    }

    private fun fail(path: String, detail: String): Nothing = throw ConfigurationValidationException(path, detail)
}
