package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note
import java.time.LocalDate

/** A day on the calendar and the notes made on it, newest first. */
data class CalendarDay(val date: LocalDate, val notes: List<Note>)
