package it.sapienza.appinterpreter.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.ai.AskAi
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.runtime.AndroidRuntimeStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The configuration editor: JSON with syntax colours, suggestions from the schema for the cursor, a live check that
 * places every problem in the text, ready-made views to insert, and Run to open the result as the app.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditorScreen(
    title: String,
    initialText: String,
    /** Where the text came from when it isn't saved anywhere yet ("Shared from your AI app"), or null. */
    unsavedSource: String?,
    /** The text is an AI app's answer (shared, pasted or a chat link): problems are offered back to it first. */
    fromAiApp: Boolean,
    check: suspend (String) -> List<EditorIssue>,
    /** The HTTP status of a web image the text shows (null: no answer), to say which don't load. */
    imageStatus: suspend (String) -> Int?,
    suggestions: SchemaSuggestions,
    onRun: (String) -> Unit,
    onSaveAs: (String) -> Unit,
    /** Opens "Ask your AI app" with the text as it is now, saved or not. */
    onAskAi: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialText)) }
    var issues by remember { mutableStateOf<List<EditorIssue>?>(null) }
    var issuesOpen by remember { mutableStateOf(false) }
    var insertOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf<String?>(null) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }

    // Each image address is asked once while the screen is open, however often the text changes.
    val imageStatuses = remember { mutableMapOf<String, Int?>() }
    // The live check, once typing pauses; when the configuration is right, whether its web images load.
    LaunchedEffect(value.text) {
        issues = null
        delay(CHECK_DELAY_MS)
        val found = check(value.text)
        issues = found.ifEmpty {
            ImageLinks.problems(value.text, AndroidRuntimeStrings(context)) { url ->
                if (url in
                    imageStatuses
                ) {
                    imageStatuses[url]
                } else {
                    imageStatus(url).also { imageStatuses[url] = it }
                }
            }
        }
    }
    // Suggestions and the keyboard type, from one reading of the text at the cursor.
    val assist = remember(value.text, value.selection) {
        if (value.selection.collapsed) {
            runCatching { suggestions.assist(value.text, value.selection.start) }.getOrNull()
        } else {
            null
        }
    }
    val numberKeyboard = assist?.numberKeyboard ?: false
    val chips = assist?.suggestions ?: Suggestions.NONE

    fun replace(start: Int, end: Int, insert: String, cursor: Int) {
        val text = value.text.replaceRange(start, end, insert)
        value = TextFieldValue(text, TextRange(start + cursor))
    }

    fun jumpTo(offset: Int) {
        val target = offset.coerceIn(0, value.text.length)
        value = value.copy(selection = TextRange(target))
        focus.requestFocus()
        layout?.let {
            scope.launch {
                scroll.animateScrollTo((it.getLineTop(it.getLineForOffset(target)) - 120f).toInt().coerceAtLeast(0))
            }
        }
    }

    // An answer from an AI app isn't saved anywhere yet, so leaving always asks.
    fun leave() = if (unsavedSource != null || value.text != initialText) confirmDiscard = true else onBack()
    BackHandler(onBack = ::leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = ::leave) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                        insertOpen = true
                    }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.editor_insert)) }
                    DropdownMenu(expanded = insertOpen, onDismissRequest = { insertOpen = false }) {
                        Snippets.all.groupBy { it.formField }.toSortedMap().forEach { (formFields, group) ->
                            if (formFields) HorizontalDivider()
                            Text(
                                stringResource(
                                    if (formFields) R.string.editor_insert_form_fields else R.string.editor_insert_views
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            group.forEach { snippet ->
                                DropdownMenuItem(
                                    text = { Text(snippet.label) },
                                    onClick = {
                                        insertOpen = false
                                        val at = value.selection.start
                                        val indent = Snippets.indentAt(value.text, at)
                                        val insert = Snippets.indented(snippet.json, indent)
                                        replace(at, value.selection.end, insert, insert.length)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                        // The text that is checked is the text that runs, even if typing goes on meanwhile.
                        val text = value.text
                        scope.launch {
                            val found = check(text)
                            issues = found
                            if (found.isEmpty()) {
                                onRun(text)
                            } else {
                                issuesOpen = true
                                found.firstNotNullOfOrNull { it.offset }?.let(::jumpTo)
                                Toast.makeText(
                                    context,
                                    resources.getQuantityString(
                                        R.plurals.editor_problems_to_fix,
                                        found.size,
                                        found.size
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.editor_run)) }
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                        moreOpen = true
                    }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.editor_more)) }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_format)) }, onClick = {
                            moreOpen = false
                            if (runCatching { JsonText.parse(value.text) }.isFailure) {
                                Toast.makeText(context, R.string.editor_fix_syntax, Toast.LENGTH_SHORT).show()
                            } else {
                                value = TextFieldValue(prettyJson(value.text), TextRange(0))
                            }
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_save_as)) }, onClick = {
                            moreOpen = false
                            onSaveAs(value.text)
                        })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_send_to_ai)) }, onClick = {
                            moreOpen = false
                            onAskAi(value.text)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_paste_ai)) }, onClick = {
                            moreOpen = false
                            val json = AskAi.jsonFromClipboard(context)
                            if (json == null) {
                                Toast.makeText(context, R.string.editor_no_json_clipboard, Toast.LENGTH_LONG).show()
                            } else {
                                pasted = json
                            }
                        })
                    }
                }
            )
        }
    ) { padding ->
        // Edge to edge, the window no longer shrinks for the keyboard: the content makes room for it instead.
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (unsavedSource != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        stringResource(
                            if (fromAiApp) R.string.editor_unsaved_ai else R.string.editor_unsaved,
                            unsavedSource
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            val colors = MaterialTheme.colorScheme
            val jsonColors = if (colors.surface.luminance() < 0.5f) JsonColors.Dark else JsonColors.Light
            val problems = issues.orEmpty().mapNotNull { it.offset }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = Snippets.withAutoIndent(value, it) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colors.onSurface
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    // Code, not prose: no autocorrect or capitals, and digits where a number goes.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = if (numberKeyboard) KeyboardType.Number else KeyboardType.Ascii
                    ),
                    visualTransformation = remember(jsonColors, problems) { JsonHighlighting(jsonColors, problems) },
                    onTextLayout = { layout = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus)
                )
            }

            if (chips.items.isNotEmpty()) {
                HorizontalDivider()
                SuggestionRow(chips) { suggestion ->
                    replace(suggestion.start, suggestion.end, suggestion.insert, suggestion.cursor)
                }
            }

            IssueBar(
                issues = issues,
                open = issuesOpen,
                text = value.text,
                onToggle = { issuesOpen = !issuesOpen },
                onIssue = { issue -> issue.offset?.let(::jumpTo) },
                fromAiApp = fromAiApp,
                onSendToAi = { found -> AskAi.shareToAiApp(context, AskAi.fixRequest(context, value.text, found)) }
            )
        }
    }

    pasted?.let { json ->
        AlertDialog(
            onDismissRequest = { pasted = null },
            title = { Text(stringResource(R.string.editor_use_answer_title)) },
            text = { Text(stringResource(R.string.editor_use_answer_text)) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    value = TextFieldValue(prettyJson(json), TextRange(0))
                    pasted = null
                }) { Text(stringResource(R.string.received_replace)) }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    pasted = null
                }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = {
                Text(
                    stringResource(
                        if (unsavedSource != null) R.string.editor_discard_app else R.string.editor_discard_edits
                    )
                )
            },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    confirmDiscard = false
                }) { Text(stringResource(R.string.editor_keep_editing)) }
            }
        )
    }
}

