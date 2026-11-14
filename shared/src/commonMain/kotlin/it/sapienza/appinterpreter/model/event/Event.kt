package it.sapienza.appinterpreter.model.event

import it.sapienza.appinterpreter.model.TypedSerializer
import it.sapienza.appinterpreter.model.view_model.StoredSort
import it.sapienza.appinterpreter.model.view_model.StoredSortsSerializer
import it.sapienza.appinterpreter.model.view_model.helper.MView
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** What an action does, chosen by its "type". */
@Serializable(with = EventSerializer::class)
sealed class Event {
    abstract val mapping: String?
    abstract val data: JsonObject?
}

@Serializable
class AlertMessage(
    val value: String? = null,
    val thenDoOK: Event? = null,
    val thenDoKO: Event? = null,
    override val mapping: String? = null,
    override val data: JsonObject? = null
) : Event()

/**
 * Opens the phone's calendar app on a new event, filled in from the configuration and the current data; the user
 * reviews and saves it there. [date] is dd/MM/yyyy and [time] HH:mm (none makes an all-day event); all text may use
 * ${field} placeholders.
 */
@Serializable
class CalendarEvent(
    val title: String,
    val date: String,
    val time: String? = null,
    @Serializable(with = LenientIntSerializer::class) val durationMinutes: Int? = null,
    val location: String? = null,
    val description: String? = null,
    override val mapping: String? = null,
    override val data: JsonObject? = null
) : Event()

/**
 * Hands [url] to the phone: a web page opens in the browser, geo: a map, tel: the dialer (nothing is called) and
 * mailto: an email app. Placeholders in it are filled in from the current data.
 */
@Serializable
class OpenLink(val url: String, override val mapping: String? = null, override val data: JsonObject? = null) : Event()

/** Opens the phone's share sheet on [text], filled in from the current data. */
@Serializable
class ShareText(
    val text: String,
    /** Where the data keeps photos to share with the text (a photos field's list, or one photo): sent as attachments. */
    val photos: String? = null,
    override val mapping: String? = null,
    override val data: JsonObject? = null
) : Event()

/** Fills lists with data from a service, then runs [thenDo]. */
sealed class DataService : Event() {
    abstract val thenDo: Event?
}

@Serializable
class RESTService(
    val url: String,
    val method: CallServiceMethod? = CallServiceMethod.get,
    override val thenDo: Event? = null,
    override val mapping: String? = null,
    override val data: JsonObject? = null,
    /** A list's initData only: how to ask for the items after these. */
    val nextPage: NextPage? = null,
    /** Request headers, such as "Authorization"; values may use ${field} placeholders and __API_KEY__ ones. */
    val headers: Map<String, String>? = null,
    /** An event's only: what runs when the request fails, with the data and "error" ({ message, status }). */
    val onError: Event? = null
) : DataService()

/**
 * Saves the current data (the part [mapping] selects, if any) as a new object of the app's [collection] on the phone,
 * with [fields] added, then runs [thenDo] with the saved object. Its ${field} placeholders are filled from the data
 * the form showed (for a form: the screen's or the row's), then from the submitted values. With [unique], an object
 * already saved with the same value in that field is replaced instead (keeping its id), so saving twice keeps one.
 */
@Serializable
class SaveData(
    val collection: String,
    val fields: JsonObject? = null,
    val unique: String? = null,
    val thenDo: Event? = null,
    override val mapping: String? = null,
    override val data: JsonObject? = null
) : Event()

/**
 * Changes the saved object of [collection] whose "id" is [id] (by default the current data's): the current data (the
 * part [mapping] selects, if any; a form's submitted values) and [fields] replace its values, then [thenDo] runs with
 * the changed object. Its "id" and "createdAt" never change. Placeholders are filled as in [SaveData].
 */
@Serializable
class UpdateData(
    val collection: String,
    val id: String = "\${id}",
    val fields: JsonObject? = null,
    val thenDo: Event? = null,
    override val mapping: String? = null,
    override val data: JsonObject? = null
) : Event()

/**
 * Removes from [collection] the saved object whose "id" is [id] (by default the current data's), or, with [where],
 * every object that matches it as a stored list's would; then runs [thenDo] with the current data. Nothing saved
 * that matches is not a mistake.
 */
@Serializable
class DeleteData(
    val collection: String,
    val id: String = "\${id}",
    val where: JsonObject? = null,
    val thenDo: Event? = null,
    override val mapping: String? = null,
    override val data: JsonObject? = null
) : Event()

