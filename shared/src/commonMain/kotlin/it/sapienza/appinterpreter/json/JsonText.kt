@file:OptIn(ExperimentalSerializationApi::class)

package it.sapienza.appinterpreter.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * A syntax error in JSON text: where the reader stopped ([offset], and the 1-based [line] and [column]) and why.
 * [reason] is worded like the parser the app used before (Jackson), which the editor turns into plain words.
 */
class JsonSyntaxException(val offset: Int, val line: Int, val column: Int, val reason: String) :
    IllegalArgumentException("$reason at line $line, column $column")

/**
 * Strict JSON (RFC 8259) reading and writing for the whole app, in common code. It reads into kotlinx.serialization
 * [JsonElement]s and keeps what the app relied on Jackson for: numbers exactly as written, the character offset of
 * every property and array item, reading one value from the middle of a text, and an optional check for a key given
 * twice. Like Jackson, it reads the first value and ignores whatever follows it.
 */
object JsonText {
    /** The value in [text], or null when it is empty or only whitespace. Throws [JsonSyntaxException]. */
    fun parse(text: String, strictDuplicates: Boolean = false): JsonElement? {
        val reader = Reader(text, 0, strictDuplicates, null)
        reader.skipWhitespace()
        if (reader.atEnd()) return null
        return reader.value("$")
    }

    /** The one value that starts at [start] in [text], whatever comes after it. Throws [JsonSyntaxException]. */
    fun parseAt(text: String, start: Int): JsonElement = Reader(text, start, false, null).value("$")

    /** Whether [text] holds a JSON value. */
    fun isValid(text: String): Boolean = runCatching { parse(text) != null }.getOrDefault(false)

    /**
     * Where each JSON path starts in the text, as "$.mainView.views[1].type": a property at its key, an array element
     * at its first character. A key given twice keeps its first place. Text after a syntax error is left out.
     */
    fun pathOffsets(text: String): Map<String, Int> {
        val offsets = HashMap<String, Int>()
        try {
            val reader = Reader(text, 0, false, offsets)
            reader.skipWhitespace()
            if (!reader.atEnd()) reader.value("$")
        } catch (_: JsonSyntaxException) {
            // A partial map still places the issues before the syntax error.
        }
        if ("$" !in offsets) offsets["$"] = 0
        return offsets
    }

    /**
     * [element] indented with two spaces as Jackson's default pretty printer wrote it ("key": value, "{ }" and "[ ]"
     * when empty), numbers in the form Jackson gave a BigDecimal (0.10 stays 0.10, 1e3 becomes 1E+3).
     */
    fun pretty(element: JsonElement): String = buildString { write(element, 0) }

    /** [element] as compact JSON, as kotlinx.serialization writes it. */
    fun compact(element: JsonElement): String = element.toString()

    /** [element] with every non-integer number as a Double would print it (0.10 becomes 0.1), like Jackson's default. */
    fun withDoubleNumbers(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { withDoubleNumbers(it.value) })

        is JsonArray -> JsonArray(element.map(::withDoubleNumbers))

