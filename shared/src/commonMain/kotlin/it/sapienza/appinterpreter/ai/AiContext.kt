package it.sapienza.appinterpreter.ai

import it.sapienza.appinterpreter.editor.ConfigurationChecker
import it.sapienza.appinterpreter.editor.EditorIssue
import it.sapienza.appinterpreter.editor.prettyJson
import it.sapienza.appinterpreter.json.JsonNode
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.sameValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Everything an AI assistant needs to write an Appkin configuration, in one Markdown text: the rules from
 * docs/LLM_GUIDE.md, a reference of every view, form field and event generated from the schema (so it can't drift
 * from it), complete examples, and optionally the app to change and what to build.
 */
object AiContext {

    data class Example(val title: String, val about: String, val json: String)

    /**
     * Where the full context is published: docs/AI_CONTEXT.md at the commit the app was built from, so it describes
     * exactly what this app runs. [page] is GitHub's page (with an anchor for every heading), [raw] the plain text.
     */
    data class Links(val page: String, val raw: String)

    /**
     * The whole context; with [links], the short version instead: the rules and an outline of every view, form field
     * and event, each linking to its full description, and links to the examples.
     */
    fun build(
        guide: String,
        schemaJson: String,
        examples: List<Example>,
        currentApp: String? = null,
        request: String? = null,
        links: Links? = null
    ): String {
        val reference = SchemaReference(schemaJson)
        val rules = rules(guide).trim()
        val full =
            document(
                FULL_INTRO,
                rules,
                reference.markdown().trim() + "\n" + examplesMarkdown(examples),
                currentApp,
                request
            )
        if (links == null) return full
        val anchors = GitHubAnchors.of(full)
        val intro =
            "Appkin is an app that turns a JSON configuration into a native app. You write the " +
                "configuration; the app validates it against the v3 schema and shows it. This is the short version " +
                "of the contract: the rules, then every view, form field and event with its properties. The complete " +
                "reference, with what each property means and three full examples, is at ${links.raw}. If you can " +
                "open links, read it, or the linked part, for anything you're unsure of. Use only what they describe."
        return document(
            intro,
            rules,
            outline(reference, anchors, links) + "\n" + exampleLinks(examples, anchors, links),
            currentApp,
            request
        )
    }

    private const val FULL_INTRO =
        "Appkin is an app that turns a JSON configuration into a native app. You write the " +
            "configuration; the app validates it against the v3 schema and shows it. This document is the complete " +
            "contract: use only what it describes."

    private fun document(intro: String, rules: String, reference: String, currentApp: String?, request: String?) =
        buildString {
            appendLine("# Appkin: context for AI assistants")
            appendLine()
            appendLine(intro)
            appendLine()
            appendLine(rules)
            appendLine()
            append(reference)
            if (currentApp != null) {
                appendLine()
                appendLine("## The current configuration")
                appendLine()
                appendLine("Change this configuration; keep what the request doesn't mention.")
                appendLine()
                appendLine("```json")
                appendLine(currentApp.trim())
                appendLine("```")
            }
            appendLine()
            appendLine("## Your task")
            appendLine()
            val what =
                request?.trim()?.takeIf { it.isNotEmpty() } ?: "<describe the app: its screens, data and actions>"
            appendLine(
                if (currentApp != null) {
                    "Change the current configuration: $what"
                } else {
                    "Create an Appkin v3 configuration for this app: $what"
                }
            )
            appendLine()
            append(
                "If you can create files, give me the complete configuration as a downloadable file named " +
                    "${fileName(currentApp)}, containing only the JSON. If you can't, reply with only the " +
                    "complete JSON, starting with { and ending with }: no comments or explanations. I will open your " +
                    "file or share your answer in the Appkin app, which shows it; if it reports problems, I " +
                    "will send them to you to fix."
            )
            appendLine()
        }

    private fun examplesMarkdown(examples: List<Example>) = buildString {
        examples.forEachIndexed { index, example ->
            appendLine()
            appendLine("## Example ${index + 1}: ${example.title}")
            appendLine()
            appendLine(example.about)
            appendLine()
            appendLine("```json")
            appendLine(example.json.trim())
            appendLine("```")
        }
    }

