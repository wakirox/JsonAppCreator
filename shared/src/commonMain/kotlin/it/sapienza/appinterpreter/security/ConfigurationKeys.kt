package it.sapienza.appinterpreter.security

import it.sapienza.appinterpreter.json.JsonText
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The API keys of configurations: those the user typed (in [secrets]) and those built into the app for its bundled
 * configurations ([bundledValues], by resource name), for the configuration [currentConfigurationId] says runs.
 */
class ConfigurationKeys(
    private val secrets: SecretStore,
    private val bundledValues: Map<String, Map<String, String>>,
    private val currentConfigurationId: () -> String
) {
    fun placeholderNames(json: String): Set<String> = ApiKeyPlaceholders.names(json)

    fun hasSavedValue(json: String, name: String): Boolean =
        secrets.read(ApiKeyPlaceholders.configurationId(json), name) != null

    fun save(json: String, values: Map<String, String>) = save(ApiKeyPlaceholders.configurationId(json), json, values)

    fun saveForCurrentConfiguration(json: String, values: Map<String, String>) =
        save(currentConfigurationId(), json, values)

    /** Removes every saved API key of [configurationId] declared in [json], when a saved app is deleted. */
    fun delete(configurationId: String, json: String) {
        if (bundledName(configurationId) != null) return
        ApiKeyPlaceholders.names(json).forEach { name -> secrets.remove(configurationId, name) }
    }

    fun availableNamesForCurrentConfiguration(json: String): Set<String> {
        val configurationId = currentConfigurationId()
        val bundledConfiguration = bundledName(configurationId)?.let(bundledValues::get).orEmpty()
        return ApiKeyPlaceholders.names(json).filterTo(mutableSetOf()) { name ->
            name in bundledConfiguration || secrets.read(configurationId, name) != null
        }
    }

    /** [text] (a request URL) with the current configuration's keys in place of their placeholders, URL-encoded. */
    fun replaceForCurrentConfiguration(text: String): String = replace(text, encode = true)

    /** [text] (a request header's value) with the current configuration's keys in place of their placeholders, as they are. */
    fun replaceInHeaderForCurrentConfiguration(text: String): String = replace(text, encode = false)

    private fun replace(text: String, encode: Boolean): String {
        val names = ApiKeyPlaceholders.names(text)
        if (names.isEmpty()) return text
        val configurationId = currentConfigurationId()
        val values = buildMap {
            bundledName(configurationId)?.let { putAll(bundledValues[it].orEmpty()) }
            names.forEach { name -> secrets.read(configurationId, name)?.let { put(name, it) } }
        }.mapValues { (_, value) -> if (encode) UriEncoding.encode(value) else value }
        return ApiKeyPlaceholders.replace(text, values)
    }

    private fun save(configurationId: String, json: String, values: Map<String, String>) {
        values.forEach { (name, value) ->
            require(name in ApiKeyPlaceholders.names(json)) {
                "Cannot save an API key that is not declared by the configuration"
            }
            if (value.isNotEmpty()) secrets.write(configurationId, name, value)
        }
    }

    private fun bundledName(configurationId: String): String? =
        configurationId.removePrefix(BUNDLED_PREFIX).takeIf { configurationId.startsWith(BUNDLED_PREFIX) }

    companion object {
        private const val BUNDLED_PREFIX = "bundled:"

        /** The build's keys for bundled configurations: Base64 of {"resource_name": {"NAME": "value"}}. */
        fun decodeBundledValues(encoded: String): Map<String, Map<String, String>> {
            if (encoded.isBlank()) return emptyMap()
            val root = JsonText.parse(Base64.decode(encoded).decodeToString())!!.jsonObject
            return root.mapValues { (_, configuration) ->
                (configuration as JsonObject).mapValues { (_, value) -> value.jsonPrimitive.content }
            }
        }
    }
}

/** Percent-encoding as Android's Uri.encode: letters, digits and _-!.~'()* stay; other UTF-8 bytes become %XX. */
object UriEncoding {
    private const val ALLOWED = "_-!.~'()*"

    fun encode(text: String): String = buildString {
        text.encodeToByteArray().forEach { byte ->
            val c = (byte.toInt() and 0xff)
            val char = c.toChar()
            if (c < 0x80 && (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in ALLOWED)) {
                append(char)
            } else {
                append('%').append(HEX[c shr 4]).append(HEX[c and 0xf])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
