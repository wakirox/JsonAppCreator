package it.sapienza.appinterpreter.calendar

import it.sapienza.appinterpreter.json.matchReplace
import it.sapienza.appinterpreter.model.event.CalendarEvent
import it.sapienza.appinterpreter.utils.FormDates
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonObject

/**
 * The calendar app's new-event screen, filled in from a calendar event and its data. Nothing is written to the
 * calendar here: the user saves the event in their calendar app, so no calendar permission is needed.
 */
class CreateCalendarEventUseCase(private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }) {

    sealed interface Result {
        class Ready(val entry: CalendarEntry) : Result

        /** The date or time isn't dd/MM/yyyy and HH:mm. */

        // The date (and time) as written, which isn't dd/MM/yyyy (HH:mm).
        class BadDate(val value: String) : Result
    }

    operator fun invoke(event: CalendarEvent, data: JsonObject?): Result {
        val date = event.date.matchReplace(data)
        val time = event.time?.matchReplace(data)?.takeIf { it.isNotBlank() }
        // A timed event starts at that local time; an all-day one covers that day from UTC midnight to the next,
        // as the calendar provider defines all-day events.
        val start = if (time != null) {
            FormDates.parseDateTime("$date $time")?.toInstant(zone())?.toEpochMilliseconds()
        } else {
            FormDates.startOfDayMillis(date, TimeZone.UTC)
        } ?: return Result.BadDate(date + time?.let { " $it" }.orEmpty())
        val end = start + if (time != null) (event.durationMinutes ?: 60) * 60_000L else 24 * 3_600_000L
        return Result.Ready(
            CalendarEntry(
                title = event.title.matchReplace(data),
                beginMillis = start,
                endMillis = end,
                allDay = time == null,
                location = event.location?.matchReplace(data),
                description = event.description?.matchReplace(data)
            )
        )
    }
}
