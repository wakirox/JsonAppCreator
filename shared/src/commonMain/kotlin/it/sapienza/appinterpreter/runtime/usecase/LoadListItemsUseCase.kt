package it.sapienza.appinterpreter.runtime.usecase

import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.getArray
import it.sapienza.appinterpreter.json.getValue
import it.sapienza.appinterpreter.model.event.NextPage
import it.sapienza.appinterpreter.model.event.RESTService
import it.sapienza.appinterpreter.model.view_model.ListView
import it.sapienza.appinterpreter.model.view_model.StoredData
import it.sapienza.appinterpreter.runtime.ListChoice
import it.sapienza.appinterpreter.runtime.RuntimeStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** A list's items: what the app saved, or from its request when it has one, else from its own data or the screen's. */
class LoadListItemsUseCase(private val fetchJson: FetchJsonUseCase, private val loadStored: LoadStoredItemsUseCase) {

    /** The saved collections that changed: the lists showing them load again. */
    val storedChanges: Flow<String> get() = loadStored.changes

    /**
     * Some of a list's items, and the URL of the ones after them, if its request has a nextPage and there are more;
     * for a stored list with a filter, the tags its items use.
     */
    class Page(val items: List<JsonObject>, val nextUrl: String?, val tags: List<String> = emptyList())

    /** The saved objects [stored] picks, in its order, for an event that uses them all (a PDF of them, say). */
    fun stored(stored: StoredData, data: JsonObject?): List<JsonObject> = loadStored(stored, data)

    suspend operator fun invoke(list: ListView, data: JsonObject?): List<JsonObject> = firstPage(list, data).items

    /** The first page, ordered and filtered as [choice] says when the list is a stored one. */
    suspend fun firstPage(list: ListView, data: JsonObject?, choice: ListChoice = ListChoice()): Page {
        list.stored?.let { stored ->
            return loadStored.load(stored, data, choice).let { Page(it.items, nextUrl = null, tags = it.tags) }
        }
        val request = list.initData ?: return Page(itemsIn(list, data ?: JsonObject(emptyMap())), nextUrl = null)
        return page(list, request, fetchJson.url(request, data), data)
    }

    /** The page at [url], a [Page.nextUrl] of [list]. */
    suspend fun nextPage(list: ListView, url: String, data: JsonObject?): Page =
        page(list, list.initData ?: return Page(emptyList(), nextUrl = null), url, data)

    private suspend fun page(list: ListView, request: RESTService, url: String, data: JsonObject?): Page {
        val response = fetchJson.fetch(url, request, data)
        val items = itemsIn(list, response)
        return Page(items, request.nextPage?.let { nextUrl(it, url, response, items.size) })
    }

    private fun itemsIn(list: ListView, source: JsonObject): List<JsonObject> {
        val array = list.mapping?.let { source.getArray(it) } ?: legacyArray(list) ?: return emptyList()
        return array.mapIndexed { index, item ->
            item as? JsonObject
                ?: throw IllegalArgumentException("Value $item at $index cannot be converted to JSONObject")
        }
    }

    /**
     * What the Android code always did when the mapping found no array but the list has its own data: read the
     * mapping itself as a JSON array, which for a path such as "books" fails with org.json's message.
     */
    private fun legacyArray(list: ListView): JsonArray? {
        if (list.data == null) return null
        val mapping = list.mapping ?: return null
        return runCatching { JsonText.parse(mapping) as? JsonArray }.getOrNull()
            ?: throw IllegalArgumentException(
                "Value $mapping of type java.lang.String cannot be converted to JSONArray"
            )
    }

    /** What the user reads when loading fails with [message], in [strings]' words. */
    fun errorMessage(message: String, strings: RuntimeStrings): String = when {
        message.contains("resolve host", ignoreCase = true) ||
            message.contains("failed to connect", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ->
            strings.loadFailedOffline

        else -> strings.loadFailed(message)
    }

    companion object {
        /** Pages a list loads at most, so a service that never says "no more" can't load forever. */
        const val MAX_PAGES = 50

        /** The URL after [url], which gave [count] items in [response], or null when there are no more. */
        fun nextUrl(next: NextPage, url: String, response: JsonObject, count: Int): String? {
            if (count == 0) return null
            next.urlFrom?.let { path ->
                return response.getValue(path)?.trim()?.takeIf { it.isNotEmpty() && it != url }
            }
            val parameter = next.parameter?.takeIf { it.isNotBlank() } ?: return null
            val step = next.step?.takeIf { it > 0 } ?: return null
            if (count < step) return null
            return withParameter(url, parameter, (parameterValue(url, parameter) ?: 0) + step)
        }

        private fun parameterValue(url: String, name: String): Int? =
            query(url).firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")?.toIntOrNull()

        private fun query(url: String): List<String> =
            url.substringAfter('?', "").substringBefore('#').split('&').filter { it.isNotEmpty() }

        /** [url] with its query parameter [name] set to [value], added at the end when it has none. */
        fun withParameter(url: String, name: String, value: Int): String {
            val fragment = url.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
            val base = url.substringBefore('#').substringBefore('?')
            val parameters = query(url)
            val updated = if (parameters.any { it.substringBefore('=') == name }) {
                parameters.map { if (it.substringBefore('=') == name) "$name=$value" else it }
            } else {
                parameters + "$name=$value"
            }
            return "$base?${updated.joinToString("&")}$fragment"
        }
    }
}
