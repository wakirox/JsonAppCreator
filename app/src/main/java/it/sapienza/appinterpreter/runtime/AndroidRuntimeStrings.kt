package it.sapienza.appinterpreter.runtime

import android.content.Context
import it.sapienza.androidratio.appratio.R

/** The runtime's messages from the app's string resources. */
class AndroidRuntimeStrings(private val context: Context) : RuntimeStrings {
    override fun viewMissing(id: String) = context.getString(R.string.view_missing, id)
    override fun configurationInvalid(detail: String) = context.getString(R.string.configuration_invalid, detail)
    override val errorUnrecognized: String get() = context.getString(R.string.error_unrecognized)
    override fun screenDataUnrestorable(detail: String) = context.getString(R.string.screen_data_unrestorable, detail)
    override fun valueMissing(path: String) = context.getString(R.string.value_missing, path)
    override fun linkNotAllowed(url: String) = context.getString(R.string.link_not_allowed, url)
    override val errorOccurred: String get() = context.getString(R.string.error_occurred)
    override val alertWithoutMessage: String get() = context.getString(R.string.alert_without_message)
    override fun formIncomplete(fields: String) = context.getString(R.string.form_incomplete, fields)
    override val itemNoLongerSaved: String get() = context.getString(R.string.item_no_longer_saved)
    override fun calendarBadDate(value: String) = context.getString(R.string.calendar_bad_date, value)
    override val loadFailedOffline: String get() = context.getString(R.string.load_failed_offline)
    override fun loadFailed(detail: String) = context.getString(R.string.load_failed, detail)
    override fun requestFailed(status: Int) = context.getString(R.string.request_failed, status)
    override fun imageDoesNotLoad(status: Int?, url: String) = if (status != null) {
        context.getString(R.string.image_does_not_load_status, status, url)
    } else {
        context.getString(R.string.image_does_not_load_no_answer, url)
    }
}
