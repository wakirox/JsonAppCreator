package it.sapienza.appinterpreter.utils.httpclient

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.model.event.CallServiceMethod
import it.sapienza.appinterpreter.security.ApiKeyPlaceholders
import kotlinx.io.IOException
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
    private val resolveHeader: (String) -> String = { it }
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(UserAgent) { agent = userAgent }
        // Like OkHttp did on its own: follow redirects for POST too.
        install(HttpRedirect) { checkHttpMethod = false }
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
        val response = client.request(resolved) {
            this.method = when (method) {
                CallServiceMethod.get -> HttpMethod.Get
                CallServiceMethod.post -> HttpMethod.Post
                CallServiceMethod.put -> HttpMethod.Put
                CallServiceMethod.delete -> HttpMethod.Delete
            }
            resolvedHeaders.forEach { (name, value) -> header(name, value) }
            // As before, a POST without data sends the text null.
            if (method.sendsBody) setBody(TextContent(body.toString(), JSON))
        }
        if (!response.status.isSuccess()) throw HttpStatusException(response.status.value)
        val text = response.bodyAsText()
        if (text.isBlank()) return JsonObject(emptyMap())
        return try {
            JsonText.parse(text) as? JsonObject ?: throw IOException("The response is not a JSON object")
        } catch (error: IllegalArgumentException) {
            throw IOException(error.message ?: "Unable to read the response")
        }
    }

    fun close() = client.close()

    companion object {
        val JSON = ContentType.parse("application/json; charset=utf-8")

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
