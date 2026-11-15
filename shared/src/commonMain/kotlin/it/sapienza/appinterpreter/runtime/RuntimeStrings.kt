package it.sapienza.appinterpreter.runtime

/** The runtime's messages in the user's language (Android: the app's string resources). */
interface RuntimeStrings {
    fun viewMissing(id: String): String
    fun configurationInvalid(detail: String): String
    val errorUnrecognized: String
    fun screenDataUnrestorable(detail: String): String

    /** A request or link needs the value at [path], which this item doesn't have. */
    fun valueMissing(path: String): String

    /** [url] isn't a web, map, phone or email address. */
    fun linkNotAllowed(url: String): String

    /** Something failed without saying what. */
    val errorOccurred: String

    /** An alert event whose message is empty. */
    val alertWithoutMessage: String

    /** A form was sent with these required [fields] (their titles, joined) still empty. */
    fun formIncomplete(fields: String): String

    /** The item a form or tick changes was deleted meanwhile. */
    val itemNoLongerSaved: String

    /** A calendar event's date (and time) [value] isn't dd/MM/yyyy (HH:mm). */
    fun calendarBadDate(value: String): String

    /** A list couldn't load because the phone is offline or the server didn't answer. */
    val loadFailedOffline: String

    /** A list couldn't load, for the reason [detail]. */
    fun loadFailed(detail: String): String

    /** A request was answered with the HTTP error [status]. */
    fun requestFailed(status: Int): String

    /** The image at [url] answered with the HTTP error [status], or not at all (null). */
    fun imageDoesNotLoad(status: Int?, url: String): String
}

/** The English messages, for tests and as a fallback. */
object EnglishRuntimeStrings : RuntimeStrings {
    override fun viewMissing(id: String) = "View '$id' does not exist in this configuration"
    override fun configurationInvalid(detail: String) = "The saved configuration is invalid\n$detail"
    override val errorUnrecognized = "Unrecognized error"
    override fun screenDataUnrestorable(detail: String) = "Unable to restore screen data\n$detail"
    override fun valueMissing(path: String) = "This item has no $path"
    override fun linkNotAllowed(url: String) = "Appkin only opens web, map, phone and email addresses, not $url"
    override val errorOccurred = "An error occurred"
    override val alertWithoutMessage = "This alert has no message"
    override fun formIncomplete(fields: String) = "Please complete: $fields"
    override val itemNoLongerSaved = "Nothing to change: this item is no longer saved"
    override fun calendarBadDate(value: String) =
        "The event's date and time must be dd/MM/yyyy and HH:mm, but they are \"$value\"."
    override val loadFailedOffline = "Unable to load data. Check your connection and try again."
    override fun loadFailed(detail: String) = "Unable to load data. $detail"
    override fun requestFailed(status: Int) = "Request failed with $status"
    override fun imageDoesNotLoad(status: Int?, url: String) = "This image doesn't load (${status?.let {
        "error $it"
    } ?: "no answer"}): $url. Check the address, or use another picture"
}
