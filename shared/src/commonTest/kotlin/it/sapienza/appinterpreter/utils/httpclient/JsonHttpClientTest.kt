package it.sapienza.appinterpreter.utils.httpclient

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import it.sapienza.appinterpreter.model.event.CallServiceMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class JsonHttpClientTest {
    private val requests = mutableListOf<String>()
    private val headers = mutableListOf<Map<String, String>>()

    private fun client(status: HttpStatusCode = HttpStatusCode.OK, body: String = """{"ok":true}""") = JsonHttpClient(
        MockEngine { request ->
            requests +=
                "${request.method.value} ${request.url} ${request.headers[HttpHeaders.UserAgent]} ${request.body.toByteArray().decodeToString()}"
            headers += request.headers.names().associateWith { request.headers[it].orEmpty() }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
        JsonHttpClient.userAgent("1.2", "Test"),
        resolveUrl = { it.replace("__KEY__", "secret") },
        resolveHeader = { it.replace("__KEY__", "a b+c") }
    )

    @Test
    fun getsWithTheAppsUserAgentAndKeys() = runTest {
        val answer = client().request("https://example.com/a?k=__KEY__", post = false, body = null)
        assertEquals("true", answer["ok"]?.jsonPrimitive?.content)
        assertEquals(
            "GET https://example.com/a?k=secret Appkin/1.2 (Test; https://github.com/wakirox/JsonAppCreator) ",
            requests.single()
        )
    }

    @Test
    fun postsTheDataAsJson() = runTest {
        client().request("https://example.com/p", post = true, body = JsonObject(mapOf("name" to JsonPrimitive("Ana"))))
        assertEquals("""{"name":"Ana"}""", requests.single().substringAfterLast(' '))
        client().request("https://example.com/p", post = true, body = null)
        assertEquals("null", requests.last().substringAfterLast(' '))
    }

    @Test
    fun failuresSayWhatWentWrong() = runTest {
        assertEquals(
            "Request failed with 503",
            assertFailsWith<IOException> {
                client(HttpStatusCode.ServiceUnavailable).request("https://e.com", false, null)
            }.message
        )
        assertEquals(
            "The response is not a JSON object",
            assertFailsWith<IOException> {
                client(body = "[1]").request("https://e.com", false, null)
            }.message
        )
        assertFailsWith<IOException> { client(body = "<html>").request("https://e.com", false, null) }
        assertEquals(
            "Missing configuration API key: OTHER_KEY",
            assertFailsWith<IllegalStateException> {
                client().request("https://e.com?k=__OTHER_KEY__", false, null)
            }.message
        )
        assertEquals(3, requests.size, "no request without its key")
    }

    @Test
    fun putSendsTheDataAndDeleteDoesNot() = runTest {
        val data = JsonObject(mapOf("name" to JsonPrimitive("Ana")))
        client().request("https://example.com/p/1", CallServiceMethod.put, data)
        assertEquals("""PUT https://example.com/p/1""", requests.last().substringBefore(" Appkin"))
        assertEquals("""{"name":"Ana"}""", requests.last().substringAfterLast(' '))
        client().request("https://example.com/p/1", CallServiceMethod.delete, data)
        assertEquals("DELETE https://example.com/p/1", requests.last().substringBefore(" Appkin"))
        assertEquals("", requests.last().substringAfterLast(' '))
    }

    @Test
    fun headersAreSentWithTheirKeysAsTheyAre() = runTest {
        client().request(
            "https://example.com/a",
            CallServiceMethod.get,
            null,
            mapOf(
                "Authorization" to "Bearer __KEY__",
                "Accept-Language" to "it"
            )
        )
        assertEquals("Bearer a b+c", headers.last()["Authorization"], "a header's key isn't URL-encoded")
        assertEquals("it", headers.last()["Accept-Language"])
        assertEquals(
            "Missing configuration API key: OTHER",
            assertFailsWith<IllegalStateException> {
                client().request("https://e.com", CallServiceMethod.get, null, mapOf("X-Key" to "__OTHER__"))
            }.message
        )
        listOf("User-Agent", "host", "Content-Type").forEach { name ->
            assertFailsWith<IllegalArgumentException> {
                client().request("https://e.com", CallServiceMethod.get, null, mapOf(name to "x"))
            }
        }
        assertEquals(1, requests.size)
    }

    @Test
    fun anEmptyAnswerIsAnEmptyObjectAndAStatusIsKept() = runTest {
        assertEquals(
            JsonObject(emptyMap()),
            client(HttpStatusCode.NoContent, body = "").request("https://e.com/1", CallServiceMethod.delete, null)
        )
        assertEquals(
            404,
            assertFailsWith<HttpStatusException> {
                client(HttpStatusCode.NotFound).request("https://e.com", false, null)
            }.status
        )
    }
}
