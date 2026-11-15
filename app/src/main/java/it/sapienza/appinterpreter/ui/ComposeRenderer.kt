@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package it.sapienza.appinterpreter.ui

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.activity.createIntentPhotoActivity
import it.sapienza.appinterpreter.extensions.toDate
import it.sapienza.appinterpreter.form.extensions.stringValue
import it.sapienza.appinterpreter.json.fillAddress
import it.sapienza.appinterpreter.json.getValue
import it.sapienza.appinterpreter.json.matchReplace
import it.sapienza.appinterpreter.json.optString
import it.sapienza.appinterpreter.json.valueAt
import it.sapienza.appinterpreter.model.Layout
import it.sapienza.appinterpreter.model.LayoutOrientation
import it.sapienza.appinterpreter.model.action.Action
import it.sapienza.appinterpreter.model.action.ActionType
import it.sapienza.appinterpreter.model.view_model.ButtonIcon
import it.sapienza.appinterpreter.model.view_model.ButtonView
import it.sapienza.appinterpreter.model.view_model.CheckView
import it.sapienza.appinterpreter.model.view_model.FilesView
import it.sapienza.appinterpreter.model.view_model.FormView
import it.sapienza.appinterpreter.model.view_model.ImageScale
import it.sapienza.appinterpreter.model.view_model.ImageView
import it.sapienza.appinterpreter.model.view_model.ListView
import it.sapienza.appinterpreter.model.view_model.TextFormat
import it.sapienza.appinterpreter.model.view_model.TextView
import it.sapienza.appinterpreter.model.view_model.TextualView
import it.sapienza.appinterpreter.model.view_model.form_model.CheckBox
import it.sapienza.appinterpreter.model.view_model.form_model.DateInput
import it.sapienza.appinterpreter.model.view_model.form_model.FilesInput
import it.sapienza.appinterpreter.model.view_model.form_model.HiddenInput
import it.sapienza.appinterpreter.model.view_model.form_model.PhotosInput
import it.sapienza.appinterpreter.model.view_model.form_model.Selector
import it.sapienza.appinterpreter.model.view_model.form_model.TagsInput
import it.sapienza.appinterpreter.model.view_model.form_model.TextInput
import it.sapienza.appinterpreter.model.view_model.form_model.TimeInput
import it.sapienza.appinterpreter.model.view_model.form_model.helpers.TextType
import it.sapienza.appinterpreter.model.view_model.helper.MView
import it.sapienza.appinterpreter.model.view_model.helper.TextStyle
import it.sapienza.appinterpreter.runtime.ListsState
import it.sapienza.appinterpreter.runtime.ScreenViewModel
import it.sapienza.appinterpreter.runtime.TagSuggestions
import it.sapienza.appinterpreter.runtime.ViewVisibility
import it.sapienza.appinterpreter.runtime.text
import it.sapienza.appinterpreter.storage.Files
import it.sapienza.appinterpreter.storage.Photos
import it.sapienza.appinterpreter.utils.FileAttachments
import it.sapienza.appinterpreter.utils.FormDates
import it.sapienza.appinterpreter.utils.PhotoStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val HORIZONTAL_LAZY_THRESHOLD = 8

/** The screen's margins: everything lines up on the same left edge, 16 dp in. */
private val PAGE_PADDING = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/** The space between one view and the next. */
private val VIEW_SPACING = 12.dp

/** The gap between the views of a list card's layouts: tighter than a screen's, so cards stay compact. */
private val CARD_SPACING = 4.dp

/** The gap a vertical layout leaves between its views: the screen's, or a card's inside list items. */
private val LocalViewSpacing = compositionLocalOf { VIEW_SPACING }

/** The screen being drawn: its app, state and events. */
val LocalScreen = staticCompositionLocalOf<ScreenViewModel> { error("RendererScreen needs LocalScreen") }