/**
 * The suggestions above the keyboard. With a heading they are the attributes the object can still take, required
 * ones first and highlighted; tapping one adds it.
 */
@Composable
private fun SuggestionRow(suggestions: Suggestions, onPick: (Suggestion) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.heading?.let {
            Text(
                "$it:",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        suggestions.items.forEach { suggestion ->
            SuggestionChip(
                onClick = { onPick(suggestion) },
                label = {
                    Text(
                        if (suggestion.required) "${suggestion.label} *" else suggestion.label,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (suggestion.required) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = if (suggestion.required) {
                    SuggestionChipDefaults.suggestionChipColors(
                        containerColor = colors.errorContainer,
                        labelColor = colors.onErrorContainer
                    )
                } else {
                    SuggestionChipDefaults.suggestionChipColors()
                },
                border = if (suggestion.required) null else SuggestionChipDefaults.suggestionChipBorder(enabled = true)
            )
        }
    }
}

/** The live check's result: a summary line that opens the list of problems; tapping one moves the cursor to it. */
@Composable
private fun IssueBar(
    issues: List<EditorIssue>?,
    open: Boolean,
    text: String,
    onToggle: () -> Unit,
    onIssue: (EditorIssue) -> Unit,
    /** An AI app's answer gets a red button to send the problems back; one's own JSON, a quieter link. */
    fromAiApp: Boolean,
    onSendToAi: (List<EditorIssue>) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // Problems turn the bar red (the theme's error colours, light or dark), so they can't be missed.
    val problems = !issues.isNullOrEmpty()
    Surface(
        color = if (problems) colors.errorContainer else colors.surfaceContainer,
        contentColor = if (problems) colors.onErrorContainer else colors.onSurface
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !issues.isNullOrEmpty(), onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    issues == null -> Text(
                        stringResource(R.string.editor_checking),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )

                    issues.isEmpty() -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.editor_no_problems), style = MaterialTheme.typography.labelLarge)
                    }

                    else -> {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        val count = pluralStringResource(R.plurals.editor_problems, issues.size, issues.size)
                        Text(
                            if (open) {
                                count
                            } else {
                                stringResource(
                                    R.string.editor_problem_first,
                                    count,
                                    issues.first().message
                                )
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (open && !issues.isNullOrEmpty()) {
                if (fromAiApp) {
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { onSendToAi(issues) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.error,
                            contentColor = colors.onError
                        ),
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    ) {
                        Text(stringResource(R.string.editor_send_problems))
                    }
                } else {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        onSendToAi(issues)
                    }, modifier = Modifier.padding(start = 4.dp)) {
                        Text(stringResource(R.string.editor_ask_fix))
                    }
                }
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(issues) { issue ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                onIssue(issue)
                            }.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            val line = issue.offset?.let { ConfigurationChecker.lineOf(text, it) }
                            Text(
                                listOfNotNull(
                                    line?.let {
                                        stringResource(R.string.editor_issue_line, it)
                                    },
                                    issue.path.takeIf { it != "$" }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = LocalContentColor.current.copy(alpha = 0.75f)
                            )
                            Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private const val CHECK_DELAY_MS = 450L
