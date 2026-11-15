package it.sapienza.appinterpreter.ios

import io.ktor.client.engine.darwin.Darwin
import it.sapienza.appinterpreter.ai.AiContext
import it.sapienza.appinterpreter.ai.AiInstructionsChoice
import it.sapienza.appinterpreter.ai.ShareLinks
import it.sapienza.appinterpreter.ai.TokenEstimate
import it.sapienza.appinterpreter.editor.ConfigurationChecker
import it.sapienza.appinterpreter.editor.EditorIssue
import it.sapienza.appinterpreter.editor.ImageLinks
import it.sapienza.appinterpreter.editor.SchemaSuggestions
import it.sapienza.appinterpreter.editor.Snippets
import it.sapienza.appinterpreter.editor.Suggestions
import it.sapienza.appinterpreter.editor.prettyJson
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.sameValue
import it.sapienza.appinterpreter.runtime.CurrentConfiguration
import it.sapienza.appinterpreter.runtime.usecase.FindMissingApiKeysUseCase
import it.sapienza.appinterpreter.security.ApiKeyPlaceholders
import it.sapienza.appinterpreter.security.ConfigurationKeys
import it.sapienza.appinterpreter.security.SecretStore
import it.sapienza.appinterpreter.storage.Photos
import it.sapienza.appinterpreter.utils.AppLibrary
import it.sapienza.appinterpreter.utils.SharedApps
import it.sapienza.appinterpreter.validation.ConfigurationValidator
import it.sapienza.appinterpreter.validation.V3Schema
import it.sapienza.appinterpreter.validation.ValidatedConfigurationParser
import kotlinx.coroutines.runBlocking

/** The schema's validator, built once: it is the slow part of starting anything that checks a configuration. */
internal object SharedValidator {
    val validator: ConfigurationValidator by lazy { ConfigurationValidator.fromSchema(V3Schema.JSON) }
}

/** What a file or an AI answer is, once read: shown to the user before it opens (see Android's ReceivedAppDialog). */
class ReceivedInfo(
    val json: String,
    val name: String,
    val author: String?,
    val version: String,
    val webAddresses: List<String>,
    val apiKeys: List<String>,
    /** A saved app has this name but other content: replaced, or both kept. */
    val sameNameAsSaved: Boolean,
    /** The very same app is already saved. */
    val unchanged: Boolean
)

/** The API keys an app still needs; [saved] are the ones the Keychain already has. */
class MissingKeys(val names: List<String>, val saved: List<String>)

/**
 * What the iPhone app's own screens (Home, the editor, Ask your AI app) take from the shared engine, in shapes Swift
 * calls without coroutines: the same checks, library, AI instructions and file handling Android uses.
 */
class ShellKit(private val secrets: SecretStore, bundledKeys: String = "") {
    private val bundled = ConfigurationKeys.decodeBundledValues(bundledKeys)

    val library = AppLibrary(UserDefaultsAppStore(prefix = "appkin.library."))
    private val instructions = AiInstructionsChoice(UserDefaultsAppStore(prefix = "appkin.askai."))
    private val checker by lazy { ConfigurationChecker(SharedValidator.validator) }
    private val parser by lazy { ValidatedConfigurationParser(SharedValidator.validator) }
    private val schemaSuggestions by lazy { SchemaSuggestions(V3Schema.JSON) }

    /** Schema-driven autocomplete suggestions for [json] at UTF-16 [cursor]. */
    fun suggestions(json: String, cursor: Int): Suggestions = schemaSuggestions.assist(json, cursor).suggestions

    /** The live check: every problem in [json], placed in the text. */
    fun check(json: String): List<EditorIssue> = checker.check(json)

    /** The web images [json] shows (at most [ImageLinks.MAX]), for the app to ask the status of. */
    fun imageUrls(json: String): List<String> {
        val tree = runCatching { JsonText.parse(json) }.getOrNull() ?: return emptyList()
        return ImageLinks.find(tree).take(ImageLinks.MAX).map { it.second }
    }

    /** The problems for images that don't load: [codes] are the HTTP statuses of [urls] (-1: no answer). */
    fun imageProblems(json: String, urls: List<String>, codes: List<Int>): List<EditorIssue> {
        val status = urls.zip(codes).toMap()
        return runBlocking { ImageLinks.problems(json) { url -> status[url]?.takeIf { it >= 0 } } }
    }

    fun line(json: String, offset: Int): Int = ConfigurationChecker.lineOf(json, offset)