@Composable
fun RendererScreen(model: MView, data: JsonObject? = model.data, loading: Boolean) {
    val screen = LocalScreen.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, screen) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) screen.lists.retryFailed()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val refreshing by screen.lists.refreshing.collectAsState()
    val canRefresh by screen.lists.canRefresh.collectAsState()
    val pull = rememberPullToRefreshState()
    // Lists loaded from a request load again when the screen is pulled down.
    Box(
        Modifier.fillMaxSize().testTag("renderer-screen")
            .pullToRefresh(
                isRefreshing = refreshing,
                state = pull,
                enabled = canRefresh,
                onRefresh = screen.lists::refresh
            )
    ) {
        RenderRoot(model, data)
        if (canRefresh) {
            PullToRefreshDefaults.Indicator(
                state = pull,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        if (loading) {
            Card(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(20.dp)
            ) {
                LoadingIndicator(Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun RenderRoot(model: MView, screenData: JsonObject?) {
    val screen = LocalScreen.current
    val resolved = screen.resolve(model)
    val data = remember(resolved, resolved.data, screenData) { screenData ?: resolved.data }

    when {
        resolved is Layout && resolved.orientation != LayoutOrientation.horizontal -> {
            val children = remember(resolved, data) { screen.shownViews(resolved.views, data) }
            val listStates = children.map { child ->
                if (child is ListView && child.orientation != LayoutOrientation.horizontal) {
                    val childData = remember(child, child.data, data) { child.data ?: data }
                    screen.lists.get(child, childData)
                } else {
                    null
                }
            }
            // The lists' content, observed here: the lazy column's items are built from it.
            val listContents = listStates.map { entry -> entry?.let { it.content.collectAsState().value } }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PAGE_PADDING,
                verticalArrangement = Arrangement.spacedBy(VIEW_SPACING)
            ) {
                children.forEachIndexed { index, child ->
                    val listState = listStates[index]
                    val listContent = listContents[index]
                    if (child is ListView && listState != null && listContent != null) {
                        verticalListItems(child, listState, listContent)
                    } else {
                        item(key = "root-$index-${child.id.orEmpty()}") {
                            RenderNode(child, data, Modifier.columnChild(child))
                        }
                    }
                }
            }
        }

        resolved is ListView && resolved.orientation != LayoutOrientation.horizontal -> {
            val entry = screen.lists.get(resolved, data)
            val content by entry.content.collectAsState()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PAGE_PADDING,
                verticalArrangement = Arrangement.spacedBy(VIEW_SPACING)
            ) {
                verticalListItems(resolved, entry, content)
            }
        }

        else -> {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PAGE_PADDING),
                verticalArrangement = Arrangement.spacedBy(VIEW_SPACING)
            ) {
                RenderNode(resolved, data, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RenderNode(
    model: MView,
    parentData: JsonObject?,
    modifier: Modifier = Modifier,
    allowImagePreview: Boolean = true
) {
    val resolved = LocalScreen.current.resolve(model)
    val data = remember(resolved, resolved.data, parentData) { resolved.data ?: parentData }
    if (!ViewVisibility.isShown(resolved, data, reference = model)) return
    when (resolved) {
        is Layout -> RenderLayout(resolved, data, modifier, allowImagePreview)
        is ButtonView -> RenderButton(resolved, data, modifier)
        is TextView -> RenderText(resolved, data, modifier)
        is ImageView -> RenderImage(resolved, data, modifier, allowImagePreview)
        is ListView -> RenderList(resolved, data, modifier)
        is FormView -> RenderForm(resolved, data, modifier)
        is CheckView -> RenderCheck(resolved, data, modifier)
        is FilesView -> RenderFiles(resolved, data, modifier)
    }
}

/** An object's attached files, a chip each: a tap opens the file in an app that reads it. */
@Composable
private fun RenderFiles(view: FilesView, data: JsonObject?, modifier: Modifier) {
    val files = Files.of(data?.valueAt(view.mapping))
    if (files.isEmpty()) return
    FileChips(files, modifier)
}

@Composable
private fun FileChips(
    files: List<Files.Attached>,
    modifier: Modifier = Modifier,
    onRemove: ((Files.Attached) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        files.forEach { file ->
            InputChip(
                selected = false,
                onClick = { scope.launch { FileAttachments.open(context, file) } },
                label = {
                    Text(
                        "📎 " + file.name + file.size?.let {
                            " · " + Files.sizeText(it)
                        }.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = onRemove?.let { remove ->
                    {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.field_remove_named, file.name),
                            modifier = Modifier.size(InputChipDefaults.IconSize).clickable { remove(file) }
                        )
                    }
                },
                modifier = Modifier.testTag("file-${file.name}")
            )
        }
    }
}

@Composable
private fun RenderLayout(layout: Layout, data: JsonObject?, modifier: Modifier, allowImagePreview: Boolean) {
    val screen = LocalScreen.current
    val resolvedChildren = screen.shownViews(layout.views, data)
    if (layout.orientation == LayoutOrientation.horizontal) {
        // A row with an icon button (such as Delete beside a tick box) centres its views on it; other rows keep them at the top.
        val centred = resolvedChildren.any { it.isIconOnly() }
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = if (centred) Alignment.CenterVertically else Alignment.Top
        ) {
            resolvedChildren.forEach { child ->
                RenderNode(child, data, Modifier.rowChild(this, child), allowImagePreview)
            }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalViewSpacing.current)) {
            resolvedChildren.forEach { child ->
                RenderNode(child, data, Modifier.columnChild(child), allowImagePreview)
            }
        }
    }
}

/** [views] resolved, without those that aren't shown with [data]: they take no space, and no gap either. */
private fun ScreenViewModel.shownViews(views: List<MView>, data: JsonObject?): List<MView> =
    views.mapNotNull { view -> resolve(view).takeIf { ViewVisibility.isShown(it, it.data ?: data, reference = view) } }

private fun Modifier.columnChild(model: MView): Modifier {
    if (model.isIconOnly()) return this
    val width = model.explicitWidth()
    return if (width == null) fillMaxWidth() else width(width.dp)
}

private fun Modifier.rowChild(row: RowScope, model: MView): Modifier {
    if (model.isIconOnly()) return this
    val width = model.explicitWidth()
    return if (width == null) with(row) { weight(1f) } else width(width.dp)
}

private fun MView.explicitWidth(): Int? = when (this) {
    is TextualView -> size?.width

    is ImageView -> size?.width

    is Layout -> when (orientation) {
        LayoutOrientation.horizontal -> views.mapNotNull { it.explicitWidth() }.takeIf { it.size == views.size }?.sum()
        else -> views.mapNotNull { it.explicitWidth() }.maxOrNull()
    }

    else -> null
}

@Composable
private fun RenderText(
    view: TextualView,
    data: JsonObject?,
    modifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 2.dp)
) {
    val text = view.text(data)
    val label = view.label?.matchReplace(data).orEmpty()
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    // Under a label, the text is its supporting line: the label leads, the text is quieter.
    val style = when (view.textStyle) {
        TextStyle.bold -> typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        TextStyle.italic -> typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
        else -> typography.bodyLarge
    }.let { base -> view.textSize?.let { base.copy(fontSize = it.sp) } ?: base }
        .let { base ->
            if (label.isNotEmpty()) base.copy(color = colors.onSurfaceVariant) else base.copy(color = colors.onSurface)
        }
    val links = linkStyles()
    val body = bodyText(view, text, links, colors.surfaceContainerHighest)
    val display = buildAnnotatedString {
        if (label.isNotEmpty()) {
            // The label is a heading: a title's size and weight, unless labelStyle (or textSize) says otherwise.
            val labelSpan = SpanStyle(
                fontSize = view.textSize?.sp ?: typography.titleMedium.fontSize,
                fontWeight = when (view.labelStyle) {
                    null -> FontWeight.SemiBold
                    TextStyle.bold -> FontWeight.Bold
                    else -> FontWeight.Normal
                },
                fontStyle = if (view.labelStyle == TextStyle.italic) FontStyle.Italic else FontStyle.Normal,
                color = colors.onSurface
            )
            withStyle(labelSpan) { append(linkified(label, links)) }
            if (body.isNotEmpty()) append("\n")
        }
        append(body)
    }
    val textModifier = modifier
        .then(view.size?.height?.let { Modifier.height(it.dp) } ?: Modifier)
        .action(view.action, data)
        .padding(contentPadding)
    if (view.action == null) {
        Text(text = display, modifier = textModifier, style = style, overflow = TextOverflow.Ellipsis)
    } else {
        // Text that does something on a tap says so, with an arrow at its end, as a list item does.
        Row(textModifier, verticalAlignment = Alignment.CenterVertically) {
            Text(text = display, modifier = Modifier.weight(1f), style = style, overflow = TextOverflow.Ellipsis)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * A text view's text with its links: simple HTML or Markdown when its format says so, else plain text with web
 * addresses linked. [codeBackground] is behind Markdown's code.
 */
private fun bodyText(view: TextualView, text: String, links: TextLinkStyles, codeBackground: Color): AnnotatedString {
    val body = when (view.format) {
        TextFormat.html -> AnnotatedString.fromHtml(text, links)
        TextFormat.markdown -> markdownText(text, links, codeBackground)
        else -> linkified(text, links)
    }
    val end = body.text.trimEnd().length
    return if (end < body.length) body.subSequence(0, end) else body
}

@Composable
private fun RenderButton(view: ButtonView, data: JsonObject?, modifier: Modifier) {
    val screen = LocalScreen.current
    val action = screen.resolve(view.action)
    val onClick = { if (action?.type != ActionType.longclick) screen.onAction(view.action, data) }
    val longClick = if (action?.type == ActionType.longclick) {
        Modifier.combinedClickable(
            onClick = {},
            onLongClick = { screen.onAction(view.action, data) }
        )
    } else {
        Modifier
    }
    val icon = view.icon
    if (icon != null && view.isIconOnly()) {
        val description = view.label?.matchReplace(data)?.trim()?.takeIf { it.isNotEmpty() } ?: icon.spokenName()
        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = onClick,
            modifier = modifier.then(longClick)
        ) {
            Icon(icon.vector(), contentDescription = description)
        }
        return
    }
    // A button on its own is tonal: a form's submit button is the screen's one filled button.
    val padding = if (icon != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding
    FilledTonalButton(
        shapes = ButtonDefaults.shapes(),
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(longClick),
        contentPadding = compactSides(padding)
    ) {
        if (icon != null) {
            Icon(icon.vector(), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(view.text(data))
    }
}

/**
 * Buttons fill their width and centre their content, so the side padding only matters when space is tight: three
 * buttons sharing a row ("New idea", "PDF", "Export") kept "New idea" on one line on a 360 dp phone only with 8 dp sides.
 */
private fun compactSides(padding: PaddingValues) = PaddingValues(
    start = 8.dp,
    end = 8.dp,
    top = padding.calculateTopPadding(),
    bottom = padding.calculateBottomPadding()
)

/** A button that shows only its icon: it keeps its own size, and the row it is in is centred on it. */
private fun MView.isIconOnly(): Boolean = this is ButtonView && icon != null && title.isNullOrBlank() && mapping == null

private fun ButtonIcon.vector(): ImageVector = when (this) {
    ButtonIcon.add -> Icons.Filled.Add
    ButtonIcon.check -> Icons.Filled.Check
    ButtonIcon.close -> Icons.Filled.Close
    ButtonIcon.copy -> MaterialIcons.ContentCopy
    ButtonIcon.delete -> Icons.Filled.Delete
    ButtonIcon.edit -> Icons.Filled.Edit
    ButtonIcon.email -> Icons.Filled.Email
    ButtonIcon.favorite -> Icons.Filled.Favorite
    ButtonIcon.info -> Icons.Filled.Info
    ButtonIcon.open -> MaterialIcons.OpenInNew
    ButtonIcon.phone -> Icons.Filled.Phone
    ButtonIcon.place -> Icons.Filled.Place
    ButtonIcon.refresh -> Icons.Filled.Refresh
    ButtonIcon.search -> Icons.Filled.Search
    ButtonIcon.settings -> Icons.Filled.Settings
    ButtonIcon.share -> Icons.Filled.Share
    ButtonIcon.star -> Icons.Filled.Star
}

/** What a screen reader says for an icon without a label, such as "Delete". */
private fun ButtonIcon.spokenName(): String = name.replaceFirstChar { it.uppercase() }

/**
 * A tick box: ticked when the data's value is true; ticking it runs the action at once. A ticked title is struck
 * through. Only the box ticks, so a tap on the title reaches the list item (to open it, say).
 */
@Composable
private fun RenderCheck(view: CheckView, data: JsonObject?, modifier: Modifier) {
    val screen = LocalScreen.current
    val checked = data?.getValue(view.mapping) == "true"
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = { screen.onCheck(view, data, it) })
        Spacer(Modifier.width(12.dp))
        Text(
            view.title?.matchReplace(data).orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
        )
    }
}

@Composable
private fun RenderImage(
    view: ImageView,
    data: JsonObject?,
    modifier: Modifier,
    allowImagePreview: Boolean,
    edgeToEdge: Boolean = false,
    fillParentHeight: Boolean = false
) {
    val context = LocalContext.current
    // A mapping to a list (a photos field's) shows every image in it, in a row.
    (view.mapping?.let { data?.valueAt(it) } as? JsonArray)?.let { list ->
        val urls = list.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        return ImageStrip(urls, view.size?.height?.dp ?: PHOTO_THUMB_SIZE, modifier, allowImagePreview)
    }
    val mapped = view.mapping?.let { data?.getValue(it) }
    // An address with a placeholder that has no value would load something else: the placeholder shows instead.
    val url = (mapped ?: view.url)?.fillAddress(data)?.takeIf { it.isNotBlank() }
    val openPreview: (() -> Unit)? = if (allowImagePreview) {
        { url?.let { context.startActivity(createIntentPhotoActivity(context, it)) } }
    } else {
        null
    }
    val heightModifier = if (fillParentHeight) {
        Modifier.fillMaxHeight()
    } else {
        view.size?.height?.let { Modifier.height(it.dp) } ?: Modifier.height(220.dp)
    }
    val imageModifier = modifier
        .then(heightModifier)
        .clip(if (edgeToEdge) RectangleShape else RoundedCornerShape(16.dp))
        .action(view.action, data, openPreview)
    if (url == null) {
        ImagePlaceholder(imageModifier) { Text(stringResource(R.string.image_unavailable)) }
        return
    }
    // A plain AsyncImage, not SubcomposeAsyncImage: list cards size their rows to their content, which a
    // subcomposition can't report. Loading and failure are drawn over it.
    var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Box(imageModifier) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = if (view.scale == ImageScale.fit) ContentScale.Fit else ContentScale.Crop,
            onState = { state = it },
            // The tag marks a loaded image, which tests wait for.
            modifier = Modifier.matchParentSize().then(
                if (state is AsyncImagePainter.State.Success) {
                    Modifier.testTag("image-${data?.optString("id").orEmpty()}")
                } else {
                    Modifier
                }
            )
        )
        when (state) {
            is AsyncImagePainter.State.Success -> {}

            is AsyncImagePainter.State.Error -> ImagePlaceholder(Modifier.matchParentSize()) {
                Text(stringResource(R.string.image_unavailable))
            }

            else -> ImagePlaceholder(Modifier.matchParentSize()) { LoadingIndicator(Modifier.size(32.dp)) }
        }
    }
}

/** Several images side by side, each [side] square; tapping one shows it full screen when previews are allowed. */
@Composable
private fun ImageStrip(urls: List<String>, side: Dp, modifier: Modifier, allowImagePreview: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        urls.forEach { url ->
            var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
            Box(Modifier.size(side).clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { state = it },
                    // The tag marks a loaded image, which tests wait for.
                    modifier = Modifier.matchParentSize()
                        .then(
                            if (state is AsyncImagePainter.State.Success) Modifier.testTag("image-$url") else Modifier
                        )
                        .then(
                            if (allowImagePreview) {
                                Modifier.clickable { openFullSize(context, scope, url) }
                            } else {
                                Modifier
                            }
                        )
                )
                // A photo that can't be read (its file gone or damaged) shows as such, not as an empty gap.
                if (state is AsyncImagePainter.State.Error) BrokenImage(Modifier.matchParentSize())
            }
        }
    }
}

/** Shows [url] full screen: for a kept photo, its original (or copy) rather than the thumbnail lists show. */
private fun openFullSize(context: Context, scope: CoroutineScope, url: String) {
    scope.launch {
        val address = if (Photos.isPhoto(url)) PhotoStore(context).fullSize(url) else url
        context.startActivity(createIntentPhotoActivity(context, address))
    }
}

/** Where an image that couldn't be loaded would be: a tile with a broken-image icon. */
@Composable
private fun BrokenImage(modifier: Modifier) {
    val description = stringResource(R.string.image_unavailable)
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant).semantics {
            contentDescription = description
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(MaterialIcons.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImagePlaceholder(modifier: Modifier, content: @Composable () -> Unit) {
    Card(modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun RenderList(view: ListView, data: JsonObject?, modifier: Modifier) {
    val screen = LocalScreen.current
    val entry = screen.lists.get(view, data)
    val state by entry.content.collectAsState()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (view.hasControls() && !state.waiting && state.errorMessage == null) StoredListControls(view, entry, state)
        when {
            state.waiting -> LoadingIndicator(Modifier.align(Alignment.CenterHorizontally))

            state.errorMessage != null -> ListLoadError(state.errorMessage.orEmpty()) { screen.lists.retry(entry) }

            state.items.isEmpty() -> EmptyList(view, entry)

            view.orientation == LayoutOrientation.horizontal &&
                (state.items.size > HORIZONTAL_LAZY_THRESHOLD || state.canLoadMore) -> LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val cardWidth = (view.itemView.explicitWidth()?.plus(28) ?: 300).dp
                itemsIndexed(
                    items = state.items,
                    key = { index, item -> listItemKey(view, index, item) }
                ) { _, item ->
                    ListCard(view, item, Modifier.width(cardWidth))
                }
                if (state.canLoadMore || state.moreErrorMessage != null) {
                    item(key = "list-more") {
                        Box(Modifier.width(cardWidth)) { MoreItems(entry, state, automatic = true) }
                    }
                }
            }

            // Every card as tall as the tallest, so a title on two lines doesn't make one card stick out.
            view.orientation == LayoutOrientation.horizontal -> Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val cardWidth = (view.itemView.explicitWidth()?.plus(28) ?: 300).dp
                state.items.forEach { item ->
                    ListCard(view, item, Modifier.width(cardWidth).fillMaxHeight())
                }
            }

            view.columnCount > 1 -> state.items.chunked(view.columnCount).forEachIndexed { index, row ->
                GridRow(view, row, index * view.columnCount)
            }

            else -> state.items.forEachIndexed { index, item ->
                SwipeableListCard(
                    view = view,
                    item = item,
                    modifier = Modifier.fillMaxWidth().testTag("list-position-$index")
                )
            }
        }
        // Inside a screen that scrolls as a whole, the next page waits for a tap rather than loading all at once.
        if (view.orientation != LayoutOrientation.horizontal && state.items.isNotEmpty() &&
            (state.canLoadMore || state.moreErrorMessage != null)
        ) {
            MoreItems(entry, state, automatic = false)
        }
    }
}

private fun LazyListScope.verticalListItems(view: ListView, entry: ListsState.Entry, state: ListsState.Content) {
    if (view.hasControls() && !state.waiting && state.errorMessage == null) {
        item(key = "list-controls-${System.identityHashCode(view)}") { StoredListControls(view, entry, state) }
    }
    when {
        state.waiting -> item(key = "list-loading-${System.identityHashCode(view)}") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }

        state.errorMessage != null -> item(key = "list-error-${System.identityHashCode(view)}") {
            val screen = LocalScreen.current
            ListLoadError(state.errorMessage.orEmpty()) { screen.lists.retry(entry) }
        }

        state.items.isEmpty() -> item(key = "list-empty-${System.identityHashCode(view)}") {
            EmptyList(view, entry)
        }

        view.columnCount > 1 -> itemsIndexed(
            items = state.items.chunked(view.columnCount),
            key = { index, row -> listItemKey(view, index * view.columnCount, row.first()) }
        ) { index, row ->
            GridRow(view, row, index * view.columnCount)
        }

        else -> itemsIndexed(
            items = state.items,
            key = { index, item -> listItemKey(view, index, item) }
        ) { _, item ->
            SwipeableListCard(view, item, Modifier.fillMaxWidth())
        }
    }
    if (state.items.isNotEmpty() && (state.canLoadMore || state.moreErrorMessage != null)) {
        item(key = "list-more-${System.identityHashCode(view)}") { MoreItems(entry, state, automatic = true) }
    }
}

/** One row of a grid list: its items share the width, and a short last row keeps their size. */
@Composable
private fun GridRow(view: ListView, row: List<JsonObject>, firstIndex: Int) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEachIndexed { offset, item ->
            ListCard(view, item, Modifier.weight(1f).fillMaxHeight().testTag("list-position-${firstIndex + offset}"))
        }
        repeat(view.columnCount - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun EmptyList(view: ListView, entry: ListsState.Entry) {
    Text(
        view.emptyText?.matchReplace(entry.data)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.list_no_data),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp).testTag("list-empty")
    )
}

/**
 * The end of a list with more pages: [automatic], it fetches the next one as soon as it comes into view (a lazy list
 * composes it only near its end); otherwise a button does. Then a wait, or why the page couldn't load.
 */
@Composable
private fun MoreItems(entry: ListsState.Entry, state: ListsState.Content, automatic: Boolean) {
    val screen = LocalScreen.current
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("list-more"), contentAlignment = Alignment.Center) {
        when {
            state.moreErrorMessage != null -> ListLoadError(state.moreErrorMessage.orEmpty()) {
                screen.lists.loadMore(entry)
            }

            state.loadingMore || automatic -> {
                if (automatic) LaunchedEffect(entry, state.items.size) { screen.lists.loadMore(entry) }
                LoadingIndicator(Modifier.size(32.dp))
            }

            else -> OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = {
                screen.lists.loadMore(entry)
            }) { Text(stringResource(R.string.list_show_more)) }
        }
    }
}