/**
 * How a list asks for more items: either the query [parameter] (such as "skip") grows by [step], the page size,
 * from its value in the first URL (0 without one), until a page has fewer than [step] items; or the next page's URL
 * is the response's value at [urlFrom], until there is none.
 */
@Serializable
class NextPage(val parameter: String? = null, val step: Int? = null, val urlFrom: String? = null)

/** Copies [text] to the clipboard, filled from the data; a line whose placeholders all lack a value is left out. */
@Serializable
class CopyText(val text: String, override val mapping: String? = null, override val data: JsonObject? = null) : Event()

/**
 * Makes a PDF of the saved objects of [collection] that match [where], in [sort]'s order: [title] at the top, filled
 * from the current data, then [text] for each object as Markdown, with its [photos] below it. The share sheet opens
 * on the PDF, to send, save or print it. Without objects, [emptyText] is shown instead.
 */
@Serializable
class PdfItems(
    override val collection: String,
    override val where: JsonObject? = null,
    @Serializable(with = StoredSortsSerializer::class) override val sort: List<StoredSort>? = null,
    override val title: String? = null,
    override val text: String,
    override val photos: String? = null,
    override val files: String? = null,
    override val emptyText: String? = null,
    /** One page as tall as everything, as a phone shows it best, instead of A4 pages. */
    val pageless: Boolean = false,
    override val mapping: String? = null,
    override val data: JsonObject? = null
) : Event(),
    ItemsExport

/**
 * Makes a TextPack (a zipped TextBundle: Markdown with its files) of the saved objects, picked and written as a
 * [PdfItems] event's are: [text] for each object, its [photos] as files in the pack's assets/, linked from the text.
 * Markdown apps and editors open it; the share sheet opens on it.
 */
@Serializable
class TextPackItems(
    override val collection: String,
    override val where: JsonObject? = null,
    @Serializable(with = StoredSortsSerializer::class) override val sort: List<StoredSort>? = null,
    override val title: String? = null,
    override val text: String,
    override val photos: String? = null,
    override val files: String? = null,
    override val emptyText: String? = null,
    override val mapping: String? = null,
    override val data: JsonObject? = null
) : Event(),
    ItemsExport

/** What an event that writes out saved objects ([PdfItems], [TextPackItems]) says about which, and how. */
interface ItemsExport {
    val collection: String
    val where: JsonObject?
    val sort: List<StoredSort>?
    val title: String?
    val text: String
    val photos: String?

    /** Where each object keeps its attached files (a files field's list): listed in a PDF, packed in a TextPack. */
    val files: String?
    val emptyText: String?
}

/** Closes the current screen and goes back to the one that opened it, as the phone's Back does. */
@Serializable
class GoBack(override val mapping: String? = null, override val data: JsonObject? = null) : Event()

@Serializable
class ShowView(
    val view: MView,
    override val mapping: String? = null,
    override val data: JsonObject? = null,
    /** The opened screen's title in the app bar, instead of the app's name; placeholders come from its data. */
    val title: String? = null
) : Event()

object EventSerializer : TypedSerializer<Event>(
    "Event",
    listOf(
        TypedSerializer.Type("rest", RESTService.serializer()) { it is RESTService },
        TypedSerializer.Type("alert", AlertMessage.serializer()) { it is AlertMessage },
        TypedSerializer.Type("show", ShowView.serializer()) { it is ShowView },
        TypedSerializer.Type("calendar", CalendarEvent.serializer()) { it is CalendarEvent },
        TypedSerializer.Type("open", OpenLink.serializer()) { it is OpenLink },
        TypedSerializer.Type("share", ShareText.serializer()) { it is ShareText },
        TypedSerializer.Type("save", SaveData.serializer()) { it is SaveData },
        TypedSerializer.Type("update", UpdateData.serializer()) { it is UpdateData },
        TypedSerializer.Type("delete", DeleteData.serializer()) { it is DeleteData },
        TypedSerializer.Type("back", GoBack.serializer()) { it is GoBack },
        TypedSerializer.Type("copy", CopyText.serializer()) { it is CopyText },
        TypedSerializer.Type("pdf", PdfItems.serializer()) { it is PdfItems },
        TypedSerializer.Type("textpack", TextPackItems.serializer()) { it is TextPackItems }
    )
)

@Serializable
enum class CallServiceMethod {
    get,
    post,
    put,
    delete;

    /** Whether the request sends its data as a JSON body. */
    val sendsBody: Boolean get() = this == post || this == put
}
