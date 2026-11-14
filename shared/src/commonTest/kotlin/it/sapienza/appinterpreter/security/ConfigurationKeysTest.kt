package it.sapienza.appinterpreter.security

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigurationKeysTest {
    private val json = """{ "url": "https://api.example.com?key=__SEARCH_KEY__&other=__OTHER_KEY__" }"""
    private var current = "bundled:v3_the_movie_db"
    private val secrets = MemorySecretStore()
    private val keys =
        ConfigurationKeys(secrets, mapOf("v3_the_movie_db" to mapOf("SEARCH_KEY" to "built in"))) { current }

    @Test
    fun bundledAndTypedKeysFillTheUrlEncoded() {
        assertEquals(setOf("SEARCH_KEY"), keys.availableNamesForCurrentConfiguration(json))
        keys.saveForCurrentConfiguration(json, mapOf("OTHER_KEY" to "a&b/ü", "SEARCH_KEY" to ""))
        assertEquals(setOf("SEARCH_KEY", "OTHER_KEY"), keys.availableNamesForCurrentConfiguration(json))
        assertEquals(
            "https://x?key=built%20in&other=a%26b%2F%C3%BC&none=__NONE__",
            keys.replaceForCurrentConfiguration("https://x?key=__SEARCH_KEY__&other=__OTHER_KEY__&none=__NONE__")
        )
        assertEquals("no placeholders", keys.replaceForCurrentConfiguration("no placeholders"))
    }

    @Test
    fun keysAreKeptPerConfiguration() {
        keys.save(json, mapOf("OTHER_KEY" to "file key"))
        assertTrue(keys.hasSavedValue(json, "OTHER_KEY"))
        current = "file:another"
        assertEquals(emptySet(), keys.availableNamesForCurrentConfiguration(json))
        assertFailsWith<IllegalArgumentException> { keys.save(json, mapOf("UNDECLARED" to "x")) }
    }

    @Test
    fun decodesBuildValuesScopedByBundledConfiguration() {
        val encoded = Base64.encode(
            (
                """{"v3_the_movie_db":{"TMDB_API_KEY":"test-only-value"},""" +
                    """"other":{"SERVICE_TOKEN":"second"}}"""
                ).encodeToByteArray()
        )
        val values = ConfigurationKeys.decodeBundledValues(encoded)
        assertEquals("test-only-value", values["v3_the_movie_db"]?.get("TMDB_API_KEY"))
        assertEquals("second", values["other"]?.get("SERVICE_TOKEN"))
        assertEquals(emptyMap(), ConfigurationKeys.decodeBundledValues(""))
    }

    @Test
    fun deletingConfigurationRemovesOnlyItsSavedSecrets() {
        current = "file:app-one"
        keys.saveForCurrentConfiguration(json, mapOf("SEARCH_KEY" to "one-search", "OTHER_KEY" to "one-other"))
        current = "file:app-two"
        keys.saveForCurrentConfiguration(json, mapOf("SEARCH_KEY" to "two-search"))

        keys.delete("file:app-one", json)

        current = "file:app-one"
        assertEquals(emptySet(), keys.availableNamesForCurrentConfiguration(json))
        current = "file:app-two"
        assertEquals(setOf("SEARCH_KEY"), keys.availableNamesForCurrentConfiguration(json))
        current = "bundled:v3_the_movie_db"
        assertEquals(setOf("SEARCH_KEY"), keys.availableNamesForCurrentConfiguration(json))
    }
}