@Composable
private fun ListLoadError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("list-load-error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onRetry) {
            Text(stringResource(R.string.list_retry))
        }
    }
}

private fun listItemKey(view: ListView, index: Int, item: JsonObject): String =
    "${System.identityHashCode(view)}-$index-${item.optString("id")}"

/**
 * A list card that can be swiped when the list has swipe actions: towards the start (a delete, say) or the end (done,
 * say). The swipe runs the action with the item and the card slides back, so it only goes when the action removes it.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SwipeableListCard(view: ListView, item: JsonObject, modifier: Modifier) {
    if (view.swipeToStart == null && view.swipeToEnd == null) return ListCard(view, item, modifier)
    val screen = LocalScreen.current
    val state = rememberSwipeToDismissBoxState()
    // Once the card has come to rest swiped (settledValue: its animation done), the action runs once and the card
    // slides back. Acting any earlier (onDismiss, or currentValue) races the end of the swipe animation, which can undo
    // the slide back and, with onDismiss, run the action again and again.
    val currentItem by rememberUpdatedState(item)
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { value ->
            if (value == SwipeToDismissBoxValue.Settled) return@collect
            screen.onAction(
                if (value ==
                    SwipeToDismissBoxValue.StartToEnd
                ) {
                    view.swipeToEnd
                } else {
                    view.swipeToStart
                },
                currentItem
            )
            state.reset()
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = view.swipeToEnd != null,
        enableDismissFromEndToStart = view.swipeToStart != null,
        backgroundContent = {
            val towardsEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val colors = MaterialTheme.colorScheme
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .background(if (towardsEnd) colors.primaryContainer else colors.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (towardsEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    (
                        if (towardsEnd) {
                            view.swipeToEndIcon ?: ButtonIcon.check
                        } else {
                            view.swipeToStartIcon
                                ?: ButtonIcon.delete
                        }
                        ).vector(),
                    contentDescription = null,
                    tint = if (towardsEnd) colors.onPrimaryContainer else colors.onErrorContainer
                )
            }
        }
    ) { ListCard(view, item, Modifier.fillMaxWidth()) }
}

@Composable
private fun ListCard(view: ListView, item: JsonObject, modifier: Modifier) {
    Card(
        modifier.action(view.action, item)
            .testTag("list-item-${item.optString("id")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        ListCardContent(
            itemView = view.itemView,
            item = item,
            allowImagePreview = view.action == null
        )
    }
}

@Composable
private fun ListCardContent(itemView: MView, item: JsonObject, allowImagePreview: Boolean) {
    val screen = LocalScreen.current
    val resolvedItem = screen.resolve(itemView)
    val resolvedLayout = resolvedItem as? Layout
    val resolvedChildren = resolvedLayout?.views?.let { screen.shownViews(it, item) }

    if (resolvedItem is ImageView) {
        RenderImage(
            view = resolvedItem,
            data = item,
            modifier = Modifier.fillMaxWidth(),
            allowImagePreview = allowImagePreview,
            edgeToEdge = true
        )
    } else if (
        resolvedLayout?.orientation == LayoutOrientation.vertical &&
        resolvedChildren?.firstOrNull() is ImageView
    ) {
        Column(Modifier.fillMaxWidth()) {
            resolvedChildren.forEachIndexed { index, child ->
                when {
                    index == 0 && child is ImageView -> RenderImage(
                        view = child,
                        data = item,
                        modifier = Modifier.fillMaxWidth(),
                        allowImagePreview = false,
                        edgeToEdge = true
                    )

                    // A button among a card's texts is drawn as text, as it always was (ButtonView was a TextView),
                    // unless it is only an icon.
                    child is TextualView && !child.isIconOnly() -> RenderText(
                        view = child,
                        data = item,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    )

                    else -> Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        RenderNode(child, item, Modifier.fillMaxWidth(), allowImagePreview = false)
                    }
                }
            }
        }
    } else if (
        resolvedLayout?.orientation == LayoutOrientation.horizontal &&
        resolvedChildren?.firstOrNull() is ImageView
    ) {
        val image = resolvedChildren.first() as ImageView
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = (image.size?.width?.let { Modifier.width(it.dp) } ?: Modifier.width(112.dp))
                    .fillMaxHeight()
            ) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(image.size?.height?.dp ?: 220.dp)
                )
                RenderImage(
                    view = image,
                    data = item,
                    modifier = Modifier.matchParentSize(),
                    allowImagePreview = false,
                    edgeToEdge = true,
                    fillParentHeight = true
                )
            }
            // Icon buttons (such as Remove) sit at the row's end; the rest is a column beside the image.
            val (icons, others) = resolvedChildren.drop(1).partition { it.isIconOnly() }
            Column(Modifier.weight(1f)) {
                others.forEach { child ->
                    when (child) {
                        is TextualView -> RenderHorizontalCardText(
                            view = child,
                            data = item,
                            modifier = Modifier.fillMaxWidth()
                        )

                        else -> Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            RenderNode(child, item, Modifier.fillMaxWidth(), allowImagePreview = false)
                        }
                    }
                }
            }
            icons.forEach { RenderNode(it, item, Modifier.padding(end = 8.dp), allowImagePreview = false) }
        }
    } else {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            CompositionLocalProvider(LocalViewSpacing provides CARD_SPACING) {
                RenderNode(resolvedItem, item, Modifier.fillMaxWidth(), allowImagePreview = false)
            }
        }
    }
}

/** Links in a text view: the theme's primary color, underlined. */
@Composable
private fun linkStyles() = TextLinkStyles(
    SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
)

