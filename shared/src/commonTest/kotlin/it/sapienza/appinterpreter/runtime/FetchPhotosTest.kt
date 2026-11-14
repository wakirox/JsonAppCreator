package it.sapienza.appinterpreter.runtime

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.content.TextContent
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.model.event.CallServiceMethod
import it.sapienza.appinterpreter.model.event.RESTService
import it.sapienza.appinterpreter.runtime.usecase.FetchJsonUseCase
import it.sapienza.appinterpreter.storage.PhotoReader
import it.sapienza.appinterpreter.utils.httpclient.JsonHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** A form's photos reach a server as the images themselves, not as the phone's references. */
class FetchPhotosTest {
    private var sent: JsonObject? = null
    private val engine = MockEngine { request ->
        sent = JsonText.parse((request.body as TextContent).text) as JsonObject
        respond("{}")
    }

    @Test
    fun keptPhotosAreSentAsDataAddresses() = runTest {
        val photos = PhotoReader { reference -> if (reference == "appkin-photo:a.jpg") byteArrayOf(1, 2, 3) else null }
        val fetch = FetchJsonUseCase(JsonHttpClient(engine, "Appkin/test"), photos)
        val body = JsonText.parse(
            """{ "title": "Tile", "photos": ["appkin-photo:a.jpg", "appkin-photo:gone.jpg"], "note": "appkin-photo is just text here" }"""
        ) as JsonObject

        fetch(RESTService(url = "https://example.com/ideas", method = CallServiceMethod.post), body)

        val photosSent = sent!!.getValue("photos").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(
            listOf("data:image/jpeg;base64,AQID", "appkin-photo:gone.jpg"),
            photosSent,
            "a missing photo stays as it was"
        )
        assertEquals("Tile", sent!!.getValue("title").jsonPrimitive.content)
        assertEquals("appkin-photo is just text here", sent!!.getValue("note").jsonPrimitive.content)
    }

    @Test
    fun getAndDeleteRequestsDoNotReadLocalPhotos() = runTest {
        var photoReadAttempted = false
        val photos = PhotoReader {
            photoReadAttempted = true
            byteArrayOf(1, 2, 3)
        }
        val bodylessEngine = MockEngine { respond("{}") }
        val fetch = FetchJsonUseCase(JsonHttpClient(bodylessEngine, "Appkin/test"), photos)
        val payload = JsonText.parse("""{ "photo": "appkin-photo:a.jpg" }""") as JsonObject

        fetch(RESTService(url = "https://example.com/ideas", method = CallServiceMethod.get), payload)
        fetch(RESTService(url = "https://example.com/ideas/1", method = CallServiceMethod.delete), payload)

        assertFalse(photoReadAttempted)
    }
}
