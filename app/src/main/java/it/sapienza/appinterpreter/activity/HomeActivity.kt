package it.sapienza.appinterpreter.activity

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import it.sapienza.androidratio.appratio.BuildConfig
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.ai.AskAi
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.optString
import it.sapienza.appinterpreter.ui.ActionMenuItem
import it.sapienza.appinterpreter.ui.AppIconView
import it.sapienza.appinterpreter.ui.AppTheme
import it.sapienza.appinterpreter.utils.AppLibrary
import it.sapienza.appinterpreter.utils.DomainController
import it.sapienza.appinterpreter.utils.HomeShortcuts
import it.sapienza.appinterpreter.utils.ShareApp
import it.sapienza.appinterpreter.utils.of
import kotlinx.serialization.json.JsonObject

/**
 * The first screen: what Appkin does, the saved apps (open one, add it to the home screen, or delete it),
 * the examples, and the ways to get a new app. An app opens on top of it, so Back comes back here. Home-screen
 * shortcuts skip it and open their app directly.
 */
class HomeActivity : AppCompatActivity() {

    /** A JSON file goes through the same path as one opened from Files: API keys, problems, then the app. */
    private val openFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        startActivity(
            Intent(this, ManageFileInActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    // Read again on every return, since an app opened from here saves itself in the library.
    private var apps by mutableStateOf(emptyList<AppLibrary.SavedApp>())
    private var current by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_Compose)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) HomeShortcuts(this).refreshPinned()
        setContent {
            AppTheme {
                var deleting by remember { mutableStateOf<AppLibrary.SavedApp?>(null) }
                var menuExpanded by remember { mutableStateOf(false) }
                var aboutOpen by remember { mutableStateOf(false) }
                var termsOpen by remember { mutableStateOf(false) }
                // Closed until opened: the user's apps and the examples come first.
                var explained by remember { mutableStateOf(preferences().getBoolean(EXPLAINED_KEY, false)) }

                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        actions = {
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { menuExpanded = true }) {
                                Icon(
                                    painterResource(R.drawable.baseline_more_vert_24),
                                    contentDescription = stringResource(R.string.menu_open)
                                )
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                val close = { menuExpanded = false }
                                ActionMenuItem(stringResource(R.string.menu_new_app), close, {
                                    EditorActivity.newApp(this@HomeActivity)
                                }, R.drawable.baseline_insert_drive_file_24)
                                HorizontalDivider()
                                ActionMenuItem(stringResource(R.string.menu_ask_ai), close, {
                                    AskAiActivity.newApp(this@HomeActivity)
                                }, R.drawable.baseline_track_changes_24)
                                ActionMenuItem(
                                    stringResource(R.string.menu_paste_ai),
                                    close,
                                    ::pasteFromAi,
                                    R.drawable.baseline_merge_type_24
                                )
                                HorizontalDivider()
                                ActionMenuItem(stringResource(R.string.home_open_file), close, {
                                    openFile.launch("application/json")
                                }, R.drawable.baseline_folder_open_24)
                                ActionMenuItem(
                                    stringResource(R.string.home_format_guide),
                                    close,
                                    ::openFormatGuide,
                                    R.drawable.baseline_menu_book_24
                                )
                                ActionMenuItem(stringResource(R.string.home_menu_about), close, {
                                    aboutOpen = true
                                }, R.drawable.baseline_perm_identity_24)
                            }
                        }
                    )
                }) { padding ->
                    // The list scrolls behind the navigation bar, ending clear of it.
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                        item {
                            HowItWorks(expanded = explained, onToggle = {
                                explained = !explained
                                preferences().edit { putBoolean(EXPLAINED_KEY, explained) }
                            }, onOpenGuide = ::openFormatGuide)
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = {
                                    EditorActivity.newApp(this@HomeActivity)
                                }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.home_new_app), Modifier.padding(start = 8.dp))
                                }
                                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = {
                                    AskAiActivity.newApp(this@HomeActivity)
                                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.home_ask_ai)) }
                            }
                        }
                        item { SectionHeader(stringResource(R.string.home_your_apps)) }
                        if (apps.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.home_your_apps_empty),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                            ListItem(
                                supportingContent = {
                                    // Under a minute, "0 minutes ago" reads oddly.
                                    val opened = if (System.currentTimeMillis() - app.savedAt <
                                        DateUtils.MINUTE_IN_MILLIS
                                    ) {
                                        stringResource(R.string.home_opened_just_now)
                                    } else {
                                        stringResource(
                                            R.string.home_opened,
                                            DateUtils.getRelativeTimeSpanString(app.savedAt)
                                        )
                                    }
                                    Text(
                                        if (app.name ==
                                            current
                                        ) {
                                            stringResource(R.string.home_open_now, opened)
                                        } else {
                                            opened
                                        }
                                    )
                                },
                                leadingContent = { AppIconView(app.name, app.icon) },
                                trailingContent = {
                                    Row {
                                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                            if (!HomeShortcuts(this@HomeActivity).pin(app)) {
                                                Toast.makeText(
                                                    this@HomeActivity,
                                                    R.string.shortcuts_unsupported,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }) {
                                            Icon(
                                                Icons.Filled.Home,
                                                contentDescription = stringResource(R.string.home_add_to_home_screen)
                                            )
                                        }
                                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                            ShareApp.share(this@HomeActivity, app.name, app.json)
                                        }) {
                                            Icon(
                                                Icons.Filled.Share,
                                                contentDescription = stringResource(R.string.home_share)
                                            )
                                        }
                                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { deleting = app }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = stringResource(R.string.home_delete)
                                            )
                                        }
                                    }
                                },
                                colors = groupedItemColors(),
                                modifier = Modifier.inGroup(index, apps.size).clickable {
                                    DomainController.setJsonFile(app.json, this@HomeActivity, app.configurationId)
                                    openCurrentApp()
                                }
                            ) { Text(app.name) }
                        }
                        item { SectionHeader(stringResource(R.string.home_examples)) }
                        itemsIndexed(EXAMPLES) { index, (name, about, resource, icon) ->
                            val title = stringResource(name)
                            ListItem(
                                supportingContent = { Text(stringResource(about)) },
                                leadingContent = { AppIconView(title, icon) },
                                colors = groupedItemColors(),
                                modifier = Modifier.inGroup(index, EXAMPLES.size).clickable {
                                    DomainController.setJsonFile(resource, this@HomeActivity)
                                    openCurrentApp()
                                }
                            ) { Text(title) }
                        }
                        item {
                            Text(
                                stringResource(R.string.home_examples_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                if (aboutOpen) {
                    AlertDialog(
                        onDismissRequest = { aboutOpen = false },
                        title = { Text(stringResource(R.string.app_name)) },
                        text = {
                            Column(
                                Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                                Text(stringResource(R.string.home_about_text))
                                Text(stringResource(R.string.about_contact))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { reportProblem() }) {
                                        Text(stringResource(R.string.about_report))
                                    }
                                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { termsOpen = true }) {
                                        Text(stringResource(R.string.about_terms))
                                    }
                                }
                                Text(
                                    stringResource(R.string.about_credits),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Text(stringResource(R.string.about_credits_commons))
                                // TMDB's terms: their logo, less prominent than ours, beside this notice.
                                Image(
                                    painterResource(R.drawable.tmdb_logo),
                                    contentDescription = stringResource(R.string.about_tmdb_logo),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Text(stringResource(R.string.about_credits_tmdb))
                            }
                        },
                        confirmButton = {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                                aboutOpen = false
                            }) { Text(stringResource(R.string.about_ok)) }
                        },
                        dismissButton = {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                                startActivity(Intent(Intent.ACTION_VIEW, GITHUB.toUri()))
                            }) { Text(stringResource(R.string.home_about_github)) }
                        }
                    )
                }
                if (termsOpen) {
                    AlertDialog(
                        onDismissRequest = { termsOpen = false },
                        title = { Text(stringResource(R.string.about_terms)) },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text(stringResource(R.string.terms_of_use))
                            }
                        },
                        confirmButton = {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                                termsOpen = false
                            }) { Text(stringResource(R.string.about_ok)) }
                        }
                    )
                }
                deleting?.let { app ->
                    AlertDialog(
                        onDismissRequest = { deleting = null },
                        title = { Text(stringResource(R.string.home_delete_title, app.name)) },
                        text = { Text(stringResource(R.string.home_delete_text)) },
                        confirmButton = {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                                AppLibrary.of(this).delete(app.id)
                                HomeShortcuts(this).disable(app.id)
                                apps = AppLibrary.of(this).all()
                                deleting = null
                            }) { Text(stringResource(R.string.home_delete), color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                                deleting = null
                            }) { Text(stringResource(R.string.home_cancel)) }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        apps = AppLibrary.of(this).all()
        current =
            runCatching {
                (JsonText.parse(DomainController.jsonFile(this)) as JsonObject).optString("name")
            }.getOrNull()
    }

    private fun pasteFromAi() {
        val answer = AskAi.clipboardText(this)
        if (answer == null) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_LONG).show()
        } else {
            EditorActivity.aiAnswer(this, answer)
        }
    }

    /** An email to the contact address, with Appkin's version and the phone's Android version filled in. */
    private fun reportProblem() {
        val subject = getString(R.string.report_subject, BuildConfig.VERSION_NAME, Build.VERSION.RELEASE)
        val mail = Intent(Intent.ACTION_SENDTO, "mailto:$CONTACT".toUri()).putExtra(Intent.EXTRA_SUBJECT, subject)
        try {
            startActivity(mail)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.report_no_email, CONTACT), Toast.LENGTH_LONG).show()
        }
    }

    private fun openFormatGuide() = startActivity(Intent(Intent.ACTION_VIEW, FORMAT_GUIDE.toUri()))

    private fun openCurrentApp() = startActivity(Intent(this, MainActivity::class.java))

    private fun preferences() = getSharedPreferences("Home", Context.MODE_PRIVATE)

    companion object {
        private const val EXPLAINED_KEY = "how_it_works_open"
        private const val GITHUB = "https://github.com/wakirox/JsonAppCreator"
        private const val CONTACT = "info@wakirox.app"

        /** The format reference written for people; docs/LLM_GUIDE.md is the one for AI apps. */
        private const val FORMAT_GUIDE = "$GITHUB/blob/master/docs/CONFIGURATION_V3.md"

        /** The home page as the only screen, from an app opened here or from a home-screen shortcut. */
        fun open(context: Context) = context.startActivity(
            Intent(
                context,
                HomeActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}

/** What the app does, in plain words. Open until it's closed once. */
@Composable
private fun HowItWorks(expanded: Boolean, onToggle: () -> Unit, onOpenGuide: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(
                onClick = onToggle
            ).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.how_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.how_hide else R.string.how_show)
            )
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                STEPS.forEachIndexed { index, (title, text) ->
                    Step(index + 1, stringResource(title), stringResource(text))
                }
                Text(
                    stringResource(R.string.how_menu_note),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onOpenGuide,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.how_format_guide))
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A list row as one piece of a rounded group: round at the group's ends, nearly square where rows meet. */
private fun Modifier.inGroup(index: Int, count: Int): Modifier {
    fun corner(end: Boolean): Dp = if (end) 20.dp else 4.dp
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(corner(first), corner(first), corner(last), corner(last))
    return padding(start = 16.dp, end = 16.dp, bottom = if (last) 0.dp else 2.dp).clip(shape)
}

@Composable
private fun groupedItemColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

/** The explanation's steps: title and text. */
private val STEPS = listOf(
    R.string.how_step_file_title to R.string.how_step_file_text,
    R.string.how_step_run_title to R.string.how_step_run_text,
    R.string.how_step_get_title to R.string.how_step_get_text,
    R.string.how_step_keep_title to R.string.how_step_keep_text
)

/** A bundled example: its name, what it shows, its configuration, and the icon that configuration sets. */
private data class Example(val name: Int, val about: Int, val configuration: Int, val icon: String)

private val EXAMPLES = listOf(
    Example(R.string.example_masterpieces, R.string.example_masterpieces_about, R.raw.v3_masterpieces, "🖼️"),
    Example(R.string.example_app_ideas, R.string.example_app_ideas_about, R.raw.v3_app_ideas, "💡"),
    Example(R.string.example_movies, R.string.example_movies_about, R.raw.v3_the_movie_db, "🎬")
)
