package it.sapienza.appinterpreter.ios

import it.sapienza.appinterpreter.alerts.AlertsViewModel
import it.sapienza.appinterpreter.calendar.CalendarEntry
import it.sapienza.appinterpreter.json.fillAddress
import it.sapienza.appinterpreter.json.getValue
import it.sapienza.appinterpreter.json.matchReplace
import it.sapienza.appinterpreter.json.valueAt
import it.sapienza.appinterpreter.markdown.Markdown
import it.sapienza.appinterpreter.model.Layout
import it.sapienza.appinterpreter.model.LayoutOrientation
import it.sapienza.appinterpreter.model.action.Action
import it.sapienza.appinterpreter.model.view_model.ButtonView
import it.sapienza.appinterpreter.model.view_model.CheckView
import it.sapienza.appinterpreter.model.view_model.FilesView
import it.sapienza.appinterpreter.model.view_model.FormView
import it.sapienza.appinterpreter.model.view_model.ImageView
import it.sapienza.appinterpreter.model.view_model.ListView
import it.sapienza.appinterpreter.model.view_model.TextView
import it.sapienza.appinterpreter.model.view_model.TextualView
import it.sapienza.appinterpreter.model.view_model.form_model.FilesInput
import it.sapienza.appinterpreter.model.view_model.form_model.PhotosInput
import it.sapienza.appinterpreter.model.view_model.helper.MView
import it.sapienza.appinterpreter.runtime.FormsState
import it.sapienza.appinterpreter.runtime.ListChoice
import it.sapienza.appinterpreter.runtime.ListsState
import it.sapienza.appinterpreter.runtime.ScreenViewModel
import it.sapienza.appinterpreter.runtime.ViewVisibility
import it.sapienza.appinterpreter.runtime.text
import it.sapienza.appinterpreter.runtime.usecase.PdfPageUseCase
import it.sapienza.appinterpreter.runtime.usecase.TextPackUseCase
import it.sapienza.appinterpreter.storage.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A one-off thing a screen asks the app to do, flattened for Swift (see [ScreenViewModel.Effect]). */
class ScreenEffect(
    /** openScreen, close, openUrl, copy, share, calendar, pdf or textPack. */
    val kind: String,
    val view: MView? = null,
    val payload: String? = null,
    val title: String? = null,
    val text: String? = null,
    val url: String? = null,
    val scheme: String? = null,
    /** The kept photos a share event sends along with its text. */
    val photos: List<String> = emptyList(),
    val calendar: CalendarEntry? = null,
    val page: PdfPageUseCase.Page? = null,
    val pack: TextPackUseCase.Pack? = null
)

/** The alert a screen shows. */
class AlertInfo(val text: String, val hasCancel: Boolean)

/**
 * A JSON object as Swift holds it. Kotlin's JsonObject is a Map, which Swift would copy into a dictionary of its own
 * and could not hand back; this holder keeps the object itself.
 */
class ItemData(val value: JsonObject?) {
    override fun toString(): String = value?.toString().orEmpty()
}

/** One styled run of Markdown text. */
class MarkdownRun(val text: String, val bold: Boolean, val italic: Boolean, val code: Boolean, val link: String?)

/** One block of Markdown: kind is heading, paragraph, item, quote or code. */
class MarkdownPart(
    val kind: String,
    val level: Int,
    val number: Int,
    val depth: Int,
    val runs: List<MarkdownRun>,
    val code: String
)

/** A form's values as Swift reads them: text, true/false and tags by mapping. */
class FormValues(private val values: Map<String, Any?>) {
    fun text(mapping: String): String? = values[mapping] as? String

    fun flag(mapping: String): Boolean = values[mapping] == true

    fun tags(mapping: String): List<String> =
        (values[mapping] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    /** The photo references a photos field holds. */
    fun photos(mapping: String): List<String> = tags(mapping)

    /** The files a files field holds. */
    fun files(mapping: String): List<Files.Attached> = Files.of(values[mapping] as? JsonArray)
}

/** What the SwiftUI renderer needs from the model, in types Swift reads without ceremony. */
object RenderKit {
    /** The type of [view]: layout, text, button, image, list, form, check, files, or reference. */
    fun kind(view: MView): String = when (view) {
        is Layout -> "layout"
        is TextView -> "text"
        is ButtonView -> "button"
        is ImageView -> "image"
        is ListView -> "list"
        is FormView -> "form"
        is CheckView -> "check"
        is FilesView -> "files"
        else -> "reference"
    }

    /** True for a layout that lays its views out in a row. */
    fun isHorizontal(view: MView): Boolean = view is Layout && view.orientation == LayoutOrientation.horizontal

    fun isVerticalLayout(view: MView): Boolean = view is Layout && view.orientation != LayoutOrientation.horizontal

    fun isHorizontalList(view: ListView): Boolean = view.orientation == LayoutOrientation.horizontal

    /** [views] resolved, without those that aren't shown with [data]: they take no space, and no gap either. */
    fun shown(screen: ScreenViewModel, views: List<MView>, data: ItemData): List<MView> = views.mapNotNull { view ->
        screen.resolve(view).takeIf { ViewVisibility.isShown(it, it.data ?: data.value, reference = view) }
    }