        is JsonPrimitive -> if (!element.isString && element !is JsonNull && JsonNumbers.isDecimal(element.content)) {
            JsonUnquotedLiteral(element.content.toDouble().toString())
        } else {
            element
        }
    }

    private fun StringBuilder.write(element: JsonElement, level: Int) {
        when (element) {
            is JsonObject -> {
                if (element.isEmpty()) {
                    append("{ }")
                    return
                }
                append('{')
                var first = true
                element.forEach { (key, value) ->
                    if (!first) append(',')
                    first = false
                    newline(level + 1)
                    quote(key)
                    append(": ")
                    write(value, level + 1)
                }
                newline(level)
                append('}')
            }

            is JsonArray -> {
                if (element.isEmpty()) {
                    append("[ ]")
                    return
                }
                append('[')
                element.forEachIndexed { index, value ->
                    if (index > 0) append(',')
                    newline(level + 1)
                    write(value, level + 1)
                }
                newline(level)
                append(']')
            }

            is JsonNull -> append("null")

            is JsonPrimitive -> when {
                element.isString -> quote(element.content)
                element.content == "true" || element.content == "false" -> append(element.content)
                else -> append(JsonNumbers.exact(element.content))
            }
        }
    }

    private fun StringBuilder.newline(level: Int) {
        append('\n')
        repeat(level) { append("  ") }
    }

    /** A JSON string as Jackson writes it: short escapes where JSON has them, \u00XX for other control characters. */
    internal fun StringBuilder.quote(value: String) {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")

                '\\' -> append("\\\\")

                '\n' -> append("\\n")

                '\r' -> append("\\r")

                '\t' -> append("\\t")

                '\b' -> append("\\b")

                '\u000C' -> append("\\f")

                else -> if (c.code < 0x20) {
                    append("\\u00")
                    append(HEX[c.code shr 4])
                    append(HEX[c.code and 0xF])
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    private const val HEX = "0123456789ABCDEF"

    private const val MAX_DEPTH = 1000

    private class Reader(
        val text: String,
        var pos: Int,
        val strictDuplicates: Boolean,
        val offsets: MutableMap<String, Int>?
    ) {
        private var depth = 0

        fun atEnd() = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length &&
                (text[pos] == ' ' || text[pos] == '\n' || text[pos] == '\r' || text[pos] == '\t')
            ) {
                pos++
            }
        }

        fun value(path: String): JsonElement {
            skipWhitespace()
            if (atEnd()) endOfInput("expected a value")
            val c = text[pos]
            return when {
                c == '{' -> objectAt(path)

                c == '[' -> arrayAt(path)

                c == '"' -> JsonPrimitive(string())

                c == '-' || c in '0'..'9' -> number()

                isWordChar(c) -> word()

                else -> fail(
                    pos,
                    "Unexpected character (${describe(
                        c
                    )}): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')"
                )
            }
        }

        private fun enter() {
            if (++depth > MAX_DEPTH) fail(pos, "Document nesting depth exceeds the maximum allowed ($MAX_DEPTH)")
        }

        private fun objectAt(path: String): JsonObject {
            enter()
            pos++
            val members = LinkedHashMap<String, JsonElement>()
            skipWhitespace()
            if (!atEnd() && text[pos] == '}') {
                pos++
                depth--
                return JsonObject(members)
            }
            while (true) {
                skipWhitespace()
                if (atEnd()) endOfInput("expected a property name")
                if (text[pos] != '"') {
                    fail(
                        pos,
                        "Unexpected character (${describe(text[pos])}): was expecting double-quote to start field name"
                    )
                }
                val keyStart = pos
                val key = string()
                val keyPath = "$path.$key"
                offsets?.let { if (keyPath !in it) it[keyPath] = keyStart }
                skipWhitespace()
                if (atEnd()) endOfInput("expected a colon")
                if (text[pos] != ':') {
                    fail(
                        pos,
                        "Unexpected character (${describe(
                            text[pos]
                        )}): was expecting a colon to separate field name and value"
                    )
                }
                pos++
                val member = value(keyPath)
                if (strictDuplicates && key in members) fail(keyStart, "Duplicate field '$key'")
                members[key] = member
                skipWhitespace()
                if (atEnd()) endOfInput("expected close marker for Object")
                when (text[pos]) {
                    ',' -> pos++

                    '}' -> {
                        pos++
                        depth--
                        return JsonObject(members)
                    }

                    else -> fail(
                        pos,
                        "Unexpected character (${describe(text[pos])}): was expecting comma to separate Object entries"
                    )
                }
            }
        }

        private fun arrayAt(path: String): JsonArray {
            enter()
            pos++
            val items = ArrayList<JsonElement>()
            skipWhitespace()
            if (!atEnd() && text[pos] == ']') {
                pos++
                depth--
                return JsonArray(items)
            }
            while (true) {
                skipWhitespace()
                val itemPath = "$path[${items.size}]"
                if (!atEnd()) offsets?.let { if (itemPath !in it) it[itemPath] = pos }
                items += value(itemPath)
                skipWhitespace()
                if (atEnd()) endOfInput("expected close marker for Array")
                when (text[pos]) {
                    ',' -> pos++

                    ']' -> {
                        pos++
                        depth--
                        return JsonArray(items)
                    }

                    else -> fail(
                        pos,
                        "Unexpected character (${describe(text[pos])}): was expecting comma to separate Array entries"
                    )
                }
            }
        }

        private fun string(): String {
            pos++ // the opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) endOfInput("was expecting closing quote for a string value")
                val c = text[pos]
                when {
                    c == '"' -> {
                        pos++
                        return out.toString()
                    }

                    c == '\\' -> {
                        if (pos + 1 >= text.length) endOfInput("in character escape sequence")
                        val escaped = text[pos + 1]
                        pos += 2
                        when (escaped) {
                            '"' -> out.append('"')

                            '\\' -> out.append('\\')

                            '/' -> out.append('/')

                            'b' -> out.append('\b')

                            'f' -> out.append('\u000C')

                            'n' -> out.append('\n')

                            'r' -> out.append('\r')

                            't' -> out.append('\t')

                            'u' -> {
                                if (pos + 4 > text.length) endOfInput("in character escape sequence")
                                val code = text.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: fail(
                                        pos,
                                        "Unexpected character in a \\u escape: expected a hex-digit for character escape sequence"
                                    )
                                out.append(code.toChar())
                                pos += 4
                            }

                            else -> fail(pos - 1, "Unrecognized character escape ${describe(escaped)}")
                        }
                    }

                    c.code < 0x20 -> fail(
                        pos,
                        "Illegal unquoted character ((CTRL-CHAR, code ${c.code})): has to be escaped using backslash to be included in string value"
                    )

                    else -> {
                        out.append(c)
                        pos++
                    }
                }
            }
        }

        private fun number(): JsonPrimitive {
            val start = pos
            if (text[pos] == '-') pos++
            if (atEnd()) endOfInput("in a number: expected a digit")
            when {
                text[pos] == '0' -> {
                    pos++
                    if (!atEnd() &&
                        text[pos] in '0'..'9'
                    ) {
                        fail(start, "Invalid numeric value: Leading zeroes not allowed")
                    }
                }

                text[pos] in '1'..'9' -> digits()

                else -> fail(
                    pos,
                    "Unexpected character (${describe(
                        text[pos]
                    )}) in numeric value: expected digit (0-9) to follow minus sign, for valid numeric value"
                )
            }
            if (!atEnd() && text[pos] == '.') {
                pos++
                if (atEnd() || text[pos] !in '0'..'9') {
                    fail(pos, "Unexpected character in numeric value: Decimal point not followed by a digit")
                }
                digits()
            }
            if (!atEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (!atEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
                if (atEnd() ||
                    text[pos] !in '0'..'9'
                ) {
                    fail(pos, "Unexpected character in numeric value: Exponent indicator not followed by a digit")
                }
                digits()
            }
            if (!atEnd() && isWordChar(text[pos])) {
                val end = wordEnd(pos)
                fail(
                    start,
                    "Unrecognized token '${text.substring(
                        start,
                        end
                    )}': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')"
                )
            }
            return JsonUnquotedLiteral(text.substring(start, pos))
        }

        private fun digits() {
            while (!atEnd() && text[pos] in '0'..'9') pos++
        }

        private fun word(): JsonPrimitive {
            val start = pos
            val end = wordEnd(pos)
            pos = end
            return when (text.substring(start, end)) {
                "true" -> JsonPrimitive(true)

                "false" -> JsonPrimitive(false)

                "null" -> JsonNull

                else -> fail(
                    start,
                    "Unrecognized token '${text.substring(
                        start,
                        end
                    )}': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')"
                )
            }
        }

        private fun wordEnd(from: Int): Int {
            var end = from
            while (end < text.length && isWordChar(text[end])) end++
            return end
        }

        private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

        private fun describe(c: Char) = if (c.code < 0x20) "CTRL-CHAR, code ${c.code}" else "'$c' (code ${c.code})"

        private fun endOfInput(what: String): Nothing = fail(text.length, "Unexpected end-of-input: $what")

        fun fail(offset: Int, reason: String): Nothing {
            val at = offset.coerceIn(0, text.length)
            val lineStart = text.lastIndexOf('\n', at - 1) + 1
            val line = (0 until at).count { text[it] == '\n' } + 1
            throw JsonSyntaxException(at, line, at - lineStart + 1, reason)
        }
    }
}

