package it.sapienza.appinterpreter.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import io.ktor.client.engine.okhttp.OkHttp
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.ai.AiContext
import it.sapienza.appinterpreter.ai.AskAi
import it.sapienza.appinterpreter.ai.ShareLinks
import it.sapienza.appinterpreter.editor.ConfigurationChecker
import it.sapienza.appinterpreter.editor.EditorScreen
import it.sapienza.appinterpreter.editor.SchemaSuggestions
import it.sapienza.appinterpreter.editor.Snippets
import it.sapienza.appinterpreter.editor.prettyJson
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.sameValue
import it.sapienza.appinterpreter.ui.AppTheme
import it.sapienza.appinterpreter.utils.AppLibrary
import it.sapienza.appinterpreter.utils.DomainController
import it.sapienza.appinterpreter.utils.PrivateStorage
import it.sapienza.appinterpreter.utils.TextHandover
import it.sapienza.appinterpreter.utils.httpclient.HttpClient
import it.sapienza.appinterpreter.utils.of
import it.sapienza.appinterpreter.validation.ConfigurationValidator
import it.sapienza.appinterpreter.validation.fromAssets
import it.sapienza.appinterpreter.validation.fromSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads shared chat pages with its own OkHttp client: a browser User-Agent, unlike the app's requests. */
private val shareLinkClient by lazy { ShareLinks.client(OkHttp.create()) }

/** Edits the active configuration, or writes a new app from a template, and runs the result. */
class EditorActivity : AppCompatActivity() {

