package it.sapienza.appinterpreter.editor

import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.runtime.EnglishRuntimeStrings
import it.sapienza.appinterpreter.runtime.RuntimeStrings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The web images an app shows from its own JSON, and which of them don't load: an AI app often makes up an image's
 * address (a likely-sounding file name), and the user only sees an empty box. An address counts when an image view
 * has it as its url, or when it is the value of a field an image view shows ("url": "${photo}" or "mapping":
 * "photo") anywhere in the app's data, such as its initial items. Addresses that come from a request aren't known
 * until the app runs, so they aren't checked.
 */
object ImageLinks {
    /** At most this many addresses are checked, so a big catalogue doesn't wait on hundreds. */
    const val MAX = 60

    /** Each image address in [tree], once, with the JSON path ("$.mainView.stored.initialItems[3].photo") it is at. */
    fun find(tree: JsonElement): List<Pair<String, String>> {
        val fields = mutableSetOf<String>()
        val found = mutableListOf<Pair<String, String>>()
        walk(tree, "$") { path, obj ->
            if ((obj["type"] as? JsonPrimitive)?.contentOrNull != "image") return@walk
            val url = (obj["url"] as? JsonPrimitive)?.contentOrNull
            when {
                url == null -> Unit

                url.startsWith("https://") && "\${" !in url -> found += "$path.url" to url

                else -> WHOLE_PLACEHOLDER.matchEntire(url.trim())?.let {
                    fields +=
                        it.groupValues[1].substringAfterLast('.')
                }
            }
            (obj["mapping"] as? JsonPrimitive)?.contentOrNull?.let { fields += it.substringAfterLast('.') }
        }
        walk(tree, "$") { path, obj ->
            obj.forEach { (key, value) ->
                val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (key in fields && text != null && text.startsWith("https://")) found += "$path.$key" to text
            }
        }
        return found.distinctBy { it.second }
    }

    /**
     * The problems to show for [json]'s images that don't load, each placed at its address in the text. [status]
     * gives an address's HTTP status after redirects (null when it couldn't be reached at all).
     */
    suspend fun problems(
        json: String,
        strings: RuntimeStrings = EnglishRuntimeStrings,
        status: suspend (String) -> Int?
    ): List<EditorIssue> {
        val tree = runCatching { JsonText.parse(json) }.getOrNull() ?: return emptyList()
        val links = find(tree).take(MAX)
        if (links.isEmpty()) return emptyList()
        val offsets = ConfigurationChecker.pathOffsets(json)
        val results = coroutineScope { links.map { (_, url) -> async { status(url) } }.awaitAll() }
        return links.zip(results).mapNotNull { (link, code) ->
            val (path, url) = link
            if (code != null && code in 200..299) return@mapNotNull null
            EditorIssue(
                ConfigurationChecker.offsetOf(path, offsets),
                path,
                strings.imageDoesNotLoad(code, url)
            )
        }
    }

    private fun walk(element: JsonElement, path: String, visit: (String, JsonObject) -> Unit) {
        when (element) {
            is JsonObject -> {
                visit(path, element)
                element.forEach { (key, value) -> walk(value, "$path.$key", visit) }
            }

            is JsonArray -> element.forEachIndexed { index, value -> walk(value, "$path[$index]", visit) }

            else -> Unit
        }
    }

    // Every brace escaped: Android's regex engine refuses a bare "}".
    private val WHOLE_PLACEHOLDER = Regex("""\$\{([^\}|]+)\}""")
}
