package it.sapienza.appinterpreter.utils.httpclient

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.readBuffer
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.model.event.CallServiceMethod
import it.sapienza.appinterpreter.security.ApiKeyPlaceholders
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonObject

/**
 * The requests of a JSON app's REST events and lists, on Ktor. [userAgent] names the app in every request, as sites
 * such as Wikimedia require (they refuse a library's default one); [resolveUrl] puts the configuration's API keys in
 * the URL.
 */
class JsonHttpClient(
    engine: HttpClientEngine,
    userAgent: String,
    private val resolveUrl: (String) -> String = { it },
    private val resolveHeader: (String) -> String = { it },
    private val maxResponseBytes: Long = MAX_RESPONSE_BYTES
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        followRedirects = false
        install(UserAgent) { agent = userAgent }
    }

    /**
     * Requests [url], by POST with [body] when [post], and returns the JSON object it answers. Cancelling the coroutine
     * cancels the request. A failure is an exception whose message says what went wrong, such as a missing API key.
     */
    suspend fun request(url: String, post: Boolean, body: JsonObject?): JsonObject =
        request(url, if (post) CallServiceMethod.post else CallServiceMethod.get, body)

    /**
     * Requests [url] with [method] and [headers]; POST and PUT send [body]. An empty answer (as a DELETE often gives)
     * is an empty object. A status that isn't a success fails with [HttpStatusException].
     */
    suspend fun request(
        url: String,
        method: CallServiceMethod,
        body: JsonObject?,
        headers: Map<String, String> = emptyMap()
    ): JsonObject {
        val resolved = resolveUrl(url)
        val resolvedHeaders = headers.mapValues { (_, value) -> resolveHeader(value) }
        val missing = ApiKeyPlaceholders.names(resolved) + resolvedHeaders.values.flatMap(ApiKeyPlaceholders::names)
        check(missing.isEmpty()) { "Missing configuration API key: ${missing.distinct().joinToString()}" }
        resolvedHeaders.keys.firstOrNull { it.lowercase() in RESERVED_HEADERS }?.let { name ->
            throw IllegalArgumentException("Appkin sets the $name header itself")
        }
        val initialOrigin = runCatching { Url(resolved) }.getOrNull()
        var currentUrl = resolved
        var currentMethod = method
        var currentBody = body
        var activeHeaders = resolvedHeaders
        var redirects = 0

        while (true) {
            val response = client.request(currentUrl) {
                this.method = when (currentMethod) {
                    CallServiceMethod.get -> HttpMethod.Get
                    CallServiceMethod.post -> HttpMethod.Post
                    CallServiceMethod.put -> HttpMethod.Put
                    CallServiceMethod.delete -> HttpMethod.Delete
                }
                activeHeaders.forEach { (name, value) -> header(name, value) }
                // As before, a POST without data sends the text null.
                if (currentMethod.sendsBody) setBody(TextContent(currentBody.toString(), JSON))
            }
            val code = response.status.value
            val location = response.headers[HttpHeaders.Location]
            if (code in REDIRECT_STATUSES && location != null && redirects < MAX_REDIRECTS) {
                val base = runCatching { Url(currentUrl) }.getOrNull() ?: throw HttpStatusException(code)
                val target = runCatching { URLBuilder(base).takeFrom(location).build() }.getOrNull()
                    ?: throw HttpStatusException(code)
                if (target.protocol.name !in ALLOWED_PROTOCOLS) {
                    throw IOException("Unsupported redirect scheme: ${target.protocol.name}")
                }
                if (initialOrigin?.protocol?.name == "https" && target.protocol.name != "https") {
                    throw IOException("Redirect from HTTPS to HTTP is not allowed")
                }
                currentUrl = target.toString()
                if (code == 303 || ((code == 301 || code == 302) && currentMethod == CallServiceMethod.post)) {
                    currentMethod = CallServiceMethod.get
                    currentBody = null
                }
                // Never forward caller/API-key headers to a different origin on redirect.
                activeHeaders = if (initialOrigin != null && sameOrigin(initialOrigin, target)) {
                    resolvedHeaders
                } else {
                    emptyMap()
                }
                redirects++
                continue
            }
            if (!response.status.isSuccess()) throw HttpStatusException(code)
            val bytes = response.bodyAsChannel().readBuffer(maxResponseBytes + 1).readByteArray()
            if (bytes.size > maxResponseBytes) {
                throw IOException("The response exceeds the maximum supported size")
            }
            val text = bytes.decodeToString()
            if (text.isBlank()) return JsonObject(emptyMap())
            return try {
                JsonText.parse(text) as? JsonObject ?: throw IOException("The response is not a JSON object")
            } catch (error: IllegalArgumentException) {
                throw IOException(error.message ?: "Unable to read the response")
            }
        }
    }

    fun close() = client.close()

    companion object {
        val JSON = ContentType.parse("application/json; charset=utf-8")
        const val MAX_RESPONSE_BYTES = 10L * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_PROTOCOLS = setOf("http", "https")

        internal fun sameOrigin(a: Url, b: Url): Boolean =
            a.protocol == b.protocol && a.host.equals(b.host, ignoreCase = true) && a.port == b.port

        /** Headers a configuration can't set: Appkin names itself, and the request's size and host are its own. */
        private val RESERVED_HEADERS =
            setOf("user-agent", "host", "content-length", "content-type", "transfer-encoding")

        /** "Appkin/1.2 (Android; https://...)": the app, its version and platform, and where it comes from. */
        fun userAgent(versionName: String, platform: String) =
            "Appkin/$versionName ($platform; https://github.com/wakirox/JsonAppCreator)"
    }
}

/** The server answered with [status], which isn't a success. */
class HttpStatusException(val status: Int) : IOException("Request failed with $status")
