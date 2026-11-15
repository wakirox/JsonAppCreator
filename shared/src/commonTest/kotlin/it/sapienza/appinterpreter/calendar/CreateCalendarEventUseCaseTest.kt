package it.sapienza.appinterpreter.calendar

import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.model.event.CalendarEvent
import it.sapienza.appinterpreter.runtime.EnglishRuntimeStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonObject

class CreateCalendarEventUseCaseTest {
    private val rome = TimeZone.of("Europe/Rome")
    private val create = CreateCalendarEventUseCase { rome }
    private val data = JsonText.parse("""{ "who": "Ana", "when": "20/10/2026", "guests": 6 }""") as JsonObject

    @Test
    fun aTimedEventStartsAtThatLocalTime() {
        val entry = assertIs<CreateCalendarEventUseCase.Result.Ready>(
            create(
                CalendarEvent(
                    title = "Dinner with ${'$'}{who}",
                    date = "${'$'}{when}",
                    time = "19:30",
                    durationMinutes = 150,
                    description = "${'$'}{guests} guests"
                ),
                data
            )
        ).entry
        val begin = LocalDateTime(2026, 10, 20, 19, 30).toInstant(rome).toEpochMilliseconds()
        assertEquals(CalendarEntry("Dinner with Ana", begin, begin + 150 * 60_000L, false, null, "6 guests"), entry)
    }

    @Test
    fun withoutATimeItCoversThatDayInUtc() {
        val entry = assertIs<CreateCalendarEventUseCase.Result.Ready>(
            create(CalendarEvent(title = "Day off", date = "20/10/2026"), data)
        ).entry
        val midnight = LocalDateTime(2026, 10, 20, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()
        assertEquals(midnight, entry.beginMillis)
        assertEquals(midnight + 24 * 3_600_000L, entry.endMillis)
        assertEquals(true, entry.allDay)
    }

    @Test
    fun aDateThatIsntDdMmYyyyIsExplained() {
        val result =
            assertIs<CreateCalendarEventUseCase.Result.BadDate>(
                create(CalendarEvent(title = "T", date = "2026-10-20", time = "9:00"), data)
            )
        assertEquals("2026-10-20 9:00", result.value)
        assertEquals(
            "The event's date and time must be dd/MM/yyyy and HH:mm, but they are \"2026-10-20 9:00\".",
            EnglishRuntimeStrings.calendarBadDate(result.value)
        )
    }
}