    private var pendingSave: String? = null
    private val saveAs = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingSave ?: return@registerForActivityResult
        pendingSave = null
        uri ?: return@registerForActivityResult
        runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } }
            .onSuccess { Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_Compose)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val externalShare = intent.action == Intent.ACTION_SEND
        val newApp = !externalShare && intent.getBooleanExtra(EXTRA_NEW_APP, false)
        // Shared from an AI app (or any app): the JSON in what was shared, if there is one.
        val shared = if (externalShare) sharedText() else incomingText()
        val fromFile = !externalShare && intent.getBooleanExtra(EXTRA_FROM_FILE, false)
        val fromAiApp = shared?.let { AiContext.extractJson(it) }
        // A link to a shared chat instead of the answer: the chat is read to find the answer.
        val chatLink = shared?.takeIf { fromAiApp == null }?.let(ShareLinks::find)
        val schema = assets.open(ConfigurationValidator.ASSET_PATH).bufferedReader().use { it.readText() }
        val checker = ConfigurationChecker(ConfigurationValidator.fromSchema(schema.byteInputStream()))
        val suggestions = SchemaSuggestions(schema)

        setContent {
            AppTheme {
                var fromChat by rememberSaveable { mutableStateOf<String?>(null) }
                var chatUnreadable by rememberSaveable { mutableStateOf(false) }
                if (chatLink != null && fromChat == null) {
                    if (chatUnreadable) {
                        ChatNotReadDialog(ShareLinks.service(chatLink), onDismiss = ::finish)
                    } else {
                        ReadingChat(chatLink, onBack = ::finish)
                        LaunchedEffect(chatLink) {
                            val found = runCatching {
                                ShareLinks.configuration(
                                    shareLinkClient,
                                    chatLink,
                                    AskAi.promptExamples(this@EditorActivity)
                                )
                            }.getOrNull()
                            if (found != null) fromChat = found else chatUnreadable = true
                        }
                    }
                    return@AppTheme
                }
                if (shared != null && fromAiApp == null && fromChat == null) {
                    NothingToOpenDialog(shared, onDismiss = ::finish)
                    return@AppTheme
                }
                val received = fromChat ?: fromAiApp
                EditorScreen(
                    title = when {
                        received != null && fromFile -> stringResource(R.string.editor_title_opened_file)
                        received != null -> stringResource(R.string.editor_title_from_ai)
                        newApp -> stringResource(R.string.menu_new_app)
                        else -> stringResource(R.string.menu_edit_app)
                    },
                    initialText = when {
                        received != null -> prettyJson(received)

                        newApp -> Snippets.newApp.replaceFirst(
                            "\"My app\"",
                            "\"${AppLibrary.of(this).freeName("My app")}\""
                        )

                        else -> prettyJson(DomainController.jsonFile(this))
                    },
                    fromAiApp = received != null && !fromFile,
                    unsavedSource = when {
                        received == null -> null
                        fromFile -> stringResource(R.string.editor_source_file)
                        chatLink != null -> stringResource(R.string.editor_source_chat, ShareLinks.service(chatLink))
                        else -> stringResource(R.string.editor_source_ai)
                    },
                    check = { text -> withContext(Dispatchers.Default) { checker.check(text) } },
                    imageStatus = HttpClient::imageStatus,
                    suggestions = suggestions,
                    onRun = { text ->
                        // Unchanged content keeps its identity, and so its saved or bundled API keys.
                        val unchanged =
                            chatLink == null && shared == null && !newApp &&
                                sameJson(text, DomainController.jsonFile(this))
                        if (unchanged) {
                            DomainController.setJsonFile(text, this, DomainController.configurationId(this))
                        } else {
                            DomainController.setJsonFile(text, this)
                        }
                        spawnNew(this)
                        // Opened from another app's share sheet, the editor would otherwise stay in that app's task.
                        finish()
                    },
                    onSaveAs = { text ->
                        pendingSave = text
                        saveAs.launch(AiContext.fileName(text))
                    },
                    onAskAi = { text -> AskAiActivity.editorText(this, text) },
                    onBack = ::finish
                )
            }
        }
    }

    /** Text handed over by [aiAnswer] or [openedFile], through a file because it can be large. */
    private fun incomingText(): String? =
        intent.getStringExtra(EXTRA_INCOMING_FILE)?.let { TextHandover.read(this, it) }

    private fun sameJson(a: String, b: String): Boolean =
        runCatching { JsonText.sameValue(JsonText.parse(a), JsonText.parse(b)) }.getOrDefault(false)

    /** The text shared to the app: the message, or the content of a shared text or JSON file. */
    private fun sharedText(): String? {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { return it }
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return null
        if (PrivateStorage.isPrivateFileUri(this, uri)) return null
        return runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
    }

    companion object {
        private const val EXTRA_NEW_APP = "new_app"
        private const val EXTRA_INCOMING_FILE = "incoming_file"
        private const val EXTRA_FROM_FILE = "from_file"

        fun edit(context: Context) = context.startActivity(Intent(context, EditorActivity::class.java))

        fun newApp(context: Context) =
            context.startActivity(Intent(context, EditorActivity::class.java).putExtra(EXTRA_NEW_APP, true))

        /** Opens an AI app's answer (copied, then pasted) like a shared one: "From your AI app". */
        fun aiAnswer(context: Context, answer: String) = context.startActivity(
            Intent(
                context,
                EditorActivity::class.java
            ).putExtra(EXTRA_INCOMING_FILE, TextHandover.write(context, answer))
        )

        /** Opens a file's configuration: one with problems, to fix or send to the AI app, or one to look at first. */
        fun openedFile(context: Context, json: String) = context.startActivity(
            Intent(context, EditorActivity::class.java)
                .putExtra(EXTRA_INCOMING_FILE, TextHandover.write(context, json))
                .putExtra(EXTRA_FROM_FILE, true)
        )
    }
}

/** Shown when something without JSON is shared to the app, such as a link to the chat instead of the AI's answer. */
@Composable
private fun NothingToOpenDialog(shared: String, onDismiss: () -> Unit) {
    val link = shared.trim().let { it.startsWith("http") && !it.contains(' ') }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_nothing_title)) },
        text = {
            Text(stringResource(if (link) R.string.editor_nothing_link else R.string.editor_nothing_no_json))
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(stringResource(R.string.about_ok))
            }
        }
    )
}

/** While a shared chat is read: what is happening, and that it's only reading a public page. */
@Composable
private fun ReadingChat(link: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingIndicator()
        Text(
            stringResource(R.string.editor_reading_chat, ShareLinks.service(link)),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.editor_reading_chat_text),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

/** When a shared chat can't be read, or has no app in it. */
@Composable
private fun ChatNotReadDialog(service: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_chat_failed_title)) },
        text = {
            Text(stringResource(R.string.editor_chat_failed_text, service))
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(stringResource(R.string.about_ok))
            }
        }
    )
}