    /** True when [model], reached with [parentData], is drawn. */
    fun isShown(screen: ScreenViewModel, model: MView, parentData: ItemData): Boolean {
        val resolved = screen.resolve(model)
        return ViewVisibility.isShown(resolved, resolved.data ?: parentData.value, reference = model)
    }

    fun text(view: TextualView, data: ItemData): String = view.text(data.value)

    fun label(view: TextualView, data: ItemData): String = view.label?.matchReplace(data.value).orEmpty()

    fun fill(text: String?, data: ItemData): String = text?.matchReplace(data.value).orEmpty()

    /** The text size a text or button view asks for, or -1. */
    fun fontSize(view: TextualView): Double = view.textSize?.toDouble() ?: -1.0

    /** The address of an image view's picture with [data]; null when it has none, or a placeholder lacks a value. */
    fun imageUrl(view: ImageView, data: ItemData): String? {
        val mapped = view.mapping?.let { data.value?.getValue(it) }
        return (mapped ?: view.url)?.fillAddress(data.value)?.takeIf { it.isNotBlank() }
    }

    /** The addresses of an image view that maps to a list (a photos field), else null. */
    fun imageUrls(view: ImageView, data: ItemData): List<String>? =
        (view.mapping?.let { data.value?.valueAt(it) } as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        }

    fun imageWidth(view: ImageView): Int = view.size?.width ?: -1

    fun imageHeight(view: ImageView): Int = view.size?.height ?: -1

    fun textHeight(view: TextualView): Int = view.size?.height ?: -1

    /** The width a view asks for in its size (a layout's from its views'), or -1. */
    fun explicitWidth(view: MView): Int = width(view) ?: -1

    private fun width(view: MView): Int? = when (view) {
        is TextualView -> view.size?.width

        is ImageView -> view.size?.width

        is Layout -> when (view.orientation) {
            LayoutOrientation.horizontal ->
                view.views.mapNotNull { width(it) }.takeIf { it.size == view.views.size }?.sum()

            else -> view.views.mapNotNull { width(it) }.maxOrNull()
        }

        else -> null
    }

    /** A button that shows only its icon. */
    fun isIconOnly(view: MView): Boolean =
        view is ButtonView && view.icon != null && view.title.isNullOrBlank() && view.mapping == null

    /** The name of a button's icon (see ButtonIcon), or null. */
    fun iconName(view: ButtonView): String? = view.icon?.name

    fun isLongClick(screen: ScreenViewModel, view: MView): Boolean =
        screen.resolve(view.action)?.type?.name == "longclick"

    /** The data [view] brings with it, else [parent]. */
    fun dataFor(view: MView, parent: ItemData): ItemData = view.data?.let(::ItemData) ?: parent

    /** The data a screen shows, as it changes. */
    fun data(screen: ScreenViewModel): Flow<ItemData> = screen.data.map(::ItemData)

    fun currentData(screen: ScreenViewModel): ItemData = ItemData(screen.data.value)

    /** The data of the view that defines a screen. */
    fun viewData(view: MView): ItemData = ItemData(view.data)

    /** Runs [action] with [data] (the action's own data wins). */
    fun onAction(screen: ScreenViewModel, action: Action?, data: ItemData) = screen.onAction(action, data.value)

    /** The list [view] shows with [data], loading it on first use. */
    fun list(screen: ScreenViewModel, view: ListView, data: ItemData): ListsState.Entry =
        screen.lists.get(view, data.value)

    /** What a list shows now: its items as holders. */
    fun items(content: ListsState.Content): List<ItemData> = content.items.map(::ItemData)

    /** [content] as a flow of itself, for Swift to watch. */
    fun content(entry: ListsState.Entry): Flow<ListsState.Content> = entry.content

    fun currentContent(entry: ListsState.Entry): ListsState.Content = entry.content.value

    /** Markdown read into blocks to style. */
    fun markdown(text: String): List<MarkdownPart> = Markdown.parse(text).map { block ->
        when (block) {
            is Markdown.Block.Heading -> MarkdownPart("heading", block.level, 0, 0, runs(block.spans), "")

            is Markdown.Block.Paragraph -> MarkdownPart("paragraph", 0, 0, 0, runs(block.spans), "")

            is Markdown.Block.ListItem ->
                MarkdownPart("item", 0, block.number ?: 0, block.depth, runs(block.spans), "")

            is Markdown.Block.Quote -> MarkdownPart("quote", 0, 0, 0, runs(block.spans), "")

            is Markdown.Block.Code -> MarkdownPart("code", 0, 0, 0, emptyList(), block.text)
        }
    }

    private fun runs(spans: List<Markdown.Span>) = spans.map {
        MarkdownRun(it.text, it.bold, it.italic, it.code, it.link)
    }

