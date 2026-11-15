package it.sapienza.appinterpreter.runtime

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.sapienza.appinterpreter.alerts.AlertsViewModel
import it.sapienza.appinterpreter.calendar.CalendarEntry
import it.sapienza.appinterpreter.calendar.CreateCalendarEventUseCase
import it.sapienza.appinterpreter.json.filledLines
import it.sapienza.appinterpreter.json.getObject
import it.sapienza.appinterpreter.json.getValue
import it.sapienza.appinterpreter.json.matchReplace
import it.sapienza.appinterpreter.json.valueAt
import it.sapienza.appinterpreter.model.ModelApplication
import it.sapienza.appinterpreter.model.action.Action
import it.sapienza.appinterpreter.model.event.AlertMessage
import it.sapienza.appinterpreter.model.event.CalendarEvent
import it.sapienza.appinterpreter.model.event.CopyText
import it.sapienza.appinterpreter.model.event.DeleteData
import it.sapienza.appinterpreter.model.event.Event
import it.sapienza.appinterpreter.model.event.GoBack
import it.sapienza.appinterpreter.model.event.ItemsExport
import it.sapienza.appinterpreter.model.event.OpenLink
import it.sapienza.appinterpreter.model.event.PdfItems
import it.sapienza.appinterpreter.model.event.RESTService
import it.sapienza.appinterpreter.model.event.SaveData
import it.sapienza.appinterpreter.model.event.ShareText
import it.sapienza.appinterpreter.model.event.ShowView
import it.sapienza.appinterpreter.model.event.TextPackItems
import it.sapienza.appinterpreter.model.event.UpdateData
import it.sapienza.appinterpreter.model.view_model.CheckView
import it.sapienza.appinterpreter.model.view_model.FormView
import it.sapienza.appinterpreter.model.view_model.StoredData
import it.sapienza.appinterpreter.model.view_model.form_model.TagsInput
import it.sapienza.appinterpreter.model.view_model.helper.MView
import it.sapienza.appinterpreter.runtime.usecase.DeleteItemsUseCase
import it.sapienza.appinterpreter.runtime.usecase.FetchJsonUseCase
import it.sapienza.appinterpreter.runtime.usecase.FindMissingApiKeysUseCase
import it.sapienza.appinterpreter.runtime.usecase.InitialFormValuesUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadListItemsUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadScreenUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadStoredItemsUseCase
import it.sapienza.appinterpreter.runtime.usecase.MissingValueException
import it.sapienza.appinterpreter.runtime.usecase.NoLongerSavedException
import it.sapienza.appinterpreter.runtime.usecase.OpenLinkUseCase
import it.sapienza.appinterpreter.runtime.usecase.PdfPageUseCase
import it.sapienza.appinterpreter.runtime.usecase.SaveItemUseCase
import it.sapienza.appinterpreter.runtime.usecase.SubmitFormUseCase
import it.sapienza.appinterpreter.runtime.usecase.TextPackUseCase
import it.sapienza.appinterpreter.runtime.usecase.UpdateItemUseCase
import it.sapienza.appinterpreter.storage.Photos
import it.sapienza.appinterpreter.utils.httpclient.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One screen of a JSON app. It loads the screen, keeps its forms ([FormsState]) and lists ([ListsState]), and runs
 * its events; the use cases (Koin provides them on Android, see AppModule) do the work. It outlives the screen's
 * recreation, and leaving the screen cancels its requests, which run in [viewModelScope]. Its state is plain
 * StateFlows, for Compose on Android or SwiftUI on iOS.
 *
 * [saved] starts with the screen's arguments: [EXTRA_VIEW_ID] names the view to show (none: the app's main view),
 * and [EXTRA_SCREEN_PAYLOAD] holds its data.
 */
