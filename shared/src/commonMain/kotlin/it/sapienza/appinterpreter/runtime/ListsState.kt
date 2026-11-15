package it.sapienza.appinterpreter.runtime

import it.sapienza.appinterpreter.model.view_model.ListView
import it.sapienza.appinterpreter.runtime.usecase.LoadListItemsUseCase
import it.sapienza.appinterpreter.utils.httpclient.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * A screen's lists, each loaded once in [scope]: cancelling the scope cancels their requests. A list of saved data
 * loads again whenever its collection changes, also while its screen is behind another.
 */
class ListsState(
    private val scope: CoroutineScope,
    private val loadItems: LoadListItemsUseCase,
    private val strings: RuntimeStrings = EnglishRuntimeStrings
) {

    /**
     * What a list shows: its items, a wait while they load, or why they couldn't. [canLoadMore] says there's another
     * page, which [loadingMore] is fetching; [moreErrorMessage] says why it couldn't. [refreshing] keeps the items on
     * screen while the first page loads again.
     */
    data class Content(
        val items: List<JsonObject>,
        val waiting: Boolean,
        val errorMessage: String?,
        val canLoadMore: Boolean = false,
        val loadingMore: Boolean = false,
        val moreErrorMessage: String? = null,
        val refreshing: Boolean = false,
        /** A stored list's tags in use, for its filter chips. */
        val tags: List<String> = emptyList()
    )

    class Entry internal constructor(internal val view: ListView, val data: JsonObject?) {
        internal val contentFlow =
            MutableStateFlow(Content(emptyList(), waiting = view.initData != null, errorMessage = null))
        val content: StateFlow<Content> = contentFlow.asStateFlow()
        internal var job: Job? = null
        internal var nextUrl: String? = null
        internal var pages = 0
        internal val choiceFlow = MutableStateFlow(ListChoice())

        /** The order and tag the user chose above the list. */
        val choice: StateFlow<ListChoice> = choiceFlow.asStateFlow()
    }

    /** By the list definition itself (its identity) and the data it shows. */
    private class Key(val view: ListView, val data: String?) {
        override fun equals(other: Any?) = other is Key && other.view === view && other.data == data
        override fun hashCode() = view.hashCode() * 31 + data.hashCode()
    }

    private val lists = mutableMapOf<Key, Entry>()

    /** Listens for saves once the screen shows saved data, and loads the lists of what changed again. */
    private var watchingSaves: Job? = null

    private val refreshingFlow = MutableStateFlow(false)

    /** True while [refresh] reloads the screen's lists. */
    val refreshing: StateFlow<Boolean> = refreshingFlow.asStateFlow()

    private val canRefreshFlow = MutableStateFlow(false)

    /** True once the screen shows a list that loads from a request, which [refresh] can load again. */
    val canRefresh: StateFlow<Boolean> = canRefreshFlow.asStateFlow()

    fun get(view: ListView, data: JsonObject?): Entry = lists.getOrPut(Key(view, data?.toString())) {
        if (view.initData != null) canRefreshFlow.value = true
        if (view.stored != null && watchingSaves == null) {
            watchingSaves = scope.launch {
                loadItems.storedChanges.collect { collection ->
                    lists.values.filter {
                        it.view.stored?.collection == collection
                    }.forEach { load(it, keepItems = true) }
                }
            }
        }
        Entry(view, data).also { load(it) }
    }

    fun retry(list: Entry) = load(list)

    /** The user picked an order or a tag above [list]: it loads again that way, keeping its items until then. */
    fun choose(list: Entry, choice: ListChoice) {
        if (choice == list.choiceFlow.value) return
        list.choiceFlow.value = choice
        load(list, keepItems = true)
    }

    /** Back on screen: lists that failed try again, since the connection may be back. */
    fun retryFailed() = lists.values.filter { it.content.value.errorMessage != null }.forEach { load(it) }

    /** Loads again, from the first page, every list that comes from a request (pull to refresh). */
    fun refresh() {
        val remote = lists.values.filter { it.view.initData != null }
        if (remote.isEmpty()) return
        refreshingFlow.value = true
        val jobs = remote.map { load(it, keepItems = true) }
        scope.launch {
            jobs.forEach { it.join() }
            refreshingFlow.value = false
        }
    }

    /** Fetches [list]'s next page, if it has one and isn't fetching it already. */
    fun loadMore(list: Entry) {
        val url = list.nextUrl ?: return
        val content = list.content.value
        if (content.loadingMore || content.waiting || content.refreshing ||
            list.pages >= LoadListItemsUseCase.MAX_PAGES
        ) {
            return
        }
        list.contentFlow.update { it.copy(loadingMore = true, moreErrorMessage = null) }
        list.job = scope.launch {
            try {
                val page = loadItems.nextPage(list.view, url, list.data)
                list.nextUrl = page.nextUrl
                list.pages += 1
                list.contentFlow.update {
                    it.copy(items = it.items + page.items, canLoadMore = canLoadMore(list), loadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                list.contentFlow.update {
                    it.copy(
                        loadingMore = false,
                        moreErrorMessage = loadItems.errorMessage(errorText(e), strings)
                    )
                }
            }
        }
    }

    private fun canLoadMore(list: Entry) = list.nextUrl != null && list.pages < LoadListItemsUseCase.MAX_PAGES

    private fun load(list: Entry, keepItems: Boolean = false): Job {
        list.job?.cancel()
        list.contentFlow.update {
            if (keepItems && it.errorMessage == null) {
                it.copy(refreshing = true, moreErrorMessage = null)
            } else {
                it.copy(
                    errorMessage = null,
                    waiting = list.view.initData != null,
                    moreErrorMessage = null,
                    loadingMore = false
                )
            }
        }
        return scope.launch {
            try {
                val page = loadItems.firstPage(list.view, list.data, list.choiceFlow.value)
                list.nextUrl = page.nextUrl
                list.pages = 1
                list.contentFlow.update {
                    it.copy(items = page.items, tags = page.tags, errorMessage = null, canLoadMore = canLoadMore(list))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                list.nextUrl = null
                list.contentFlow.update {
                    it.copy(
                        items = emptyList(),
                        errorMessage = loadItems.errorMessage(errorText(e), strings),
                        canLoadMore = false
                    )
                }
            }
            list.contentFlow.update { it.copy(waiting = false, refreshing = false, loadingMore = false) }
        }.also { list.job = it }
    }

    /** Why loading failed, as the user reads it: an HTTP error by its status, else the error's own words. */
    private fun errorText(e: Exception): String =
        (e as? HttpStatusException)?.let { strings.requestFailed(it.status) } ?: e.message ?: strings.errorOccurred
}
