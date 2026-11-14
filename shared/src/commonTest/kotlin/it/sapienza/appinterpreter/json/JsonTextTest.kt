package it.sapienza.appinterpreter.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JsonTextTest {

    @Test
    fun doubleNumbersLeaveTrueAndFalseAlone() {
        val value = JsonText.parse("""{ "a": 0.10, "b": false, "c": true, "d": 3 }""")!!
        assertEquals("""{"a":0.1,"b":false,"c":true,"d":3}""", JsonText.withDoubleNumbers(value).toString())
        assertEquals(false, JsonNumbers.isDecimal("false"))
        assertEquals(true, JsonNumbers.isDecimal("-1e3"))
    }

    @Test
    fun readsValuesAndKeepsNumbersAsWritten() {
        val value = JsonText.parse(
            """{ "a": 0.10, "b": [1, -2, 3e2], "c": "x\"é\n", "d": true, "e": null }"""
        )!!.jsonObject
        assertEquals("0.10", value["a"]!!.jsonPrimitive.content)
        assertEquals(listOf("1", "-2", "3e2"), value["b"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("x\"é\n", value["c"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(true), value["d"])
        assertEquals("null", value["e"].toString())
    }

    @Test
    fun emptyTextIsNoValueAndTrailingTextIsIgnoredAsJacksonDid() {
        assertNull(JsonText.parse("  \n"))
        assertEquals(JsonObject(emptyMap()), JsonText.parse("{} trailing"))
        assertEquals("1", JsonText.parseAt("xx[1, {}]yy", 2).jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun syntaxErrorsSayWhereAndWhyInJacksonsWords() {
        fun error(json: String) = assertFailsWith<JsonSyntaxException> { JsonText.parse(json) }

        val missingColon = error("{\n  \"label\" \"Hello\"\n}")
        assertEquals(2, missingColon.line)
        assertEquals(11, missingColon.column)
        assertTrue(missingColon.reason.contains("was expecting a colon"))
        assertTrue(error("{\"a\": 1,}").reason.contains("double-quote to start field name"))
        assertTrue(error("{\"a\": 1 \"b\": 2}").reason.contains("comma to separate"))
        assertTrue(error("[1 2]").reason.contains("comma to separate"))
        assertTrue(error("{\"a\": \"open\n}").reason.contains("CTRL-CHAR, code 10"))
        assertTrue(error("{\"a\": [1, 2").reason.startsWith("Unexpected end-of-input"))
        assertTrue(error("{\"a\": hello}").reason.startsWith("Unrecognized token 'hello'"))
        assertTrue(error("{\"a\": 01}").reason.contains("Leading zeroes"))
        assertTrue(error("[1,]").reason.contains("expected a valid value"))
        assertTrue(
            assertFailsWith<JsonSyntaxException> {
                JsonText.parse("{\"a\": 1, \"a\": 2}", strictDuplicates = true)
            }.reason.contains("Duplicate")
        )
    }

    @Test
    fun aKeyTwiceKeepsTheLastValueUnlessStrict() {
        assertEquals("2", JsonText.parse("{\"a\": 1, \"a\": 2}")!!.jsonObject["a"]!!.jsonPrimitive.content)
    }

    @Test
    fun pathOffsetsPlaceKeysAndArrayItems() {
        val json = """{ "mainView": { "views": [ { "type": "text" }, 5 ] } }"""
        val offsets = JsonText.pathOffsets(json)
        assertEquals(0, offsets["$"])
        assertEquals(json.indexOf("\"mainView\""), offsets["$.mainView"])
        assertEquals(json.indexOf("{ \"type\""), offsets["$.mainView.views[0]"])
        assertEquals(json.indexOf("\"type\""), offsets["$.mainView.views[0].type"])
        assertEquals(json.indexOf("5 ]"), offsets["$.mainView.views[1]"])
        // Before a syntax error, the paths found so far.
        val broken = JsonText.pathOffsets("""{ "a": 1, "b" 2 }""")
        assertEquals(2, broken["$.a"])
        assertEquals(10, broken["$.b"])
    }

    @Test
    fun prettyPrintsLikeJacksonsDefaultPrinter() {
        assertEquals(
            "{\n  \"price\": 0.10,\n  \"big\": 12345678901234567890.5,\n  \"none\": { },\n  \"list\": [ ],\n  \"items\": [\n    1,\n    \"a/b\"\n  ]\n}",
            JsonText.pretty(
                JsonText.parse(
                    """{"price":0.10,"big":12345678901234567890.5,"none":{},"list":[],"items":[1,"a/b"]}"""
                )!!
            )
        )
        assertEquals("\"tab\\t\\u0001\"", JsonText.pretty(JsonPrimitive("tab\t\u0001")))
    }

    @Test
    fun numbersPrintAsBigDecimalAndDouble() {
        assertEquals("1E+3", JsonNumbers.exact("1e3"))
        assertEquals("1E-8", JsonNumbers.exact("0.00000001"))
        assertEquals("0.000001", JsonNumbers.exact("0.000001"))
        assertEquals("1.50", JsonNumbers.exact("1.50"))
        assertEquals("150", JsonNumbers.exact("1.50e2"))
        assertEquals("0.0", JsonNumbers.exact("-0.0"))
        assertEquals("0", JsonNumbers.exact("-0"))
        assertEquals("-12", JsonNumbers.exact("-12"))
        assertEquals("1.5", JsonNumbers.display("1.50"))
        assertEquals("1000.0", JsonNumbers.display("1e3"))
        assertEquals("42", JsonNumbers.display("42"))
        assertEquals("0.1", JsonText.withDoubleNumbers(JsonText.parse("[0.10]")!!).jsonArray[0].jsonPrimitive.content)
        assertEquals("1E+999999999999", JsonNumbers.exact("1e999999999999"))
        assertEquals("1E-999999999999", JsonNumbers.exact("1e-999999999999"))
        assertEquals("1e999999999999999999999", JsonNumbers.exact("1e999999999999999999999"))
        assertEquals("[\n  1E+999999999999\n]", JsonText.pretty(JsonText.parse("[1e999999999999]")!!))
    }
}
