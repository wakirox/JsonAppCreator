package it.sapienza.appinterpreter.editor

import it.sapienza.appinterpreter.validation.ConfigurationValidator
import it.sapienza.appinterpreter.validation.V3Schema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The editor's check, suggestions, formatting and snippets, in common code (highlighting is Android UI). */
class EditorLogicTest {
    private val checker by lazy { ConfigurationChecker(ConfigurationValidator.fromSchema(V3Schema.JSON)) }
    private val suggestions by lazy { SchemaSuggestions(V3Schema.JSON) }

    /** The text with "|" marking the cursor, and the labels suggested there. */
    private fun labelsAt(textWithCursor: String): List<String> {
        val cursor = textWithCursor.indexOf('|')
        return suggestions.at(textWithCursor.removeRange(cursor, cursor + 1), cursor).items.map { it.label }
    }

    @Test
    fun aValidConfigurationHasNoIssues() {
        assertEquals(emptyList<EditorIssue>(), checker.check(HELLO))
    }

    @Test
    fun aSchemaIssueIsPlacedAtItsKey() {
        val json = HELLO.replace("\"type\": \"text\"", "\"type\": \"txet\"")
        val issue = checker.check(json).single()
        assertEquals("$.mainView.views[0].type", issue.path)
        assertEquals(json.indexOf("\"type\": \"txet\""), issue.offset)
    }

    @Test
    fun anUnknownPropertyIsPlacedAtItsName() {
        val json = HELLO.replace("\"label\": \"Hello\"", "\"label\": \"Hello\", \"colour\": \"red\"")
        assertEquals(json.indexOf("\"colour\""), checker.check(json).single().offset)
    }

    @Test
    fun aSyntaxErrorIsPlacedWhereTheParserStopped() {
        val json = HELLO.replace("\"label\": \"Hello\"", "\"label\" \"Hello\"")
        val issue = checker.check(json).single()
        val offset = checkNotNull(issue.offset)
        val label = json.indexOf("\"label\"")
        assertTrue(offset in label..json.indexOf("\"Hello\"", label), "offset $offset should be at the missing colon")
    }

    @Test
    fun aMissingReferenceIsReported() {
        val json = HELLO.replace("\"label\": \"Hello\"", "\"label\": \"Hello\", \"action\": { \"ref\": \"nowhere\" }")
        val issue = checker.check(json).single()
        assertTrue(issue.message.contains("nowhere"), issue.message)
    }

    @Test
    fun rootKeysAreSuggestedExceptThoseAlreadyThere() {
        val labels = labelsAt("{\n  \"schemaVersion\": 3,\n  |\n}")
        assertTrue(labels.containsAll(listOf("name", "version", "mainView", "views", "actions")))
        assertTrue("schemaVersion" !in labels)
    }

    @Test
    fun aNewViewOffersTypeAndTheTypesAreItsValues() {
        assertTrue("type" in labelsAt("""{ "mainView": { "type": "layout", "views": [ { | } ] } }"""))
        assertEquals(
            listOf("image"),
            labelsAt("""{ "mainView": { "type": "layout", "views": [ { "type": "im|" } ] } }""")
        )
    }

    @Test
    fun aViewOffersThePropertiesOfItsType() {
        val labels = labelsAt("""{ "mainView": { "type": "layout", "views": [ { "type": "image", | } ] } }""")
        assertTrue(labels.containsAll(listOf("url", "size", "action")))
        assertTrue("views" !in labels && "textStyle" !in labels)
    }

    @Test
    fun aPartialKeyIsCompletedWithItsValueTemplate() {
        val text = """{ "mainView": { "type": "layout", "views": [ { "type": "text", "textSt" } ] } }"""
        val cursor = text.indexOf("textSt") + "textSt".length
        val suggestion = suggestions.at(text, cursor).items.single()
        assertEquals("textStyle", suggestion.label)
        assertEquals("\"textStyle\": \"\"", suggestion.insert)
        assertEquals(text.indexOf("\"textSt\""), suggestion.start)
        assertEquals(text.indexOf("\"textSt\"") + "\"textSt\"".length, suggestion.end)
    }

    @Test
    fun formFieldsAndEventsAreToldApartByType() {
        assertTrue(
            "options" in
                labelsAt("""{ "mainView": { "type": "form", "formElements": [ { "type": "selector", | } ] } }""")
        )
        assertEquals(
            listOf("show", "share"),
            labelsAt("""{ "actions": [ { "id": "a", "event": { "type": "sh|" } } ] }""")
        )
        assertTrue(
            "thenDoOK" in labelsAt("""{ "actions": [ { "id": "a", "event": { "type": "alert", | } } ] }""")
        )
    }

