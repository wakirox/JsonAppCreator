package it.sapienza.appinterpreter.security

import it.sapienza.appinterpreter.storage.MemoryAppStore
import it.sapienza.appinterpreter.utils.AppLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Deleting or replacing a saved app frees the API keys nothing needs any more. */
class ReleaseSavedAppKeysUseCaseTest {
    private val secrets = MemorySecretStore()
    private val keys = ConfigurationKeys(secrets, emptyMap()) { "unused" }
    private val library = AppLibrary(MemoryAppStore(), onRemoved = ReleaseSavedAppKeysUseCase(keys)::invoke)

    private fun app(name: String, vararg placeholders: String): String {
        val query = placeholders.joinToString("&") { "k=__${it}__" }
        return """{ "schemaVersion": 3, "name": "$name", "url": "https://x?$query" }"""
    }

    private fun store(configurationId: String, vararg names: String) =
        names.forEach { secrets.write(configurationId, it, "secret-$it") }

    @Test
    fun deletingAnAppRemovesItsKeys() {
        library.save(app("Books", "A", "B"), "file:books")
        store("file:books", "A", "B")
        store("file:other", "A")

        library.delete(AppLibrary.idOf("Books"))

        assertNull(secrets.read("file:books", "A"))
        assertNull(secrets.read("file:books", "B"))
        assertEquals("secret-A", secrets.read("file:other", "A"))
    }

    @Test
    fun keysStayWhileAnotherSavedAppSharesTheConfigurationId() {
        library.save(app("Books", "A"), "file:shared")
        library.save(app("Notes", "A"), "file:shared")
        store("file:shared", "A")

        library.delete(AppLibrary.idOf("Books"))
        assertEquals("secret-A", secrets.read("file:shared", "A"))

        library.delete(AppLibrary.idOf("Notes"))
        assertNull(secrets.read("file:shared", "A"))
    }

    @Test
    fun anEditThatRemovesAPlaceholderRemovesItsKeyOnSave() {
        library.save(app("Books", "A", "B"), "file:books")
        store("file:books", "A", "B")

        library.save(app("Books", "A"), "file:books")

        assertEquals("secret-A", secrets.read("file:books", "A"))
        assertNull(secrets.read("file:books", "B"))

        // The key is gone for good, so deleting the app later has nothing left to miss.
        library.delete(AppLibrary.idOf("Books"))
        assertNull(secrets.read("file:books", "A"))
    }

    @Test
    fun resavingTheSameAppKeepsItsKeys() {
        library.save(app("Books", "A"), "file:books")
        store("file:books", "A")

        library.save(app("Books", "A"), "file:books")

        assertEquals("secret-A", secrets.read("file:books", "A"))
    }

    @Test
    fun replacingASameNamedAppUnderAnotherConfigurationRemovesTheOldKeys() {
        library.save(app("Books", "A"), "file:old")
        store("file:old", "A")

        library.save(app("books", "A"), "file:new")
        assertNull(secrets.read("file:old", "A"))
    }

    @Test
    fun replacingKeepsTheOldKeysWhileAnotherAppSharesThem() {
        library.save(app("Books", "A"), "file:shared")
        library.save(app("Notes", "A"), "file:shared")
        store("file:shared", "A")

        library.save(app("Books", "A"), "file:new")

        assertEquals("secret-A", secrets.read("file:shared", "A"))
    }

    @Test
    fun bundledConfigurationsAreLeftAlone() {
        library.save(app("Movies", "A"), "bundled:v3_movies")
        store("bundled:v3_movies", "A")

        library.delete(AppLibrary.idOf("Movies"))

        assertEquals("secret-A", secrets.read("bundled:v3_movies", "A"))
    }
}
