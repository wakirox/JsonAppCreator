package it.sapienza.appinterpreter.runtime.usecase

import it.sapienza.appinterpreter.json.fillAddress
import it.sapienza.appinterpreter.json.matchReplace
import it.sapienza.appinterpreter.json.missingAddressPlaceholders
import it.sapienza.appinterpreter.json.missingPlaceholders
import it.sapienza.appinterpreter.model.event.CallServiceMethod
import it.sapienza.appinterpreter.model.event.RESTService
import it.sapienza.appinterpreter.storage.PhotoReader
import it.sapienza.appinterpreter.storage.Photos
import it.sapienza.appinterpreter.utils.httpclient.JsonHttpClient
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A request event's JSON answer: its URL filled in from the data (each value percent-encoded, see fillAddress), which
 * a POST also sends as its body, with each kept photo (a photos field's) as a "data:image/jpeg;base64,..." address, as
 * a server can't read the phone's own references. A URL whose placeholder has no value isn't requested: it fails with
 * [MissingValueException] instead.
 */
class FetchJsonUseCase(private val client: JsonHttpClient, private val photos: PhotoReader = PhotoReader.None) {
    suspend operator fun invoke(event: RESTService, data: JsonObject?): JsonObject =
        fetch(url(event, data), event, data)

    /** The URL [event] asks for with [data]. */
    fun url(event: RESTService, data: JsonObject?): String =
        event.url.fillAddress(data) ?: throw MissingValueException(event.url.missingAddressPlaceholders(data).first())

    /** [url], already filled in (such as a list's next page), asked for as [event] says, with its headers. */
    suspend fun fetch(url: String, event: RESTService, data: JsonObject?): JsonObject {
        val method = event.method ?: CallServiceMethod.get
        return client.request(
            url,
            method,
            body = if (method.sendsBody && data != null) withPhotos(data) as JsonObject else null,
            headers = headers(event, data)
        )
    }

    /** [element] with each kept photo's reference replaced by the photo itself, as a data address. */
    private suspend fun withPhotos(element: JsonElement): JsonElement = when {
        element is JsonObject -> JsonObject(element.mapValues { (_, value) -> withPhotos(value) })

        element is JsonArray -> JsonArray(element.map { withPhotos(it) })

        element is JsonPrimitive && element.isString && Photos.isPhoto(element.content) ->
            photos.jpeg(element.content)?.let { JsonPrimitive("data:image/jpeg;base64," + Base64.encode(it)) }
                ?: element

        else -> element
    }

    /** [event]'s headers filled in from [data]; like the URL, a header needing a value [data] lacks isn't sent. */
    fun headers(event: RESTService, data: JsonObject?): Map<String, String> =
        event.headers.orEmpty().mapValues { (_, value) ->
            value.missingPlaceholders(data).firstOrNull()?.let { throw MissingValueException(it) }
            value.matchReplace(data)
        }
}

/** A request needs the value at [path], which the data doesn't have. */
class MissingValueException(val path: String) : IllegalArgumentException("This item has no $path")