    /** Each part of the reference on one line: its properties (required ones in bold) and a link to the full part. */
    private fun outline(reference: SchemaReference, anchors: GitHubAnchors, links: Links) = buildString {
        appendLine("## Reference")
        appendLine()
        appendLine(
            "Every property allowed, **required** ones in bold; unknown properties are rejected. Each link opens the " +
                "full description: types, allowed values and what the property does."
        )
        reference.groups.forEach { group ->
            appendLine()
            appendLine("### ${group.heading}")
            appendLine()
            group.about?.let {
                appendLine(it)
                appendLine()
            }
            group.sections.forEach { section ->
                val names = section.properties.joinToString(", ") { if (it.required) "**${it.name}**" else it.name }
                val anchor = if (section.kind ==
                    SchemaReference.Kind.Root
                ) {
                    anchors.of(group.heading)
                } else {
                    anchors.of(section.heading, after = group.heading)
                }
                val name = if (section.kind == SchemaReference.Kind.Root) "Root" else section.heading
                append("- $name: $names")
                anchor?.let { append(" ([details](${links.page}#$it))") }
                appendLine()
            }
        }
    }

    private fun exampleLinks(examples: List<Example>, anchors: GitHubAnchors, links: Links) = buildString {
        appendLine()
        appendLine("## Examples")
        appendLine()
        appendLine("Complete, valid configurations to follow, in the full reference:")
        appendLine()
        examples.forEachIndexed { index, example ->
            val heading = "Example ${index + 1}: ${example.title}"
            val anchor = anchors.of(heading)?.let { "${links.page}#$it" } ?: links.raw
            appendLine("- [$heading]($anchor)")
        }
    }

    /**
     * The worked examples, from the bundled configurations: a checklist saved on the phone with a detail screen, a form
     * with an alert, and a list loaded from a REST API.
     */
    fun examples(checklist: String, signUpForm: String, restList: String): List<Example> = listOf(
        Example(
            "a checklist saved on the phone, with a detail screen",
            "The list starts with its `initialItems`, saved once; `unique` adds the ones a later version brings. " +
                "Ticking runs the `tick` update, and `sort` puts visited places last. Tapping an item shows the " +
                "reusable `place` view.",
            prettyJson(checklist)
        ),
        Example(
            "a form with required fields and an alert",
            "Submitting runs the form's action with the filled-in values, so the alert can use `${'$'}{name}`.",
            prettyJson(signUpForm)
        ),
        Example(
            "a list loaded from a REST API, with a detail screen",
            "`initData` loads the JSON; `mapping` selects its array; each item's fields fill the item view and the detail.",
            prettyJson(restList)
        )
    )

    /**
     * A tree as the app wrote it with Jackson's default mapper and then formatted it: decimals as a Double prints
     * them, then pretty-printed.
     */
    private fun written(tree: JsonElement): String = prettyJson(JsonText.withDoubleNumbers(tree).toString())

    /** A file name for the configuration: from its "name", else "app.json". */
    fun fileName(json: String?): String {
        val name = json?.let { Regex(""""name"\s*:\s*"([^"]*)"""").find(it)?.groupValues?.get(1) }.orEmpty()
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return (slug.ifEmpty { "app" }) + ".json"
    }

    /** The guide's rules for a model: its sections without the repository-specific ones. */
    private fun rules(guide: String): String {
        val sections = guide.split(Regex("(?m)^(?=## )"))
        return sections.drop(1)
            .filterNot { section -> REPOSITORY_SECTIONS.any { section.startsWith("## $it") } }
            .joinToString("")
    }

    private val REPOSITORY_SECTIONS = listOf("Validate generated output", "Recommended prompt")

    /**
     * The JSON object in an AI answer: the first fenced block that holds one, else everything from the first { to the
     * last }. Null when there is none.
     */
    fun extractJson(answer: String): String? {
        val candidate = Regex("(?s)```[a-zA-Z]*\\s*\\n(.*?)```").findAll(answer)
            .map { it.groupValues[1].trim() }
            .firstOrNull { it.startsWith("{") }
            ?: answer.indexOf('{').let { start ->
                val end = answer.lastIndexOf('}')
                if (start >= 0 && end > start) answer.substring(start, end + 1).trim() else null
            }
            ?: return null
        val json = if (parses(candidate)) candidate else unescapeMarkdown(candidate).takeIf(::parses) ?: candidate
        return restorePlaceholders(json)
    }

    /**
     * The last configuration in a page or API response holding a whole conversation, such as a shared chat, where
     * the AI's answers sit inside JavaScript or JSON strings, escaped once or more: each level of escaping is undone
     * in turn until an object with "schemaVersion" and "mainView" parses. Exact copies of [promptExamples] (the
     * prompt's examples, which the conversation also contains) and the guide's skeleton are skipped; an AI's own app
     * is returned whatever its name, even one changed from an example.
     */
    fun lastConfiguration(page: String, promptExamples: Collection<String> = emptyList()): String? {
        val examples = promptExamples.mapNotNull { runCatching { JsonText.parse(it) }.getOrNull() }
        var text = page
        repeat(MAX_ESCAPING) {
            var at = text.lastIndexOf("\"schemaVersion\"")
            while (at >= 0) {
                var start = text.lastIndexOf('{', at)
                var tries = 0
                while (start >= 0 && tries < 64) {
                    val node = runCatching { JsonText.parseAt(text, start) }.getOrNull() as? JsonObject
                    if (node != null && "schemaVersion" in node && "mainView" in node) {
                        if (examples.none { JsonText.sameValue(it, node) } &&
                            JsonNode(node).path("name").asText() != GUIDE_SKELETON
                        ) {
                            return restorePlaceholders(written(node))
                        }
                        break
                    }
                    start = text.lastIndexOf('{', start - 1)
                    tries++
                }
                at = text.lastIndexOf("\"schemaVersion\"", at - 1)
            }
            text = unescapeString(text)
        }
        return null
    }

    private const val MAX_ESCAPING = 4

    /** The name in the guide's skeleton configuration, which every shared chat contains. */
    private const val GUIDE_SKELETON = "Application name"

    /** One level of JSON string escaping undone: \" becomes ", \n a line break, \u003c a <, \\ a \. */
    private fun unescapeString(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                val next = text[i + 1]
                if (next == 'u' && i + 5 < text.length) {
                    text.substring(i + 2, i + 6).toIntOrNull(16)?.let {
                        append(it.toChar())
                        i += 6
                        continue
                    }
                }
                append(
                    when (next) {
                        'n' -> '\n'
                        't' -> '\t'
                        'r' -> '\r'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        else -> next
                    }
                )
                i += 2
                continue
            }
            append(c)
            i++
        }
    }