@Composable
private fun RenderHorizontalCardText(view: TextualView, data: JsonObject?, modifier: Modifier) {
    val title = view.label?.matchReplace(data).orEmpty()
    val supportingText = view.text(data)
    Column(
        modifier = modifier
            .then(view.size?.height?.let { Modifier.height(it.dp) } ?: Modifier)
            .action(view.action, data)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (supportingText.isNotEmpty()) {
            Text(
                text = bodyText(view, supportingText, linkStyles(), MaterialTheme.colorScheme.surfaceContainerHighest),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RenderForm(view: FormView, data: JsonObject?, modifier: Modifier) {
    val screen = LocalScreen.current
    val form = screen.forms.state(view, data)
    val values by form.values.collectAsState()
    val error by form.error.collectAsState()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A form's title is a heading like a text view's label.
        view.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        view.formElements.forEach { item ->
            val onChange: (Any?) -> Unit = { screen.forms.set(view, data, item.mapping, it) }
            when (item) {
                is CheckBox -> BooleanField(item, values[item.mapping] == true, onChange)

                is TextInput -> TextField(item, values[item.mapping] as? String, onChange)

                is DateInput -> DateField(item, values[item.mapping] as? String, onChange)

                is Selector -> SelectorField(item, values[item.mapping] as? String, onChange)

                is PhotosInput -> PhotosField(item, values[item.mapping] as? JsonArray, onChange)

                is FilesInput -> FilesField(item, values[item.mapping] as? JsonArray, onChange)

                is TagsInput -> TagsField(
                    item,
                    values[item.mapping] as? JsonArray,
                    remember(view, item) {
                        screen.knownTags(view, item)
                    },
                    onChange
                )

                is TimeInput -> TimeField(item, values[item.mapping] as? String, onChange)

                // A hidden field only sends its value.
                is HiddenInput -> Unit
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // Sending is the form's main action: a medium button across the form, easy to reach and to hit.
        val height = ButtonDefaults.MediumContainerHeight
        Button(
            shapes = ButtonDefaults.shapesFor(height),
            onClick = { screen.onSubmit(view, data) },
            modifier = Modifier.fillMaxWidth().heightIn(min = height),
            contentPadding = ButtonDefaults.contentPaddingFor(height)
        ) {
            Text(view.buttonTitle ?: stringResource(R.string.field_submit), style = ButtonDefaults.textStyleFor(height))
        }
    }
}

@Composable
private fun BooleanField(item: CheckBox, checked: Boolean, onChange: (Any?) -> Unit) {
    Column {
        // The whole row toggles, label included.
        Row(
            Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = { onChange(it) }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium)
        }
        Description(item.description)
    }
}

/**
 * The keyboard for a text field of [textType]. Plain text starts its sentences with a capital, as people write;
 * addresses, numbers and passwords don't.
 */
internal fun keyboardOptionsFor(textType: TextType?): KeyboardOptions {
    val keyboardType = when (textType) {
        TextType.numeric -> KeyboardType.Number
        TextType.email -> KeyboardType.Email
        TextType.password -> KeyboardType.Password
        TextType.url -> KeyboardType.Uri
        else -> KeyboardType.Text
    }
    val capitalization = if (keyboardType ==
        KeyboardType.Text
    ) {
        KeyboardCapitalization.Sentences
    } else {
        KeyboardCapitalization.None
    }
    return KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType)
}

@Composable
private fun TextField(item: TextInput, value: String?, onChange: (Any?) -> Unit) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { onChange(it.ifEmpty { null }) },
        label = { Text(item.title) },
        placeholder = { item.placeholder?.let { Text(it) } },
        supportingText = if (item.description == null) {
            null
        } else {
            { Text(item.description!!) }
        },
        keyboardOptions = keyboardOptionsFor(item.textType),
        // A multiline field grows with its text up to a few lines, then scrolls.
        visualTransformation = if (item.textType ==
            TextType.password
        ) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        singleLine = item.multiline != true,
        maxLines = if (item.multiline == true) MULTILINE_MAX_LINES else 1,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DateField(item: DateInput, value: String?, onChange: (Any?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column {
        FieldTitle(item.title)
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = {
            picking = true
        }, modifier = Modifier.fillMaxWidth()) { Text(value ?: stringResource(R.string.field_choose_date)) }
        Description(item.description)
    }
    if (!picking) return
    val min = item.min?.let { utcDay(Date(it)) }
    val max = item.max?.let { utcDay(Date(it)) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = value?.toDate()?.let(::utcDay),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                (min == null || utcTimeMillis >= min) && (max == null || utcTimeMillis <= max)
        }
    )
    DatePickerDialog(
        onDismissRequest = { picking = false },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                state.selectedDateMillis?.let { onChange(localDay(it).stringValue()) }
                picking = false
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                picking = false
            }) { Text(stringResource(android.R.string.cancel)) }
        }
    ) { DatePicker(state) }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TimeField(item: TimeInput, value: String?, onChange: (Any?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column {
        FieldTitle(item.title)
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = {
            picking = true
        }, modifier = Modifier.fillMaxWidth()) { Text(value ?: stringResource(R.string.field_choose_time)) }
        Description(item.description)
    }
    if (!picking) return
    val (hour, minute) = value?.let(FormDates::parseTime) ?: (12 to 0)
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )
    AlertDialog(
        onDismissRequest = { picking = false },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                onChange(FormDates.formatTime(state.hour, state.minute))
                picking = false
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                picking = false
            }) { Text(stringResource(android.R.string.cancel)) }
        },
        text = { TimePicker(state) }
    )
}