/** How the app prints JSON numbers, which are kept as written when read. */
object JsonNumbers {
    /** Whether [literal] has a fraction or an exponent. */
    fun isDecimal(literal: String) = (literal.firstOrNull()?.let { it == '-' || it.isDigit() } == true) &&
        literal.any { it == '.' || it == 'e' || it == 'E' }

    /**
     * [literal] as the old Android code showed a number read from JSON: whole numbers as they are (as a long when they
     * fit), others as a Double prints them (1.50 is 1.5).
     */
    fun display(literal: String): String = if (isDecimal(literal)) {
        literal.toDouble().toString()
    } else {
        literal.toLongOrNull()?.toString() ?: literal.toDouble().toString()
    }

    /** [literal] as Java's BigDecimal.toString() and BigInteger print it, which is how Jackson wrote exact numbers. */
    fun exact(literal: String): String {
        if (!isDecimal(literal)) {
            val negative = literal.startsWith('-')
            val digits = literal.removePrefix("-").trimStart('0').ifEmpty { "0" }
            return if (negative && digits != "0") "-$digits" else digits
        }
        val negative = literal.startsWith('-')
        val body = literal.removePrefix("-")
        val mantissa = body.substringBefore('e').substringBefore('E')
        val exponent = if (mantissa.length <
            body.length
        ) {
            body.substring(mantissa.length + 1).removePrefix("+").toLongOrNull() ?: return literal
        } else {
            0L
        }
        val whole = mantissa.substringBefore('.')
        val fraction = if ('.' in mantissa) mantissa.substringAfter('.') else ""
        val unscaled = (whole + fraction).trimStart('0').ifEmpty { "0" }
        val scale = fraction.length.toLong() - exponent
        val sign = if (negative && unscaled != "0") "-" else ""
        if (scale == 0L) return sign + unscaled
        val adjusted = -scale + (unscaled.length - 1)
        if (scale > 0L && adjusted >= -6L) {
            val intScale = scale.toInt()
            val text = if (unscaled.length > intScale) {
                unscaled.substring(0, unscaled.length - intScale) + "." +
                    unscaled.substring(unscaled.length - intScale)
            } else {
                "0." + "0".repeat(intScale - unscaled.length) + unscaled
            }
            return sign + text
        }
        val scientific = StringBuilder(sign).append(unscaled[0])
        if (unscaled.length > 1) scientific.append('.').append(unscaled, 1, unscaled.length)
        if (adjusted != 0L) {
            scientific.append('E')
            if (adjusted > 0) scientific.append('+')
            scientific.append(adjusted)
        }
        return scientific.toString()
    }
}
