package it.sapienza.appinterpreter.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.markdown.Markdown
import it.sapienza.appinterpreter.ui.markdownText
import java.text.NumberFormat

/** Ideas to start from: tapping one writes its request in the field. */
private val IDEAS = listOf(
    R.string.ask_ai_idea_books to R.string.ask_ai_idea_books_request,
    R.string.ask_ai_idea_recipes to R.string.ask_ai_idea_recipes_request,
    R.string.ask_ai_idea_race to R.string.ask_ai_idea_race_request,
    R.string.ask_ai_idea_gallery to R.string.ask_ai_idea_gallery_request
)

/**
 * Starts working with an AI app: what to ask for (with ideas to start from), whether to change the current app, all
 * the instructions or the short ones with links, how the round trip goes, and how much of the AI chat they take. Appkin has no AI inside, which the screen
 * says once, plainly. Share and Copy stay above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(
    request: String,
    onRequestChange: (String) -> Unit,
    canChangeCurrent: Boolean,
    changeCurrent: Boolean,
    onChangeCurrentChange: (Boolean) -> Unit,
    withLinks: Boolean,
    onWithLinksChange: (Boolean) -> Unit,
    tokens: Int,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onBack: () -> Unit,
    preview: String? = null,
    onPreview: () -> Unit = {},
    onClosePreview: () -> Unit = {}
) {
    if (preview != null) {
        PromptPreview(preview, tokens, onShare, onCopy, onClosePreview)
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_ask_ai)) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                }
            )
        },
        bottomBar = { SendBar(onShare, onCopy) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(changeCurrent)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = request,
                    onValueChange = onRequestChange,
                    label = {
                        Text(
                            stringResource(
                                if (changeCurrent) R.string.ask_ai_label_change else R.string.ask_ai_label_new
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                if (changeCurrent) {
                                    R.string.ask_ai_placeholder_change
                                } else {
                                    R.string.ask_ai_placeholder_new
                                }
                            )
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    // A height rather than minLines, which Robolectric can't lay out.
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("ai-request")
                )
                if (!changeCurrent) Ideas(onRequestChange)
            }
            if (canChangeCurrent) ChangeCurrent(changeCurrent, onChangeCurrentChange)
            Instructions(withLinks, onWithLinksChange)
            HowItWorks()
            Note(
                Icons.Filled.Info,
                stringResource(R.string.ask_ai_tokens, NumberFormat.getIntegerInstance().format(tokens)),
                textTag = "ai-token-estimate"
            )
            OutlinedButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onPreview,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.testTag("ai-preview")
            ) {
                Icon(
                    painterResource(R.drawable.baseline_visibility_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ask_ai_see_sent))
            }
        }
    }
}

/** Copy and Share, above the keyboard and the navigation bar. */
@Composable
private fun SendBar(onShare: () -> Unit, onCopy: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onCopy) {
                Text(stringResource(R.string.ask_ai_copy))
            }
            Button(
                shapes = ButtonDefaults.shapes(),
                onClick = onShare,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ask_ai_share))
            }
        }
    }
}

/**
 * Exactly what Share and Copy send, to read before sending: formatted (its Markdown styled) or as sent (the raw text),
 * in parts (a lazy list, as it can be long), selectable, with the same Copy and Share. Back returns to the request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptPreview(prompt: String, tokens: Int, onShare: () -> Unit, onCopy: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var formatted by rememberSaveable { mutableStateOf(true) }
    val paragraphs = remember(prompt) { prompt.trimEnd().split(Regex("\n{2,}")) }
    // Formatted, consecutive list items stay together, one line apart.
    val blocks = remember(prompt) {
        Markdown.parse(prompt).fold(mutableListOf<List<Markdown.Block>>()) { groups, block ->
            val last = groups.lastOrNull()
            if (block is Markdown.Block.ListItem &&
                last?.last() is Markdown.Block.ListItem
            ) {
                groups[groups.lastIndex] = last + block
            } else {
                groups += listOf(block)
            }
            groups
        }
    }
    val links =
        TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ask_ai_sent_title)) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                }
            )
        },
        bottomBar = { SendBar(onShare, onCopy) }
    ) { padding ->
        SelectionContainer {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize().testTag("ai-preview-text"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Note(
                            Icons.Filled.Info,
                            stringResource(
                                R.string.ask_ai_sent_size,
                                NumberFormat.getIntegerInstance().format(prompt.length),
                                NumberFormat.getIntegerInstance().format(tokens)
                            )
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                        ) {
                            listOf(true to R.string.ask_ai_formatted, false to R.string.ask_ai_as_sent).forEachIndexed {
                                    index,
                                    (styled, label)
                                ->
                                ToggleButton(
                                    checked = formatted == styled,
                                    onCheckedChange = { formatted = styled },
                                    shapes = if (index ==
                                        0
                                    ) {
                                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    } else {
                                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    },
                                    colors = ToggleButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag(if (styled) "ai-preview-formatted" else "ai-preview-raw")
                                        .semantics {
                                            role = Role.RadioButton
                                            selected = formatted == styled
                                        }
                                ) { Text(stringResource(label), maxLines = 1) }
                            }
                        }
                    }
                }
                if (formatted) {
                    items(blocks) { group ->
                        val code = group.singleOrNull() as? Markdown.Block.Code
                        if (code != null) {
                            Surface(
                                color = codeBackground,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    code.text,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Text(
                                markdownText(group, links, codeBackground),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(paragraphs) { paragraph ->
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(changeCurrent: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text("✨", fontSize = 26.sp) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (changeCurrent) R.string.ask_ai_header_change else R.string.ask_ai_header_new),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.ask_ai_header_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Ideas(onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IDEAS.forEach { (name, request) ->
            val text = stringResource(request)
            SuggestionChip(onClick = { onPick(text) }, label = { Text(stringResource(name)) })
        }
    }
}

/** The switch for sending the current app too; the whole row toggles it. */
@Composable
private fun ChangeCurrent(checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .testTag("ai-change-current")
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.ask_ai_change_current), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.ask_ai_change_current_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/**
 * What the AI app gets: all the instructions, or the short ones that link to the rest; a connected button group
 * (Material 3 Expressive), with a line saying what the choice means.
 */
@Composable
private fun Instructions(withLinks: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ask_ai_instructions), style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                listOf(false to R.string.ask_ai_send_all, true to R.string.ask_ai_short_links).forEachIndexed {
                        index,
                        (short, label)
                    ->
                    ToggleButton(
                        checked = withLinks == short,
                        onCheckedChange = { onChange(short) },
                        shapes = if (index ==
                            0
                        ) {
                            ButtonGroupDefaults.connectedLeadingButtonShapes()
                        } else {
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        },
                        // The card is surfaceContainer, the default unchecked colour: a step up keeps both choices visible.
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (short) "ai-instructions-links" else "ai-instructions-all")
                            .semantics {
                                role = Role.RadioButton
                                selected = withLinks == short
                            }
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
            Text(
                stringResource(if (withLinks) R.string.ask_ai_links_text else R.string.ask_ai_all_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HowItWorks() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.how_title), style = MaterialTheme.typography.titleSmall)
            Step(1, stringResource(R.string.ask_ai_step_share))
            Step(2, stringResource(R.string.ask_ai_step_chat))
            Step(3, stringResource(R.string.ask_ai_step_back))
            Note(Icons.Filled.Lock, stringResource(R.string.ask_ai_no_ai))
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** A small line with an icon: a fact worth knowing, not something to act on. */
@Composable
private fun Note(icon: ImageVector, text: String, textTag: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = textTag?.let { Modifier.testTag(it) } ?: Modifier
        )
    }
}
