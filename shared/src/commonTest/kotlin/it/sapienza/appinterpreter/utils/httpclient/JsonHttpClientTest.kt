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
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    @Test
    fun redirectsKeepHeadersOnSameOriginAndStripThemCrossOrigin() = runTest {
        val redirectClient = JsonHttpClient(
            MockEngine { request ->
                requests += "${request.method.value} ${request.url}"
                headers += request.headers.names().associateWith { request.headers[it].orEmpty() }
                when (request.url.toString()) {
                    "https://example.com/same" -> respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "/target")
                    )

                    "https://example.com/cross" -> respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "https://other.example.org/target")
                    )

                    "https://example.com/downgrade" -> respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "http://example.com/target")
                    )

                    else -> respond(
                        """{"ok":true}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            },
            JsonHttpClient.userAgent("1.2", "Test")
        )

        redirectClient.request(
            "https://example.com/same",
            CallServiceMethod.get,
            null,
            mapOf("Authorization" to "Bearer secret")
        )
        assertEquals("GET https://example.com/target", requests.last())
        assertEquals("Bearer secret", headers.last()["Authorization"])

        redirectClient.request(
            "https://example.com/cross",
            CallServiceMethod.get,
            null,
            mapOf("Authorization" to "Bearer secret")
        )
        assertEquals("GET https://other.example.org/target", requests.last())
        assertNull(headers.last()["Authorization"])

        val downgradeError = assertFailsWith<IOException> {
            redirectClient.request("https://example.com/downgrade", CallServiceMethod.get, null)
        }
        assertTrue(downgradeError.message.orEmpty().contains("HTTPS to HTTP"))
    }

    @Test
    fun responsesLargerThanCapFailCleanly() = runTest {
        val cappedClient = JsonHttpClient(
            MockEngine {
                respond(
                    """{"payload":"0123456789abcdef"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            JsonHttpClient.userAgent("1.2", "Test"),
            maxResponseBytes = 10
        )

        val error = assertFailsWith<IOException> {
            cappedClient.request("https://example.com/big", false, null)
        }
        assertTrue(error.message.orEmpty().contains("exceeds the maximum supported size"))
    }

    private fun redirecting(routes: Map<String, Pair<HttpStatusCode, String>>) = JsonHttpClient(
        MockEngine { request ->
            requests += "${request.method.value} ${request.url} ${request.body.toByteArray().decodeToString()}"
            headers += request.headers.names().associateWith { request.headers[it].orEmpty() }
            val route = routes[request.url.toString()]
            if (route == null) {
                respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("", route.first, headersOf(HttpHeaders.Location, route.second))
            }
        },
        JsonHttpClient.userAgent("1.2", "Test")
    )

    private val data = JsonObject(mapOf("a" to JsonPrimitive("b")))

    @Test
    fun aBodyIsNeverResentToAnotherOrigin() = runTest {
        listOf(HttpStatusCode.TemporaryRedirect, HttpStatusCode.PermanentRedirect).forEach { status ->
            requests.clear()
            val http = redirecting(mapOf("https://example.com/p" to (status to "https://other.example.org/p")))
            val error = assertFailsWith<IOException> {
                http.request("https://example.com/p", CallServiceMethod.post, data)
            }
            assertTrue(error.message.orEmpty().contains("another origin"))
            assertEquals(1, requests.size, "the body went nowhere else")
        }
        val put = redirecting(mapOf("https://example.com/p" to (HttpStatusCode.Found to "https://other.example.org/p")))
        assertFailsWith<IOException> { put.request("https://example.com/p", CallServiceMethod.put, data) }
        // On the same origin the body is resent.
        val same = redirecting(mapOf("https://example.com/p" to (HttpStatusCode.TemporaryRedirect to "/q")))
        same.request("https://example.com/p", CallServiceMethod.post, data)
        assertEquals("""POST https://example.com/q {"a":"b"}""", requests.last())
    }

    @Test
    fun aRewrittenRequestMayLeaveTheOriginWithoutItsBody() = runTest {
        val http =
            redirecting(mapOf("https://example.com/p" to (HttpStatusCode.SeeOther to "https://other.example.org/g")))
        http.request("https://example.com/p", CallServiceMethod.post, data)
        assertEquals("GET https://other.example.org/g ", requests.last())
    }

    @Test
    fun postBecomesGetOn301302And303ButKeepsItsMethodOn307() = runTest {
        listOf(HttpStatusCode.MovedPermanently, HttpStatusCode.Found, HttpStatusCode.SeeOther).forEach { status ->
            val http = redirecting(mapOf("https://example.com/p" to (status to "/q")))
            http.request("https://example.com/p", CallServiceMethod.post, data)
            assertEquals("GET https://example.com/q ", requests.last())
        }
        val http = redirecting(mapOf("https://example.com/p" to (HttpStatusCode.TemporaryRedirect to "/q")))
        http.request("https://example.com/p", CallServiceMethod.post, data)
        assertEquals("""POST https://example.com/q {"a":"b"}""", requests.last())
        val put = redirecting(mapOf("https://example.com/p" to (HttpStatusCode.Found to "/q")))
        put.request("https://example.com/p", CallServiceMethod.put, data)
        assertEquals("""PUT https://example.com/q {"a":"b"}""", requests.last())
    }

    @Test
    fun headersStayStrippedAfterLeavingTheInitialOrigin() = runTest {
        val http = redirecting(
            mapOf(
                "https://example.com/a" to (HttpStatusCode.Found to "https://other.example.org/b"),
                "https://other.example.org/b" to (HttpStatusCode.Found to "https://example.com/c")
            )
        )
        http.request("https://example.com/a", CallServiceMethod.get, null, mapOf("Authorization" to "Bearer secret"))
        assertEquals(
            listOf("GET https://example.com/a ", "GET https://other.example.org/b ", "GET https://example.com/c "),
            requests
        )
        assertEquals("Bearer secret", headers.first()["Authorization"])
        assertNull(headers.last()["Authorization"])
    }

    @Test
    fun redirectsStopAtTheHopCap() = runTest {
        val http = JsonHttpClient(
            MockEngine { request ->
                requests += request.url.toString()
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/next${requests.size}"))
            },
            JsonHttpClient.userAgent("1.2", "Test")
        )
        val error = assertFailsWith<HttpStatusException> { http.request("https://example.com/a", false, null) }
        assertEquals(302, error.status)
        assertEquals(6, requests.size, "the first request and five redirects")
    }

    @Test
    fun aRedirectsQueryReplacesTheCurrentOne() = runTest {
        val http =
            redirecting(mapOf("https://example.com/a?q=1" to (HttpStatusCode.Found to "https://example.com/b?q=2")))
        http.request("https://example.com/a?q=1", CallServiceMethod.get, null)
        assertEquals("GET https://example.com/b?q=2 ", requests.last())
    }

    private fun charsetClient(bytes: ByteArray, contentType: String?) = JsonHttpClient(
        MockEngine {
            respond(
                bytes,
                HttpStatusCode.OK,
                if (contentType == null) headersOf() else headersOf(HttpHeaders.ContentType, contentType)
            )
        },
        JsonHttpClient.userAgent("1.2", "Test")
    )

    @Test
    fun theBodyIsReadInTheResponsesCharset() = runTest {
        val latin1 = "{\"name\":\"caf\u00E9\"}".map { it.code.toByte() }.toByteArray()
        assertEquals(
            "caf\u00E9",
            charsetClient(latin1, "application/json; charset=ISO-8859-1").request("https://e.com", false, null)["name"]
                ?.jsonPrimitive?.content
        )
        val utf8 = "{\"name\":\"caf\u00E9\"}".encodeToByteArray()
        assertEquals(
            "caf\u00E9",
            charsetClient(
                utf8,
                "application/json"
            ).request("https://e.com", false, null)["name"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun aUtf8ByteOrderMarkIsDropped() = runTest {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + """{"ok":true}""".encodeToByteArray()
        assertEquals(
            "true",
            charsetClient(bom, "application/json; charset=utf-8").request("https://e.com", false, null)["ok"]
                ?.jsonPrimitive?.content
        )
        assertEquals(
            "true",
            charsetClient(bom, null).request("https://e.com", false, null)["ok"]?.jsonPrimitive?.content
        )
    }
}