    /**
     * AI answers sometimes write ${field} placeholders without the $ (Gemini wrote {thumb} and {title}), and chat apps
     * that render $...$ as maths can eat it too. JSON's own braces never enclose a bare name, so a {name} is always a
     * placeholder that lost its $, and gets it back.
     */
    private fun restorePlaceholders(json: String) = BARE_PLACEHOLDER.replace(json) { match ->
        // The space before the $ often went with it ("guests on{date}", "24{ingredient}"): put it back after words
        // and punctuation, but not inside URLs or names ("?c={category}", "item-{id}").
        val before = json.getOrNull(match.range.first - 1)
        val escape = json.getOrNull(match.range.first - 2) == '\\' // as in "\n{genre}": a line break, not a word
        val space = if (before != null && !escape && (before.isLetterOrDigit() || before in SPACE_AFTER)) " " else ""
        "$space\${${match.groupValues[1]}}"
    }

    private const val SPACE_AFTER = ":,;.!})•·"

    private val BARE_PLACEHOLDER = Regex("""(?<!\${'$'})\{([A-Za-z_][\w.]*)\}""")

    /**
     * Copying from a chat app can add Markdown escapes such as \[ and \_, which JSON never allows: they are removed.
     * Only used on text that isn't valid JSON as it is.
     */
    private fun unescapeMarkdown(text: String) = MARKDOWN_ESCAPE.replace(text, "$1")

    private val MARKDOWN_ESCAPE = Regex("""\\([\[\]{}()_*#`~<>!|+.-])""")

    private fun parses(json: String) = runCatching { JsonText.parse(json) }.isSuccess

    /**
     * A message asking a model to fix the problems the editor found, with their lines and paths, the reference for the
     * parts they are in (with [schemaJson]), and the JSON.
     */
    fun fixRequest(json: String, issues: List<EditorIssue>, schemaJson: String? = null): String = buildString {
        appendLine("Appkin reports these problems in the configuration below:")
        appendLine()
        issues.forEach { issue ->
            val line = issue.offset?.let { ConfigurationChecker.lineOf(json, it) }
            val where = listOfNotNull(line?.let { "line $it" }, issue.path.takeIf { it != "$" }).joinToString(", ")
            appendLine("- ${if (where.isEmpty()) "" else "$where: "}${issue.message}")
        }
        appendLine()
        appendLine(
            "Fix them and give me the complete corrected configuration: as a downloadable file named " +
                "${fileName(json)} if you can create files, otherwise as only the JSON with no comments."
        )
        appendLine()
        val sections = schemaJson?.let { IssueSections(SchemaReference(it)).of(json, issues) }.orEmpty()
        if (sections.isNotEmpty()) {
            appendLine("The reference for these parts:")
            appendLine()
            sections.forEach { (title, text) ->
                appendLine("#### $title")
                appendLine()
                append(text)
            }
        }
        appendLine("```json")
        appendLine(json.trim())
        append("```")
        appendLine()
    }
}
