package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * One month of the calendar, as weeks of seven days starting on [firstDay]. A week that runs into
 * the month before or after holds null there.
 */
data class CalendarMonth(val month: YearMonth, val firstDay: DayOfWeek, val weeks: List<List<CalendarDay?>>) {
    /** The days of the week in the order the weeks hold them. */
    val weekdays: List<DayOfWeek> get() = List(7) { firstDay.plus(it.toLong()) }

    /** [date] with its notes, or null when it is not in this month. */
    fun day(date: LocalDate): CalendarDay? = weeks.firstNotNullOfOrNull { week -> week.firstOrNull { it?.date == date } }

    companion object {
        /**
         * [month] with each of [notes] on its day in [zone]. The day is the note's position, which is its
         * memo's create time (ADR 0008), so every device puts a note on the same day.
         */
        fun of(notes: List<Note>, month: YearMonth, zone: ZoneId, firstDay: DayOfWeek): CalendarMonth {
            val byDate = notes.groupBy { Instant.ofEpochSecond(it.position).atZone(zone).toLocalDate() }
            val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
            val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
                .map { date -> CalendarDay(date, byDate[date].orEmpty().sortedByDescending { it.position }) }
            val weeks = (List(lead) { null } + days).chunked(7).map { week -> week + List(7 - week.size) { null } }
            return CalendarMonth(month, firstDay, weeks)
        }
    }
}