/** The picker counts days in UTC; the form's dates are local midnights. */
private fun utcDay(date: Date): Long = date.toInstant().atZone(
    ZoneId.systemDefault()
).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun localDay(utcMillis: Long): Date = Date.from(
    Instant.ofEpochMilli(
        utcMillis
    ).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
)

@Composable
private fun SelectorField(item: Selector, selected: String?, onChange: (Any?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldTitle(item.title)
        // Material 3 Expressive: a few short options are one connected button group across the width; more, or long
        // ones, are separate toggle buttons that wrap onto further lines.
        val options = item.options
        val connected =
            options.size in 2..CONNECTED_OPTIONS && options.all { it.label.length <= CONNECTED_LABEL_LENGTH }
        val choice = @Composable { index: Int, modifier: Modifier ->
            val option = options[index]
            val checked = selected == option.value
            ToggleButton(
                checked = checked,
                onCheckedChange = { onChange(option.value) },
                shapes = when {
                    !connected -> ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = modifier.semantics {
                    role = Role.RadioButton
                    this.selected = checked
                }
            ) { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (connected) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                options.indices.forEach { choice(it, Modifier.weight(1f)) }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.indices.forEach { choice(it, Modifier) }
            }
        }
        Description(item.description)
    }
}

/**
 * Photos picked from the gallery (Android's photo picker: no permission), copied into the app and shown as thumbnails
 * that can be removed; the field's value is the list of their references.
 */
@Composable
private fun PhotosField(item: PhotosInput, photos: JsonArray?, onChange: (Any?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { PhotoStore(context) }
    val current = photos?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    var failed by remember { mutableStateOf(false) }
    val keep: (List<Uri>) -> Unit = { uris ->
        scope.launch {
            val added = uris.take(item.limit - current.size).mapNotNull { uri ->
                runCatching { store.add(uri) }.getOrNull()
            }
            failed = added.size < uris.size.coerceAtMost(item.limit - current.size)
            if (added.isNotEmpty()) onChange(JsonArray((current + added).map(::JsonPrimitive)))
        }
    }
    val pickMany = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = item.limit.coerceAtLeast(2))
    ) { keep(it) }
    val pickOne =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { keep(listOf(it)) }
        }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldTitle(item.title)
        if (current.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                current.forEach { reference ->
                    var loaded by remember(reference) { mutableStateOf(false) }
                    Box(Modifier.size(PHOTO_THUMB_SIZE)) {
                        var broken by remember(reference) { mutableStateOf(false) }
                        AsyncImage(
                            model = reference,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onState = {
                                loaded = it is AsyncImagePainter.State.Success
                                broken = it is AsyncImagePainter.State.Error
                            },
                            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp))
                                .then(if (loaded) Modifier.testTag("image-$reference") else Modifier)
                                .clickable { openFullSize(context, scope, reference) }
                        )
                        if (broken) BrokenImage(Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)))
                        FilledTonalIconButton(
                            onClick = {
                                onChange(
                                    JsonArray((current - reference).map(::JsonPrimitive)).takeIf {
                                        it.isNotEmpty()
                                    }
                                )
                            },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.field_remove_photo),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        val room = item.limit - current.size
        FilledTonalButton(
            onClick = { if (room > 1) pickMany.launch(imagesOnly) else pickOne.launch(imagesOnly) },
            shapes = ButtonDefaults.shapes(),
            enabled = room > 0,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(
                MaterialIcons.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(if (current.isEmpty()) R.string.field_add_photos else R.string.field_add_more))
        }
        if (failed) {
            Text(
                stringResource(R.string.field_photo_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Description(item.description)
    }
}

private val PHOTO_THUMB_SIZE = 88.dp

/** Tags typed one at a time (Enter or + adds one, ignoring a repeat), shown as chips that can be removed. */
@Composable
private fun TagsField(item: TagsInput, tags: JsonArray?, known: List<String>, onChange: (Any?) -> Unit) {
    val current = tags?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    var typed by rememberSaveable(item.mapping) { mutableStateOf("") }
    val addTag = { text: String ->
        val tag = text.trim().trimStart('#')
        if (tag.isNotEmpty() && current.none { it.equals(tag, ignoreCase = true) }) {
            onChange(JsonArray((current + tag).map(::JsonPrimitive)))
        }
        typed = ""
    }
    val add = { addTag(typed) }
    val suggestions = TagSuggestions.matching(known, current, typed)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it.replace(",", "").replace("\n", "") },
            label = { Text(item.title) },
            placeholder = { item.placeholder?.let { Text(it) } },
            supportingText = item.description?.let { description -> { Text(description) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            trailingIcon = {
                IconButton(onClick = add, enabled = typed.isNotBlank(), shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.field_add_tag))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        // Tags used elsewhere in the same collection: one tap adds one, spelt as before.
        if (suggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("tag-suggestions")
            ) {
                suggestions.forEach { tag ->
                    SuggestionChip(onClick = { addTag(tag) }, label = { Text("+ #$tag") })
                }
            }
        }
        if (current.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.field_remove_named, tag),
                                modifier = Modifier.size(InputChipDefaults.IconSize).clickable {
                                    onChange(JsonArray((current - tag).map(::JsonPrimitive)).takeIf { it.isNotEmpty() })
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Files of any kind from Android's file picker (no permission needed): linked, not copied, each a chip that opens it
 * and can be removed; the field's value is the list of the files (see Files).
 */
@Composable
private fun FilesField(item: FilesInput, value: JsonArray?, onChange: (Any?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachments = remember(context) { FileAttachments(context) }
    val current = Files.of(value)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val known = current.map { it.uri }.toSet()
            val added = uris.filter { it.toString() !in known }.take(item.limit - current.size)
                .mapNotNull { uri -> runCatching { attachments.add(uri) }.getOrNull() }
            if (added.isNotEmpty()) onChange(JsonArray((current + added).map { it.toJson() }))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldTitle(item.title)
        if (current.isNotEmpty()) {
            FileChips(current) { removed ->
                onChange(JsonArray((current - removed).map { it.toJson() }).takeIf { it.isNotEmpty() })
            }
        }
        FilledTonalButton(
            onClick = { pick.launch(arrayOf("*/*")) },
            shapes = ButtonDefaults.shapes(),
            enabled = current.size < item.limit,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(if (current.isEmpty()) R.string.field_attach_files else R.string.field_attach_more))
        }
        Description(item.description)
    }
}

/** The lines a multiline text field grows to before it scrolls. */
private const val MULTILINE_MAX_LINES = 8

/** Whether a stored list offers orders or a tag filter above its items. */
private fun ListView.hasControls(): Boolean =
    stored?.let { !it.sortOptions.isNullOrEmpty() || it.filterBy != null } == true

/**
 * Above a stored list: its orders as one connected button group (Material 3 Expressive), and its tags as filter chips
 * after "All", to show only the items with that tag.
 */
@Composable
private fun StoredListControls(view: ListView, entry: ListsState.Entry, state: ListsState.Content) {
    val screen = LocalScreen.current
    val choice by entry.choice.collectAsState()
    val options = view.stored?.sortOptions.orEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (options.size > 1) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                options.forEachIndexed { index, option ->
                    val checked = choice.sortOption == index
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { screen.lists.choose(entry, choice.copy(sortOption = index)) },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f).semantics {
                            role = Role.RadioButton
                            selected = checked
                        }
                    ) { Text(option.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        val tags = (state.tags + listOfNotNull(choice.tag)).distinctBy(String::lowercase)
        if (view.stored?.filterBy != null && tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = choice.tag == null,
                    onClick = { screen.lists.choose(entry, choice.copy(tag = null)) },
                    label = { Text(stringResource(R.string.list_filter_all)) }
                )
                tags.forEach { tag ->
                    FilterChip(
                        selected = choice.tag.equals(tag, ignoreCase = true),
                        onClick = { screen.lists.choose(entry, choice.copy(tag = tag)) },
                        label = { Text("#$tag") }
                    )
                }
            }
        }
    }
}

/** A selector with this many options or fewer, all short, is drawn as one connected group. */
private const val CONNECTED_OPTIONS = 4
private const val CONNECTED_LABEL_LENGTH = 12

/** The title above a date, time or choice field, in the field labels' style. */
@Composable
private fun FieldTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Description(description: String?) {
    description?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Modifier.action(actionRef: Action?, data: JsonObject?, fallback: (() -> Unit)? = null): Modifier {
    val screen = LocalScreen.current
    val action = screen.resolve(actionRef)
    return if (action != null || fallback != null) {
        combinedClickable(
            onClick = {
                when {
                    action != null && action.type != ActionType.longclick -> screen.onAction(actionRef, data)
                    action == null -> fallback?.invoke()
                }
            },
            onLongClick = if (action?.type == ActionType.longclick) {
                { screen.onAction(actionRef, data) }
            } else {
                null
            }
        )
    } else {
        this
    }
}
