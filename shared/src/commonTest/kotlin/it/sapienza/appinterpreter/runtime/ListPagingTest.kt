package it.sapienza.appinterpreter.runtime

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.getValue
import it.sapienza.appinterpreter.model.event.NextPage
import it.sapienza.appinterpreter.model.view_model.ListView
import it.sapienza.appinterpreter.runtime.usecase.FetchJsonUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadListItemsUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadStoredItemsUseCase
import it.sapienza.appinterpreter.storage.AppData
import it.sapienza.appinterpreter.storage.MemoryAppStore
import it.sapienza.appinterpreter.utils.AppParser
import it.sapienza.appinterpreter.utils.httpclient.JsonHttpClient
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject

/** Lists that load more pages as they're scrolled, and load again when pulled. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListPagingTest {
    private val requested = mutableListOf<String>()
    private var failNext = false

    /** While set, requests wait for it before answering, so a test can look at a list while it loads. */
    @Volatile private var hold: CompletableDeferred<Unit>? = null

    /** 7 works in all, served [limit] at a time from [skip]. */
    private val engine = MockEngine { request ->
        requested += request.url.toString()
        hold?.await()
        if (failNext) {
            failNext = false
            return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable)
        }
        val skip = request.url.parameters["skip"]?.toInt() ?: 0
        val limit = request.url.parameters["limit"]!!.toInt()
        val items = (skip until minOf(skip + limit, 7)).joinToString { """{ "id": $it }""" }
        respond("""{ "data": [ $items ] }""")
    }
    private val loadItems = LoadListItemsUseCase(
        FetchJsonUseCase(JsonHttpClient(engine, "Appkin/test")),
        LoadStoredItemsUseCase(AppData(MemoryAppStore()), AppRepository({ error("no saved lists here") }))
    )

    private fun list(initData: String) = AppParser(
        """{ "name": "Pages", "version": "1.0", "schemaVersion": 3,
             "mainView": { "type": "list", "mapping": "data", "initData": $initData, "itemView": { "type": "text", "mapping": "id" } } }"""
    ).parseApplication2().mainView as ListView

    /** The engine answers on its own thread: wait for [entry] to show what [done] expects. */
    private suspend fun settled(
        entry: ListsState.Entry,
        done: (ListsState.Content) -> Boolean = {
            !it.waiting &&
                !it.loadingMore &&
                !it.refreshing
        }
    ) = withContext(Dispatchers.Default) { withTimeout(10_000) { entry.content.first(done) } }

    private val paged = list(
        """{ "url": "https://example.com/works?q=${'$'}{q}&limit=3", "nextPage": { "parameter": "skip", "step": 3 } }"""
    )
    private val search = JsonText.parse("""{ "q": "fuji" }""") as JsonObject

    @Test
    fun pagesFollowEachOtherUntilAShortOne() = runTest(UnconfinedTestDispatcher()) {
        val lists = ListsState(this, loadItems)
        val entry = lists.get(paged, search)
        assertEquals(3, settled(entry).items.size)
        assertTrue(entry.content.value.canLoadMore)

        repeat(3) {
            lists.loadMore(entry)
            settled(entry)
        }

        val content = entry.content.value
        assertEquals((0..6).map { "$it" }, content.items.map { it.getValue("id") })
        assertFalse(content.canLoadMore, "the third page had 1 item, fewer than 3")
        assertEquals(
            listOf(
                "https://example.com/works?q=fuji&limit=3",
                "https://example.com/works?q=fuji&limit=3&skip=3",
                "https://example.com/works?q=fuji&limit=3&skip=6"
            ),
            requested
        )
    }

    @Test
    fun aPageThatFailsCanBeTriedAgainAndKeepsTheItems() = runTest(UnconfinedTestDispatcher()) {
        val lists = ListsState(this, loadItems)
        val entry = lists.get(paged, search)
        settled(entry)
        failNext = true

        lists.loadMore(entry)
        assertEquals(3, settled(entry).items.size)
        assertTrue(entry.content.value.moreErrorMessage!!.startsWith("Unable to load data."))

        lists.loadMore(entry)
        assertEquals(6, settled(entry).items.size)
        assertNull(entry.content.value.moreErrorMessage)
    }

    @Test
    fun pullingLoadsTheFirstPageAgain() = runTest(UnconfinedTestDispatcher()) {
        val lists = ListsState(this, loadItems)
        assertFalse(lists.canRefresh.value)
        val entry = lists.get(paged, search)
        settled(entry)
        lists.loadMore(entry)
        assertEquals(6, settled(entry).items.size)
        assertTrue(lists.canRefresh.value)

        // The first page is held back until the list has been seen loading again with its items.
        val firstPage = CompletableDeferred<Unit>().also { hold = it }
        lists.refresh()
        assertTrue(lists.refreshing.value)
        assertEquals(6, entry.content.value.items.size, "the items stay while the list loads again")
        hold = null
        firstPage.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(10_000) { lists.refreshing.first { !it } } }

        assertEquals(3, entry.content.value.items.size, "back to the first page")
        assertTrue(entry.content.value.canLoadMore)
        assertEquals("https://example.com/works?q=fuji&limit=3", requested.last())
    }

    @Test
    fun aListWithoutNextPageLoadsOnePage() = runTest(UnconfinedTestDispatcher()) {
        val lists = ListsState(this, loadItems)
        val entry = lists.get(list("""{ "url": "https://example.com/works?limit=3" }"""), null)
        assertEquals(3, settled(entry).items.size)
        assertFalse(entry.content.value.canLoadMore)
    }

    @Test
    fun theNextPagesUrlCanComeFromTheResponse() {
        val next = NextPage(urlFrom = "info.next")
        val withNext = JsonText.parse("""{ "info": { "next": "https://example.com/p2" } }""") as JsonObject
        assertEquals(
            "https://example.com/p2",
            LoadListItemsUseCase.nextUrl(next, "https://example.com/p1", withNext, 5)
        )
        assertNull(LoadListItemsUseCase.nextUrl(next, "https://example.com/p2", withNext, 5), "the same page again")
        assertNull(LoadListItemsUseCase.nextUrl(next, "https://example.com/p1", JsonObject(emptyMap()), 5))
        assertNull(
            LoadListItemsUseCase.nextUrl(next, "https://example.com/p1", withNext, 0),
            "an empty page ends the list"
        )
        val relativeNext = JsonText.parse("""{ "info": { "next": "/p2?cursor=abc" } }""") as JsonObject
        assertEquals(
            "https://example.com/p2?cursor=abc",
            LoadListItemsUseCase.nextUrl(next, "https://example.com/p1", relativeNext, 5)
        )
        val crossOriginNext =
            JsonText.parse("""{ "info": { "next": "https://evil.example.org/p2" } }""") as JsonObject
        assertNull(
            LoadListItemsUseCase.nextUrl(next, "https://example.com/p1", crossOriginNext, 5),
            "cross-origin pagination links are rejected"
        )
        val downgradeNext = JsonText.parse("""{ "info": { "next": "http://example.com/p2" } }""") as JsonObject
        assertNull(
            LoadListItemsUseCase.nextUrl(next, "https://example.com/p1", downgradeNext, 5),
            "protocol downgrade pagination links are rejected"
        )
    }

    @Test
    fun theParameterIsReplacedOrAdded() {
        assertEquals(
            "https://x/a?skip=40&limit=20",
            LoadListItemsUseCase.withParameter("https://x/a?skip=20&limit=20", "skip", 40)
        )
        assertEquals(
            "https://x/a?limit=20&skip=20",
            LoadListItemsUseCase.withParameter("https://x/a?limit=20", "skip", 20)
        )
        assertEquals("https://x/a?skip=20#top", LoadListItemsUseCase.withParameter("https://x/a#top", "skip", 20))
        val response = JsonObject(emptyMap())
        assertEquals(
            "https://x/a?skip=40&limit=20",
            LoadListItemsUseCase.nextUrl(NextPage("skip", 20), "https://x/a?skip=20&limit=20", response, 20)
        )
        assertNull(LoadListItemsUseCase.nextUrl(NextPage("skip", 20), "https://x/a?skip=20", response, 19))
    }
}
