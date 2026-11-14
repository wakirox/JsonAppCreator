package it.sapienza.appinterpreter.ios

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.darwin.Darwin
import it.sapienza.appinterpreter.alerts.AlertsViewModel
import it.sapienza.appinterpreter.calendar.CreateCalendarEventUseCase
import it.sapienza.appinterpreter.runtime.AppRepository
import it.sapienza.appinterpreter.runtime.CurrentConfiguration
import it.sapienza.appinterpreter.runtime.RuntimeStrings
import it.sapienza.appinterpreter.runtime.ScreenViewModel
import it.sapienza.appinterpreter.runtime.usecase.DeleteItemsUseCase
import it.sapienza.appinterpreter.runtime.usecase.FetchJsonUseCase
import it.sapienza.appinterpreter.runtime.usecase.FindMissingApiKeysUseCase
import it.sapienza.appinterpreter.runtime.usecase.InitialFormValuesUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadListItemsUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadScreenUseCase
import it.sapienza.appinterpreter.runtime.usecase.LoadStoredItemsUseCase
import it.sapienza.appinterpreter.runtime.usecase.OpenLinkUseCase
import it.sapienza.appinterpreter.runtime.usecase.SaveItemUseCase
import it.sapienza.appinterpreter.runtime.usecase.SubmitFormUseCase
import it.sapienza.appinterpreter.runtime.usecase.UpdateItemUseCase
import it.sapienza.appinterpreter.security.ConfigurationKeys
import it.sapienza.appinterpreter.security.SecretStore
import it.sapienza.appinterpreter.storage.AppData
import it.sapienza.appinterpreter.storage.PhotoReader
import it.sapienza.appinterpreter.utils.httpclient.JsonHttpClient
import it.sapienza.appinterpreter.validation.ValidatedConfigurationParser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.cancel
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * What Android's Koin module builds, for the iPhone app: the shared engine's use cases on iOS implementations, running
 * the JSON app [json] ([versionName] and [platform] name the app in its requests' User-Agent, [userAgent]).
 */
class IosEngine(
    private val json: String,
    versionName: String,
    platform: String,
    /** Where the user's API keys are kept (the Keychain), under [configurationId]: the app's identity for them. */
    secrets: SecretStore,
    private val configurationId: String,
    /** The build's keys for the bundled examples: Base64 of {"v3_the_movie_db": {"TMDB_API_KEY": "…"}}, or empty. */
    bundledKeys: String = "",
    /** The JPEG bytes of a kept photo by its "appkin-photo:" reference (null: none), for requests that send photos. */
    photoData: (String) -> NSData?
) {
    val userAgent: String = JsonHttpClient.userAgent(versionName, platform)

    private val photos = PhotoReader { reference -> photoData(reference)?.toByteArray() }
    private val strings: RuntimeStrings = IosRuntimeStrings
    private val current = CurrentConfiguration { json }
    private val repository = AppRepository(current, ValidatedConfigurationParser(SharedValidator.validator))
    private val keys =
        ConfigurationKeys(secrets, ConfigurationKeys.decodeBundledValues(bundledKeys)) { configurationId }
    private val httpClient = JsonHttpClient(
        Darwin.create {
            configureRequest { setTimeoutInterval(30.0) }
        },
        userAgent,
        keys::replaceForCurrentConfiguration,
        keys::replaceInHeaderForCurrentConfiguration
    )

    // One for the app, so every screen hears of the changes the others save.
    private val appData = AppData(UserDefaultsAppStore())
    private val loadStored = LoadStoredItemsUseCase(appData, repository)

    /**
     * A screen of the app: the view named [viewId] (none: the app's main view) shown with [payload], a JSON object as
     * text, and [title] instead of the app's name. These are the arguments Android's screens start with. [alerts] is
     * shared by the screens of one stack.
     */
    fun screen(viewId: String?, payload: String?, title: String?, alerts: AlertsViewModel): ScreenViewModel {
        val arguments = buildMap<String, Any?> {
            viewId?.let { put(ScreenViewModel.EXTRA_VIEW_ID, it) }
            payload?.let { put(ScreenViewModel.EXTRA_SCREEN_PAYLOAD, it) }
            title?.let { put(ScreenViewModel.EXTRA_SCREEN_TITLE, it) }
        }
        return ScreenViewModel(
            saved = SavedStateHandle(arguments),
            alerts = alerts,
            fetchJson = FetchJsonUseCase(httpClient, photos),
            loadListItems = LoadListItemsUseCase(FetchJsonUseCase(httpClient, photos), loadStored),
            initialFormValues = InitialFormValuesUseCase(),
            submitForm = SubmitFormUseCase(),
            createCalendarEvent = CreateCalendarEventUseCase(),
            saveItem = SaveItemUseCase(appData, repository),
            updateItem = UpdateItemUseCase(appData, repository),
            deleteItems = DeleteItemsUseCase(appData, repository),
            openLink = OpenLinkUseCase(),
            findMissingApiKeys = FindMissingApiKeysUseCase(current, keys),
            loadScreen = LoadScreenUseCase(repository, strings),
            strings = strings
        )
    }

    /** Ends [screen]'s requests: call it when the screen is gone. */
    fun dispose(screen: ScreenViewModel) = screen.viewModelScope.cancel()

    /** Closes the app session's underlying Darwin HTTP client and invalidates its URLSession. */
    fun close() = httpClient.close()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
}

private object IosRuntimeStrings : RuntimeStrings {
    private fun text(english: String): String =
        NSBundle.mainBundle.localizedStringForKey(english, value = english, table = null)

    override fun viewMissing(id: String) = text("View '%@' does not exist in this configuration").replace("%@", id)

    override fun configurationInvalid(detail: String) = "${text("The saved configuration is invalid")}\n$detail"

    override val errorUnrecognized get() = text("Unrecognized error")

    override fun screenDataUnrestorable(detail: String) = "${text("Unable to restore screen data")}\n$detail"

    override fun valueMissing(path: String) = text("This item has no %@").replace("%@", path)

    override fun linkNotAllowed(url: String) =
        text("Appkin only opens web, map, phone and email addresses, not %@").replace("%@", url)

    override val errorOccurred get() = text("An error occurred")

    override val alertWithoutMessage get() = text("This alert has no message")

    override fun formIncomplete(fields: String) = text("Please complete: %@").replace("%@", fields)

    override val itemNoLongerSaved get() = text("Nothing to change: this item is no longer saved")

    override fun calendarBadDate(value: String) =
        text("The event's date and time must be dd/MM/yyyy and HH:mm, but they are \"%@\".").replace("%@", value)

    override val loadFailedOffline get() =
        text("Unable to load data. Check your connection and try again.")

    override fun loadFailed(detail: String) = "${text("Unable to load data.")} $detail"

    override fun requestFailed(status: Int) = text("Request failed with %@").replace("%@", status.toString())

    override fun imageDoesNotLoad(status: Int?, url: String): String {
        val reason = status?.let { text("error %@").replace("%@", it.toString()) } ?: text("no answer")
        return text("This image doesn't load (%1$@): %2$@. Check the address, or use another picture")
            .replace("%1$@", reason)
            .replace("%2$@", url)
    }
}