class ScreenViewModel(
    private val saved: SavedStateHandle,
    val alerts: AlertsViewModel,
    private val fetchJson: FetchJsonUseCase,
    private val loadListItems: LoadListItemsUseCase,
    initialFormValues: InitialFormValuesUseCase,
    submitForm: SubmitFormUseCase,
    private val createCalendarEvent: CreateCalendarEventUseCase,
    private val saveItem: SaveItemUseCase,
    private val updateItem: UpdateItemUseCase,
    private val deleteItems: DeleteItemsUseCase,
    private val openLink: OpenLinkUseCase,
    findMissingApiKeys: FindMissingApiKeysUseCase,
    loadScreen: LoadScreenUseCase,
    private val strings: RuntimeStrings,
    private val pdfPage: PdfPageUseCase = PdfPageUseCase(),
    private val textPack: TextPackUseCase = TextPackUseCase()
) : ViewModel() {

    /** One-off things only the platform's screen can do. */
    sealed interface Effect {
        /** Open [view] with [payload]; [title], if any, replaces the app's name in its app bar. */
        class OpenScreen(val view: MView, val payload: JsonObject?, val title: String? = null) : Effect
        class AddToCalendar(val entry: CalendarEntry) : Effect

        /** Put [text] on the clipboard (the phone confirms it). */
        class Copy(val text: String) : Effect

        /** Close this screen, back to the one that opened it. */
        data object Close : Effect

        /** Hand [url] to the app for its [scheme]: https/http, geo, tel or mailto (see [OpenLinkUseCase]). */
        class OpenUrl(val url: String, val scheme: String) : Effect

        /** Open the share sheet on [text], with the kept [photos] (references) attached. */
        class Share(val text: String, val photos: List<String> = emptyList()) : Effect

        /** Make a PDF of [page] and open the share sheet on it (sending, saving or printing it). */
        class Pdf(val page: PdfPageUseCase.Page) : Effect

        /** Zip [pack] (a TextPack, named .zip so phones open it) and open the share sheet on it. */
        class TextPack(val pack: TextPackUseCase.Pack) : Effect
    }

    private val effectChannel = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    private val loaded = loadScreen(saved[EXTRA_VIEW_ID], saved[EXTRA_SCREEN_PAYLOAD])
        .also { if (it is LoadScreenUseCase.Result.Failed) alerts.show(it.message) }

    /** The parsed app, or null when it can't be shown (an alert says why). */
    val app: ModelApplication? = (loaded as? LoadScreenUseCase.Result.Ready)?.app

    /** The title the event that opened this screen gave it, shown instead of the app's name. */
    val title: String? = saved[EXTRA_SCREEN_TITLE]

    /** What this screen shows, or null when it can't be shown (an alert says why). */
    val screen: ScreenRuntimeState? = (loaded as? LoadScreenUseCase.Result.Ready)?.screen

    private val shownData = MutableStateFlow(screen?.data)

    /**
     * The data the screen shows: what it was opened with, then the saved object it is, as this screen changes it (a
     * detail screen's tick shows at once, and a second tap turns it back).
     */
    val data: StateFlow<JsonObject?> = shownData.asStateFlow()

    val forms =
        FormsState(saved, initialFormValues, submitForm, { form -> resolve(form.action)?.event is SaveData }, strings)
    val lists = ListsState(viewModelScope, loadListItems, strings)

    private val missing = MutableStateFlow(screen?.let { findMissingApiKeys() })

    /** The app's API keys this phone lacks: asked for before the screen shows, unless skipped. */
    val missingKeys: StateFlow<FindMissingApiKeysUseCase.Missing?> = missing.asStateFlow()

    /** The keys stay missing: the app opens without them, and what needs them can't load. */
    fun skipKeys() {
        missing.value = null
    }

    private val busy = MutableStateFlow(false)

    /** True while a request started by an event is running. */
    val loading: StateFlow<Boolean> = busy.asStateFlow()
    private var requests = 0

    /** [view] as this app defines it, following a reference to a reusable view. */
    fun resolve(view: MView): MView = app?.viewBy(view) ?: view

    /** [action] as this app defines it, following a reference to a reusable action. */
    fun resolve(action: Action?): Action? = action?.let { app?.actionBy(it) }

    /** Runs [action]'s event with the action's own data, or [data] when it has none. */
    fun onAction(action: Action?, data: JsonObject?) {
        val resolved = resolve(action) ?: return
        resolved.event?.let { onEvent(it, resolved.data ?: data) }
    }

    /**
     * The tags already used in the collection [form] saves to or changes, at [field]'s mapping, for the field to
     * suggest; none when the form doesn't save on the phone.
     */
    fun knownTags(form: FormView, field: TagsInput): List<String> {
        val collection = when (val event = resolve(form.action)?.event) {
            is SaveData -> event.collection
            is UpdateData -> event.collection
            else -> return emptyList()
        }
        return LoadStoredItemsUseCase.tagsIn(loadListItems.stored(StoredData(collection), null), field.mapping)
    }

    /** [view] was ticked ([checked]) or unticked: its action runs with the data where its value says so. */
    fun onCheck(view: CheckView, data: JsonObject?, checked: Boolean) {
        onAction(view.action, JsonObject(data.orEmpty() + (view.mapping to JsonPrimitive(checked))))
    }

    /**
     * Sends [form] to its action's event, once every required field is filled in. A form whose values are saved on the
     * phone as a new object starts empty again, ready for the next one; a form that changes one keeps its values.
     */
    fun onSubmit(form: FormView, data: JsonObject?) {
        val json = forms.submit(form, data) ?: return
        when (val event = resolve(form.action)?.event) {
            null -> Unit

            is SaveData -> if (store(event.thenDo) { saveItem(event, json, context = data) }) forms.reset(form, data)

            // A field the user emptied goes from the saved object, rather than keeping its old value. Only whole
            // fields of the object: a dotted mapping's emptied part stays.
            is UpdateData -> {
                val cleared = form.formElements.map { it.mapping }.filter { '.' !in it && it !in json }.toSet()
                store(event.thenDo) {
                    updateItem(
                        event,
                        json,
                        context = data,
                        cleared = cleared.takeIf {
                            event.mapping ==
                                null
                        }.orEmpty()
                    ).also(::follow)
                }
            }

            else -> perform(event, json)
        }
    }

    /** Runs [event] with its own data, or [current] when it has none (as an action's data does). */
    fun onEvent(event: Event, current: JsonObject?) = perform(event, event.data ?: current)

    /** Runs [event] with [data] as it is: a form's submitted values, which the event's own data doesn't replace. */
    private fun perform(event: Event, data: JsonObject?) {
        when (event) {
            is AlertMessage -> showAlert(event, data)

            // The view receives the part of the data the mapping selects, such as "meals.0".
            is ShowView -> openScreen(event, event.mapping?.let { data?.getObject(it) } ?: data)

            is RESTService -> request(event, data)

            is SaveData -> store(event.thenDo) { saveItem(event, data, context = data) }

            is UpdateData -> store(event.thenDo) { updateItem(event, data, context = data).also(::follow) }

            is DeleteData -> store(event.thenDo) { deleteItems(event, data).let { data } }

            is GoBack -> effectChannel.trySend(Effect.Close)

            is CopyText -> {
                val copied = event.mapping?.let { data?.getObject(it) } ?: data
                event.text.filledLines(copied).takeIf {
                    it.isNotBlank()
                }?.let { effectChannel.trySend(Effect.Copy(it)) }
            }

            is CalendarEvent -> when (
                val result = createCalendarEvent(
                    event,
                    event.mapping?.let { data?.getObject(it) } ?: data
                )
            ) {
                is CreateCalendarEventUseCase.Result.Ready -> effectChannel.trySend(Effect.AddToCalendar(result.entry))
                is CreateCalendarEventUseCase.Result.BadDate -> alerts.show(strings.calendarBadDate(result.value))
            }

            is OpenLink -> when (val result = openLink(event, event.mapping?.let { data?.getObject(it) } ?: data)) {
                is OpenLinkUseCase.Result.Ready -> effectChannel.trySend(Effect.OpenUrl(result.url, result.scheme))
                is OpenLinkUseCase.Result.Missing -> alerts.show(strings.valueMissing(result.path))
                is OpenLinkUseCase.Result.NotAllowed -> alerts.show(strings.linkNotAllowed(result.url))
            }

            is PdfItems -> export(event, data) { items, shown ->
                Effect.Pdf(pdfPage(event, items, shown, app?.name.orEmpty()))
            }

            is TextPackItems -> export(event, data) { items, shown ->
                Effect.TextPack(textPack(event, items, shown, app?.name.orEmpty()))
            }

            is ShareText -> {
                val shared = event.mapping?.let { data?.getObject(it) } ?: data
                val photos = event.photos?.let { path ->
                    when (val value = shared?.valueAt(path)) {
                        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
                        else -> emptyList()
                    }.filter(Photos::isPhoto)
                }.orEmpty()
                val text = event.text.filledLines(shared)
                if (text.isNotBlank() || photos.isNotEmpty()) effectChannel.trySend(Effect.Share(text, photos))
            }
        }
    }

    /** Writes out the saved objects [event] picks, with [data] (the part its mapping selects); without any, its emptyText. */
    private fun <E> export(
        event: E,
        data: JsonObject?,
        effect: (List<JsonObject>, JsonObject?) -> Effect
    ) where E : Event, E : ItemsExport {
        val shown = event.mapping?.let { data?.getObject(it) } ?: data
        val items = loadListItems.stored(StoredData(event.collection, event.where, event.sort), shown)
        val empty = event.emptyText?.matchReplace(shown)?.takeIf { items.isEmpty() && it.isNotBlank() }
        if (empty != null) alerts.show(empty) else effectChannel.trySend(effect(items, shown))
    }

    private fun showAlert(event: AlertMessage, data: JsonObject?) {
        val message = (event.mapping?.let { data?.getValue(it) } ?: event.value)?.matchReplace(data)
        val ok = event.thenDoOK?.let { next -> { onEvent(next, data) } }
        // An alert that does something on OK can always be declined: Cancel then just closes it.
        val cancel = event.thenDoKO?.let { next -> { onEvent(next, data) } } ?: if (ok != null) ({ }) else null
        alerts.show(message ?: strings.alertWithoutMessage, ok, cancel)
    }

    private fun openScreen(event: ShowView, payload: JsonObject?) {
        val definition = app?.viewBy(event.view) ?: return alerts.show(strings.viewMissing(event.view.id.orEmpty()))
        val data = payload ?: definition.data
        val title = event.title?.matchReplace(data)?.trim()?.takeIf { it.isNotEmpty() }
        effectChannel.trySend(Effect.OpenScreen(definition, data, title))
    }

    /**
     * [updated], a saved object just changed: when it is the one this screen shows (the same id), the screen shows its
     * new values, kept with the screen's arguments so they survive the process being stopped.
     */
    private fun follow(updated: JsonObject) {
        val shown = shownData.value ?: return
        val id = shown["id"] ?: return
        if (updated["id"] != id) return
        val now = JsonObject(shown + updated)
        shownData.value = now
        if (saved.contains(EXTRA_SCREEN_PAYLOAD)) saved[EXTRA_SCREEN_PAYLOAD] = now.toString()
    }

    /**
     * Saves, changes or deletes data on the phone with [change], then runs [thenDo] with what it gives back (the saved
     * object, or the data a delete had); false, after an alert says why, when it couldn't.
     */
    private fun store(thenDo: Event?, change: () -> JsonObject?): Boolean {
        val result = try {
            change()
        } catch (e: Exception) {
            alerts.show(
                if (e is NoLongerSavedException) {
                    strings.itemNoLongerSaved
                } else {
                    e.message
                        ?: strings.errorOccurred
                }
            )
            return false
        }
        thenDo?.let { onEvent(it, result) }
        return true
    }

    private fun request(event: RESTService, data: JsonObject?) {
        viewModelScope.launch {
            requests += 1
            busy.value = true
            try {
                val response = fetchJson(event, data)
                event.thenDo?.let { onEvent(it, response) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message =
                    (e as? MissingValueException)?.let { strings.valueMissing(it.path) }
                        ?: (e as? HttpStatusException)?.let { strings.requestFailed(it.status) }
                        ?: e.message
                        ?: strings.errorOccurred
                val onError = event.onError
                if (onError == null) {
                    alerts.show(message)
                } else {
                    // The error event gets the request's data and what went wrong, as ${error.message}.
                    val error = buildMap<String, JsonElement> {
                        put("message", JsonPrimitive(message))
                        (e as? HttpStatusException)?.let { put("status", JsonPrimitive(it.status)) }
                    }
                    onEvent(onError, JsonObject(data.orEmpty() + ("error" to JsonObject(error))))
                }
            } finally {
                requests -= 1
                busy.value = requests > 0
            }
        }
    }

    companion object {
        const val EXTRA_VIEW_ID = "idView"
        const val EXTRA_SCREEN_PAYLOAD = "screenPayload"
        const val EXTRA_SCREEN_TITLE = "screenTitle"
    }
}
