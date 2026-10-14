package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarMonthTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val october = YearMonth.of(2026, 10)

    private fun note(id: String, made: LocalDateTime, zone: ZoneId = warsaw) =
        Note(id, id, "", NoteColor.Default, false, false, made.atZone(zone).toInstant().toEpochMilli(), 0, 0)

    /** Each week as day numbers, 0 where the week runs outside the month. */
    private fun CalendarMonth.numbers() = weeks.map { week -> week.map { it?.date?.dayOfMonth ?: 0 } }

    @Test
    fun A_month_is_whole_weeks_starting_on_the_first_day_given_with_gaps_outside_it() {
        val monday = CalendarMonth.of(emptyList(), october, warsaw, DayOfWeek.MONDAY)
        val sunday = CalendarMonth.of(emptyList(), october, warsaw, DayOfWeek.SUNDAY)

        // 1 October 2026 is a Thursday.
        assertEquals(listOf(0, 0, 0, 1, 2, 3, 4), monday.numbers().first())
        assertEquals(listOf(26, 27, 28, 29, 30, 31, 0), monday.numbers().last())
        assertEquals(5, monday.weeks.size)
        assertEquals(listOf(0, 0, 0, 0, 1, 2, 3), sunday.numbers().first())
        assertEquals(listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), sunday.weekdays.take(2))
    }

    @Test
    fun A_month_starting_on_the_first_day_of_the_week_has_no_gap_before_it() {
        // 1 June 2026 is a Monday.
        val june = CalendarMonth.of(emptyList(), YearMonth.of(2026, 6), warsaw, DayOfWeek.MONDAY)

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), june.numbers().first())
    }

    @Test
    fun A_note_falls_on_the_day_it_was_made_newest_first() {
        val morning = note("morning", LocalDateTime.of(2026, 10, 14, 8, 0))
        val evening = note("evening", LocalDateTime.of(2026, 10, 14, 21, 0))
        val other = note("other", LocalDateTime.of(2026, 10, 2, 12, 0))

        val calendar = CalendarMonth.of(listOf(morning, other, evening), october, warsaw, DayOfWeek.MONDAY)

        assertEquals(listOf("evening", "morning"), calendar.day(LocalDate.of(2026, 10, 14))!!.notes.map { it.id })
        assertEquals(listOf("other"), calendar.day(LocalDate.of(2026, 10, 2))!!.notes.map { it.id })
        assertEquals(emptyList<Note>(), calendar.day(LocalDate.of(2026, 10, 15))!!.notes)
    }

    @Test
    fun The_day_a_note_was_made_is_read_in_the_time_zone_given() {
        val lateUtc = note("late", LocalDateTime.of(2026, 10, 14, 23, 30), ZoneOffset.UTC)

        val calendar = CalendarMonth.of(listOf(lateUtc), october, warsaw, DayOfWeek.MONDAY)

        assertEquals(listOf("late"), calendar.day(LocalDate.of(2026, 10, 15))!!.notes.map { it.id })
    }

    @Test
    fun Notes_and_days_from_other_months_are_left_out() {
        val september = note("september", LocalDateTime.of(2026, 9, 30, 12, 0))

        val calendar = CalendarMonth.of(listOf(september), october, warsaw, DayOfWeek.MONDAY)

        assertNull(calendar.day(LocalDate.of(2026, 9, 30)))
        assertEquals(0, calendar.weeks.flatten().sumOf { it?.notes?.size ?: 0 })
    }
}