    /** [screen]'s effects, flattened. */
    fun effects(screen: ScreenViewModel): Flow<ScreenEffect> = screen.effects.map { effect ->
        when (effect) {
            is ScreenViewModel.Effect.OpenScreen ->
                ScreenEffect(
                    "openScreen",
                    view = effect.view,
                    payload = effect.payload?.toString(),
                    title = effect.title
                )

            ScreenViewModel.Effect.Close -> ScreenEffect("close")

            is ScreenViewModel.Effect.OpenUrl -> ScreenEffect("openUrl", url = effect.url, scheme = effect.scheme)

            is ScreenViewModel.Effect.Copy -> ScreenEffect("copy", text = effect.text)

            is ScreenViewModel.Effect.Share -> ScreenEffect("share", text = effect.text, photos = effect.photos)

            is ScreenViewModel.Effect.AddToCalendar -> ScreenEffect("calendar", calendar = effect.entry)

            is ScreenViewModel.Effect.Pdf -> ScreenEffect("pdf", page = effect.page)

            is ScreenViewModel.Effect.TextPack -> ScreenEffect("textPack", pack = effect.pack)
        }
    }

    /** The alert [alerts] shows, if any. */
    fun alert(alerts: AlertsViewModel): Flow<AlertInfo?> = alerts.alertState.map { alert ->
        alert?.let { AlertInfo(it.text, hasCancel = it.onCancel != null) }
    }

    /** True when a stored list offers orders or a tag filter above its items. */
    fun hasControls(view: ListView): Boolean =
        view.stored?.let { !it.sortOptions.isNullOrEmpty() || it.filterBy != null } == true

    /** The state of [form] showing [data]. */
    fun form(screen: ScreenViewModel, form: FormView, data: ItemData): FormsState.Form =
        screen.forms.state(form, data.value)

    fun values(form: FormsState.Form): Flow<FormValues> = form.values.map(::FormValues)

    fun currentValues(form: FormsState.Form): FormValues = FormValues(form.values.value)

    /** An empty text is no value, as on Android. */
    fun setText(screen: ScreenViewModel, form: FormView, data: ItemData, mapping: String, text: String) =
        screen.forms.set(form, data.value, mapping, text.ifEmpty { null })

    fun setFlag(screen: ScreenViewModel, form: FormView, data: ItemData, mapping: String, flag: Boolean) =
        screen.forms.set(form, data.value, mapping, flag)

    /** No tags is no value. */
    fun setTags(screen: ScreenViewModel, form: FormView, data: ItemData, mapping: String, tags: List<String>) =
        screen.forms.set(form, data.value, mapping, JsonArray(tags.map(::JsonPrimitive)).takeIf { it.isNotEmpty() })

    /** No photos is no value. */
    fun setPhotos(screen: ScreenViewModel, form: FormView, data: ItemData, mapping: String, photos: List<String>) =
        setTags(screen, form, data, mapping, photos)

    /** No files is no value. */
    fun setFiles(
        screen: ScreenViewModel,
        form: FormView,
        data: ItemData,
        mapping: String,
        files: List<Files.Attached>
    ) = screen.forms.set(form, data.value, mapping, JsonArray(files.map { it.toJson() }).takeIf { it.isNotEmpty() })

    /** The files a files view shows with [data]. */
    fun files(view: FilesView, data: ItemData): List<Files.Attached> = Files.of(data.value?.valueAt(view.mapping))

    /** How many photos or files [field] takes. */
    fun photoLimit(field: PhotosInput): Int = field.limit

    fun fileLimit(field: FilesInput): Int = field.limit

    fun submit(screen: ScreenViewModel, form: FormView, data: ItemData) = screen.onSubmit(form, data.value)

    /** The data's true/false value a tick box shows. */
    fun isChecked(view: CheckView, data: ItemData): Boolean = data.value?.getValue(view.mapping) == "true"

    fun checkTitle(view: CheckView, data: ItemData): String = view.title?.matchReplace(data.value).orEmpty()

    fun onCheck(screen: ScreenViewModel, view: CheckView, data: ItemData, checked: Boolean) =
        screen.onCheck(view, data.value, checked)

    /** The sort options a stored list offers, by title. */
    fun sortTitles(view: ListView): List<String> = view.stored?.sortOptions.orEmpty().map { it.title }

    fun filtersByTag(view: ListView): Boolean = view.stored?.filterBy != null

    fun choice(entry: ListsState.Entry): Flow<ListChoice> = entry.choice

    fun currentChoice(entry: ListsState.Entry): ListChoice = entry.choice.value

    fun choose(screen: ScreenViewModel, entry: ListsState.Entry, sortOption: Int, tag: String?) =
        screen.lists.choose(entry, ListChoice(sortOption, tag))

    fun swipeToStart(view: ListView): Action? = view.swipeToStart

    fun swipeToEnd(view: ListView): Action? = view.swipeToEnd

    /** The icon names of a list's swipe actions: a bin and a tick unless the list says otherwise. */
    fun swipeToStartIcon(view: ListView): String = view.swipeToStartIcon?.name ?: "delete"

    fun swipeToEndIcon(view: ListView): String = view.swipeToEndIcon?.name ?: "check"
}