    @Test
    fun requiredAttributesComeFirstAndAreMarked() {
        val text = "{\n  \"schemaVersion\": 3,\n  \n}"
        val result = suggestions.at(text, text.indexOf("  \n}") + 2)
        assertEquals("app", result.heading)
        assertEquals(listOf("name", "version", "mainView"), result.items.filter { it.required }.map { it.label })
        assertEquals(listOf("name", "version", "mainView"), result.items.take(3).map { it.label })

        val list = """{ "mainView": { "type": "list", | } }"""
        val cursor = list.indexOf('|')
        val listResult = suggestions.at(list.removeRange(cursor, cursor + 1), cursor)
        assertEquals("list", listResult.heading)
        assertEquals(listOf("itemView"), listResult.items.filter { it.required }.map { it.label })
    }

    @Test
    fun whileWritingAValueTheObjectsMissingAttributesAreAddedAtItsEnd() {
        val inline = """{ "mainView": { "type": "layout", "views": [ { "type": "image", "url": "https://x" } ] } }"""
        val inValue = inline.indexOf("https://x") + 3
        val result = suggestions.at(inline, inValue)
        assertEquals("image", result.heading)
        val size = result.items.first { it.label == "size" }
        val applied = inline.replaceRange(size.start, size.end, size.insert)
        assertTrue(applied.contains("\"url\": \"https://x\", \"size\": {} }"), applied)
        assertEquals(applied.indexOf("{} }") + 1, size.start + size.cursor)

        val multiline = "{\n  \"mainView\": {\n    \"type\": \"text\",\n    \"label\": \"Hi\"\n  }\n}"
        val label = suggestions.at(multiline, multiline.indexOf("Hi") + 1).items.first { it.label == "textSize" }
        assertEquals(
            "{\n  \"mainView\": {\n    \"type\": \"text\",\n    \"label\": \"Hi\",\n    \"textSize\": \n  }\n}",
            multiline.replaceRange(label.start, label.end, label.insert)
        )
    }

    @Test
    fun numbersAreRecognisedFromTheSchemaAndFromTypedDigits() {
        fun numberAt(textWithCursor: String): Boolean {
            val cursor = textWithCursor.indexOf('|')
            return suggestions.isNumberAt(textWithCursor.removeRange(cursor, cursor + 1), cursor)
        }
        assertTrue(numberAt("""{ "mainView": { "type": "text", "textSize": | } }"""))
        assertTrue(numberAt("""{ "mainView": { "type": "text", "textSize": 2|0 } }"""))
        assertTrue(numberAt("""{ "mainView": { "type": "image", "size": { "height": | } } }"""))
        assertTrue(!numberAt("""{ "mainView": { "type": "text", "label": "2|0" } }"""))
        assertTrue(!numberAt("""{ "mainView": { "type": "text", "label": | } }"""))
        assertTrue(!numberAt("""{ "mainView": { "type": "text", | } }"""))
        // On the next line the letters come back, for "}" or the next property.
        assertTrue(!numberAt("{ \"mainView\": { \"type\": \"text\", \"textSize\": 20\n|} }"))
        // Digits typed for a property the schema doesn't know (inside data) still get the number pad.
        assertTrue(numberAt("""{ "mainView": { "type": "text", "data": { "price": 1|9 } } }"""))
    }

    @Test
    fun formattingKeepsNumbersAndDuplicatesAndSurvivesLongStrings() {
        assertEquals(
            "{\n  \"price\": 0.10,\n  \"big\": 12345678901234567890.5\n}",
            prettyJson("{\"price\":0.10,\"big\":12345678901234567890.5}")
        )
        val duplicated = "{\"a\": 1, \"a\": 2}"
        assertEquals(duplicated, prettyJson(duplicated), "a key twice is left as it is")
        val empty = "{\"a\":[],\"b\":{}}"
        assertEquals("{\n  \"a\": [ ],\n  \"b\": { }\n}", prettyJson(empty))
        assertEquals("not json", prettyJson("not json"))
    }

    @Test
    fun anUnclosedStringIsExplainedInPlainWords() {
        val json = HELLO.replace("\"label\": \"Hello\"", "\"label\": \"Hello")
        assertEquals(
            "A text value isn't closed: add a quote (\") before the end of the line",
            checker.check(json).single().message
        )
    }

