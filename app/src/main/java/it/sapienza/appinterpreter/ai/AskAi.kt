package it.sapienza.appinterpreter.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import it.sapienza.androidratio.appratio.BuildConfig
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.editor.EditorIssue
import it.sapienza.appinterpreter.validation.ConfigurationValidator

/**
 * Working with an AI app the user already has. Appkin contains no AI: it shares instructions to the AI app
 * through Android's share sheet, and receives the AI's answer back the same way (see EditorActivity).
 */
object AskAi {
    private const val GUIDE_ASSET = "LLM_GUIDE.md"

    /** The example configurations exactly as the prompt shows them, to tell them apart from an AI's answer. */
    fun promptExamples(context: Context): List<String> {
        fun raw(id: Int) = context.resources.openRawResource(id).bufferedReader().use { it.readText() }
        return AiContext.examples(
            raw(R.raw.v3_ai_checklist),
            raw(R.raw.v3_race_signup),
            raw(R.raw.v3_open_access_art)
        ).map {
            it.json
        }
    }

    /** docs/AI_CONTEXT.md on GitHub at the commit this app was built from, which the short instructions link to. */
    val links = AiContext.Links(
        page = "https://github.com/wakirox/JsonAppCreator/blob/${BuildConfig.AI_DOCS_REF}/docs/AI_CONTEXT.md",
        raw = "https://raw.githubusercontent.com/wakirox/JsonAppCreator/" +
            "${BuildConfig.AI_DOCS_REF}/docs/AI_CONTEXT.md"
    )

    /** The instructions: everything, or with [withLinks] the short version that links to the rest. */
    fun context(context: Context, currentApp: String?, request: String?, withLinks: Boolean = false): String {
        fun raw(id: Int) = context.resources.openRawResource(id).bufferedReader().use { it.readText() }
        return AiContext.build(
            guide = asset(context, GUIDE_ASSET),
            schemaJson = asset(context, ConfigurationValidator.ASSET_PATH),
            examples = AiContext.examples(
                raw(R.raw.v3_ai_checklist),
                raw(R.raw.v3_race_signup),
                raw(R.raw.v3_open_access_art)
            ),
            currentApp = currentApp,
            request = request,
            links = links.takeIf { withLinks }
        )
    }

    /** Roughly how many tokens of the AI chat the instructions take (see [TokenEstimate]). */
    fun tokens(context: Context, currentApp: String?, request: String?, withLinks: Boolean = false): Int =
        TokenEstimate.of(context(context, currentApp, request, withLinks))

    /** A message asking the AI app to fix [issues] in [json], with the reference for the parts they are in. */
    fun fixRequest(context: Context, json: String, issues: List<EditorIssue>): String =
        AiContext.fixRequest(json, issues, asset(context, ConfigurationValidator.ASSET_PATH))

    private fun asset(context: Context, name: String) = context.assets.open(name).bufferedReader().use { it.readText() }

    /** Opens the share sheet with [text], to send to the AI app the user picks. */
    fun shareToAiApp(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.ask_ai_share_chooser))
        // Not to Appkin itself, which receives AI answers through the same share sheet.
        chooser.putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(context, "it.sapienza.appinterpreter.activity.EditorActivity"))
        )
        context.startActivity(chooser)
    }

    fun copy(context: Context, text: String, done: String) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Appkin", text))
        Toast.makeText(context, done, Toast.LENGTH_LONG).show()
    }

    /** The clipboard's text, such as an answer copied from an AI app. */
    fun clipboardText(context: Context): String? {
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip ?: return null
        return (0 until clip.itemCount).joinToString("\n") { clip.getItemAt(it).coerceToText(context).toString() }
            .takeIf { it.isNotBlank() }
    }

    /** The JSON in the clipboard's text, if it holds an AI answer with one. */
    fun jsonFromClipboard(context: Context): String? = clipboardText(context)?.let(AiContext::extractJson)
}