    fun prettyPrinted(json: String): String = prettyJson(json)

    fun canParse(json: String): Boolean = runCatching { JsonText.parse(json) }.isSuccess

    fun templateForNewApp(): String = Snippets.newApp.replaceFirst("\"My app\"", "\"${library.freeName("My app")}\"")

    /** [json] read as an app that can open now, or null when it has problems (the editor shows them). */
    fun received(json: String): ReceivedInfo? {
        val app = runCatching { parser.parse(json) }.getOrNull() ?: return null
        val saved = library.get(AppLibrary.idOf(app.name))
        val arrival = SharedApps.arrival(json, saved)
        return ReceivedInfo(
            json = json,
            name = app.name,
            author = app.author,
            version = app.version,
            webAddresses = SharedApps.webAddresses(json),
            apiKeys = ApiKeyPlaceholders.names(json).toList(),
            sameNameAsSaved = arrival is SharedApps.Arrival.SameName,
            unchanged = arrival is SharedApps.Arrival.Unchanged
        )
    }

    fun renamed(json: String, name: String): String = SharedApps.renamed(json, name)

    fun fileName(name: String): String = SharedApps.fileName(name)

    fun sameJson(a: String, b: String): Boolean =
        runCatching { JsonText.sameValue(JsonText.parse(a), JsonText.parse(b)) }.getOrDefault(false)

    fun configurationId(json: String): String = ApiKeyPlaceholders.configurationId(json)

    /** The keys [json] needs that the Keychain doesn't have yet under [configurationId]; null when it has them all. */
    fun missingKeys(json: String, configurationId: String): MissingKeys? {
        val keys = ConfigurationKeys(secrets, bundled) { configurationId }
        val missing = FindMissingApiKeysUseCase(CurrentConfiguration { json }, keys)() ?: return null
        return MissingKeys(missing.names.toList(), missing.savedNames.toList())
    }

    fun saveKeys(json: String, configurationId: String, values: Map<String, String>) =
        ConfigurationKeys(secrets, bundled) { configurationId }.saveForCurrentConfiguration(json, values)

    // Ask your AI app

    var shortInstructions: Boolean
        get() = instructions.withLinks
        set(value) {
            instructions.withLinks = value
        }

    /** The instructions for an AI app: all of them, or with [withLinks] the short version that links to the rest. */
    fun prompt(
        guide: String,
        checklist: String,
        signUpForm: String,
        restList: String,
        currentApp: String?,
        request: String?,
        withLinks: Boolean
    ): String = AiContext.build(
        guide = guide,
        schemaJson = V3Schema.JSON,
        examples = AiContext.examples(checklist, signUpForm, restList),
        currentApp = currentApp,
        request = request,
        links = AI_LINKS.takeIf { withLinks }
    )

    fun tokens(text: String): Int = TokenEstimate.of(text)

    fun roundedTokens(tokens: Int): Int = TokenEstimate.rounded(tokens)

    /** The configuration in an AI answer (a fenced block, or from the first { to the last }), or null. */
    fun extractJson(answer: String): String? = AiContext.extractJson(answer)

    fun fixRequest(json: String, issues: List<EditorIssue>): String = AiContext.fixRequest(json, issues, V3Schema.JSON)

    /** A file name for the configuration, from its name: "my-app.json". */
    fun fileNameFor(json: String): String = AiContext.fileName(json)

    fun findShareLink(text: String): String? = ShareLinks.find(text)

    fun shareLinkService(link: String): String = ShareLinks.service(link)

    fun readShareLink(link: String, checklist: String, signUpForm: String, restList: String): String? = runCatching {
        val examples = AiContext.examples(checklist, signUpForm, restList).map { it.json }
        val client = ShareLinks.client(Darwin.create())
        try {
            runBlocking { ShareLinks.configuration(client, link, examples) }
        } finally {
            client.close()
        }
    }.getOrNull()

    /** Every "appkin-photo:<name>" reference still present in saved collection data, for orphan photo cleanup. */
    fun keptPhotoReferences(): Set<String> {
        val saved = UserDefaultsAppStore()
        return Photos.referencesIn(saved.keys().joinToString { saved.get(it).orEmpty() })
    }

    private companion object {
        const val DOCS = "docs/AI_CONTEXT.md"
        val AI_LINKS = AiContext.Links(
            page = "https://github.com/wakirox/JsonAppCreator/blob/master/$DOCS",
            raw = "https://raw.githubusercontent.com/wakirox/JsonAppCreator/master/$DOCS"
        )
    }
}