    @Test
    fun enterKeepsTheIndentationAndOpensBraces() {
        fun typeEnter(text: String): Pair<String, Int> {
            val cursor = text.indexOf('|')
            val before = text.removeRange(cursor, cursor + 1)
            return checkNotNull(Snippets.autoIndent(before, before.replaceRange(cursor, cursor, "\n"), cursor + 1))
        }
        val (plain, plainCursor) = typeEnter("{\n    \"a\": 1,|\n}")
        assertEquals("{\n    \"a\": 1,\n    \n}", plain)
        assertEquals(plain.indexOf(",\n") + 6, plainCursor)

        val (braces, bracesCursor) = typeEnter("  \"views\": [|]")
        assertEquals("  \"views\": [\n    \n  ]", braces)
        assertEquals(braces.indexOf("[\n") + 6, bracesCursor)
        assertEquals(null, Snippets.autoIndent("ab", "abc", 3))
    }

    @Test
    fun anEmptyTextIsReportedAsEmpty() {
        assertEquals(listOf(EditorIssue(0, "$", "Configuration is empty")), checker.check(""))
    }

    @Test
    fun theNewAppTemplateAndEverySnippetAreValidWhereTheyGo() {
        assertEquals(emptyList<EditorIssue>(), checker.check(Snippets.newApp))
        Snippets.all.forEach { snippet ->
            val json = if (snippet.formField) {
                HELLO.replace(
                    "{ \"type\": \"text\", \"label\": \"Hello\" }",
                    "{ \"type\": \"form\", \"formElements\": [ ${snippet.json} ] }"
                )
            } else {
                HELLO.replace("{ \"type\": \"text\", \"label\": \"Hello\" }", snippet.json)
            }
            assertEquals(emptyList<EditorIssue>(), checker.check(json), snippet.label)
        }
    }

    /** [textWithCursor] ("|" marks the cursor) after inserting [json] there, with "|" at the new cursor. */
    private fun inserted(textWithCursor: String, json: String = "{ }"): String {
        val at = textWithCursor.indexOf('|')
        val text = textWithCursor.removeRange(at, at + 1)
        val insertion = Snippets.insertion(text, at, at, json)
        val result = text.replaceRange(insertion.start, insertion.end, insertion.text)
        val cursor = insertion.start + insertion.cursor
        return result.replaceRange(cursor, cursor, "|")
    }

    @Test
    fun insertingAfterAnItemAddsTheCommaAndStartsANewLine() {
        assertEquals("[\n  { \"a\": 1 },\n  { }|\n]", inserted("[\n  { \"a\": 1 }|\n]"))
    }

    @Test
    fun insertingOnALineAfterAnItemWithoutCommaAddsIt() {
        assertEquals("[\n  { \"a\": 1 },\n  { }|\n]", inserted("[\n  { \"a\": 1 }\n  |\n]"))
    }

    @Test
    fun insertingAfterACommaOrIntoAnEmptyArrayAddsNoComma() {
        assertEquals("[\n  1,\n  { }|\n]", inserted("[\n  1,\n  |\n]"))
        assertEquals("[{ }|]", inserted("[|]"))
    }

    @Test
    fun insertingBeforeAnItemAddsTheCommaAfter() {
        assertEquals("[\n  { }|,{ \"a\": 1 }\n]", inserted("[\n  |{ \"a\": 1 }\n]"))
    }

    @Test
    fun insertingBetweenTwoItemsKeepsTheJsonValid() {
        val text = HELLO.replace(
            "{ \"type\": \"text\", \"label\": \"Hello\" }",
            "{ \"type\": \"text\", \"label\": \"Hello\" },\n    { \"type\": \"text\", \"label\": \"Bye\" }"
        )
        val at = text.indexOf("\"Hello\" }") + "\"Hello\" }".length
        Snippets.all.filterNot { it.formField }.forEach { snippet ->
            val insertion = Snippets.insertion(text, at, at, snippet.json)
            val result = text.replaceRange(insertion.start, insertion.end, insertion.text)
            assertEquals(emptyList<EditorIssue>(), checker.check(result), snippet.label)
        }
    }

    @Test
    fun theFormSnippetDoesSomethingWhenSent() {
        assertTrue(Snippets.all.single { it.label == "Form" }.json.contains("\"action\""))
    }

    private companion object {
        val HELLO = """
            {
              "schemaVersion": 3,
              "name": "Hello",
              "version": "1.0",
              "mainView": {
                "type": "layout",
                "views": [
                  { "type": "text", "label": "Hello" }
                ]
              }
            }
        """.trimIndent()
    }
}
