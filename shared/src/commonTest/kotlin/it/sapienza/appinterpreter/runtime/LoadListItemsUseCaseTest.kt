package it.sapienza.appinterpreter.runtime

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.getValue
import it.sapienza.appinterpreter.model.view_model.ListView
import it.sapienza.appinterpreter.runtime.usecase.FetchJsonUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadListItemsUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadStoredItemsUseCase
import it.sapienza.appinterpreter.storage.AppData
import it.sapienza.appinterpreter.storage.MemoryAppStore
import it.sapienza.appinterpreter.utils.AppLibrary
import it.sapienza.appinterpreter.utils.AppParser
import it.sapienza.appinterpreter.utils.httpclient.JsonHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class LoadListItemsUseCaseTest {
    private val appData = AppData(MemoryAppStore())
    private val repository = AppRepository({
        """{ "name": "Lists", "version": "1.0", "schemaVersion": 3, "mainView": { "type": "text", "title": "Lists" } }"""
    })
    private val loadItems = LoadListItemsUseCase(
        FetchJsonUseCase(
            JsonHttpClient(
                MockEngine {
                    respond("""{ "results": [ { "title": "From the API" } ] }""")
                },
                "Appkin/test"
            )
        ),
        LoadStoredItemsUseCase(appData, repository)
    )

    private fun idea(title: String, appId: String) = JsonObject(
        mapOf(
            "title" to JsonPrimitive(title),
            "appId" to JsonPrimitive(appId)
        )
    )

    private fun list(view: String) = AppParser(
        """{ "name": "Lists", "version": "1.0", "schemaVersion": 3, "mainView": $view }"""
    ).parseApplication2().mainView as ListView

    @Test
    fun aListWithoutARequestTakesItsItemsFromTheScreensData() = runTest {
        val list =
            list("""{ "type": "list", "mapping": "books", "itemView": { "type": "text", "mapping": "title" } }""")

        val items =
            loadItems(
                list,
                JsonText.parse(
                    """{ "books": [ { "title": "Middlemarch" }, { "title": "The Leopard" } ] }"""
                ) as JsonObject
            )

        assertEquals(listOf("Middlemarch", "The Leopard"), items.map { it.getValue("title") })
    }

    @Test
    fun aListWithARequestTakesItsItemsFromTheAnswer() = runTest {
        val list =
            list(
                """{ "type": "list", "mapping": "results", "initData": { "url": "https://example.com/x" }, "itemView": { "type": "text" } }"""
            )

        assertEquals(listOf("From the API"), loadItems(list, null).map { it.getValue("title") })
    }

    @Test
    fun aStoredListShowsTheCurrentAppsSavedItemsThatMatchItsData() = runTest {
        val lists = AppLibrary.idOf("Lists")
        appData.add(lists, "ideas", idea("Dark mode", "a1"))
        appData.add(lists, "ideas", idea("Widgets", "a2"))
        appData.add(lists, "ideas", idea("Sync", "a1"))
        appData.add(AppLibrary.idOf("Another app"), "ideas", idea("Not this app's", "a1"))
        val list = list(
            """{ "type": "list", "stored": { "collection": "ideas", "where": { "appId": "${'$'}{id}" } }, "itemView": { "type": "text" } }"""
        )

        val items = loadItems(list, JsonText.parse("""{ "id": "a1" }""") as JsonObject)

        assertEquals(listOf("Dark mode", "Sync"), items.map { it.getValue("title") })
    }

    @Test
    fun aStoredListWithoutWhereShowsTheWholeCollectionAndNothingBeforeASave() = runTest {
        val list = list("""{ "type": "list", "stored": { "collection": "apps" }, "itemView": { "type": "text" } }""")
        assertEquals(emptyList(), loadItems(list, null))

        appData.add(AppLibrary.idOf("Lists"), "apps", JsonObject(mapOf("name" to JsonPrimitive("Rainy"))))

        assertEquals(listOf("Rainy"), loadItems(list, null).map { it.getValue("name") })
    }

    @Test
    fun whereMatchesNumbersAndBooleansByTheirText() = runTest {
        appData.add(
            AppLibrary.idOf("Lists"),
            "ideas",
            JsonObject(
                mapOf(
                    "title" to JsonPrimitive("Done"),
                    "done" to JsonPrimitive(true)
                )
            )
        )
        appData.add(
            AppLibrary.idOf("Lists"),
            "ideas",
            JsonObject(
                mapOf(
                    "title" to JsonPrimitive("Open"),
                    "done" to JsonPrimitive(false)
                )
            )
        )
        val list =
            list(
                """{ "type": "list", "stored": { "collection": "ideas", "where": { "done": false } }, "itemView": { "type": "text" } }"""
            )

        assertEquals(listOf("Open"), loadItems(list, null).map { it.getValue("title") })
    }

    @Test
    fun aMissingArrayIsEmptyUnlessTheListHasItsOwnData() = runTest {
        val plain = list("""{ "type": "list", "mapping": "books", "itemView": { "type": "text" } }""")
        assertEquals(emptyList(), loadItems(plain, null))
        // As it always was on Android: with data of its own, the mapping is read as a JSON array and fails.
        val withData =
            list("""{ "type": "list", "mapping": "books", "data": { "other": [] }, "itemView": { "type": "text" } }""")
        assertEquals(
            "Value books of type java.lang.String cannot be converted to JSONArray",
            assertFailsWith<IllegalArgumentException> { loadItems(withData, withData.data) }.message
        )
    }

    @Test
    fun connectionProblemsReadPlainly() {
        assertEquals(
            "Unable to load data. Check your connection and try again.",
            loadItems.errorMessage("Unable to resolve host \"example.com\"", EnglishRuntimeStrings)
        )
        assertEquals(
            "Unable to load data. Request failed with 500",
            loadItems.errorMessage("Request failed with 500", EnglishRuntimeStrings)
        )
    }
}
