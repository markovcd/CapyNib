package io.github.markovcd.capynib.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.board.CalendarDay
import io.github.markovcd.capynib.core.board.CalendarMonth
import io.github.markovcd.capynib.ui.Tags
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** The most dots a day shows, however many notes were made on it. */
private const val MAX_MARKS = 3

/** How far a sideways swipe over the month goes before it turns the page. */
private val MONTH_SWIPE = 64.dp

/**
 * The board as a month's calendar: each day marks the notes made on it, and the picked [BoardState.day]'s
 * notes follow as cards. The arrows, or a sideways swipe over the days, turn the month.
 */
@Composable
internal fun NoteCalendar(calendar: CalendarMonth, state: BoardState, actions: BoardActions, modifier: Modifier) {
    val selection = state.selection
    val picked = calendar.day(state.day)
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        modifier = modifier.fillMaxSize().testTag(Tags.CALENDAR),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine, key = "month") {
            Month(calendar, state.day, state.today, actions.onMonth, actions.onDay)
        }
        if (picked == null) return@LazyVerticalStaggeredGrid
        item(span = StaggeredGridItemSpan.FullLine, key = "day-${picked.date}") {
            DayHeading(picked)
        }
        items(picked.notes, key = { it.id }) { note ->
            NoteCard(
                note,
                selected = note.id in selection.ids,
                playing = state.playing,
                onPlay = actions.onPlay,
                onOpen = { if (selection.isActive) actions.selection.toggle(note.id) else actions.onOpen(note.id) },
            )
        }
    }
}

@Composable
private fun Month(calendar: CalendarMonth, day: LocalDate, today: LocalDate, onMonth: (Long) -> Unit, onDay: (LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme
    val swipe = with(LocalDensity.current) { MONTH_SWIPE.toPx() }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(-1) }, modifier = Modifier.testTag(Tags.PREVIOUS_MONTH)) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.board_previous_month), tint = colors.onSurfaceVariant)
            }
            Text(
                calendar.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                modifier = Modifier.weight(1f).testTag(Tags.MONTH),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onMonth(1) }, modifier = Modifier.testTag(Tags.NEXT_MONTH)) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.board_next_month), tint = colors.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(top = 4.dp)) {
            calendar.weekdays.forEach { weekday ->
                Text(
                    weekday.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            Modifier.pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged > swipe) onMonth(-1)
                        if (dragged < -swipe) onMonth(1)
                    },
                ) { _, by -> dragged += by }
            },
        ) {
            calendar.weeks.forEach { week ->
                Row {
                    week.forEach { cell ->
                        Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.Center) {
                            if (cell != null) Day(cell, picked = cell.date == day, today = cell.date == today, onDay)
                        }
                    }
                }
            }
        }
    }
}

/** A day's number, ringed when it is today and filled when picked, over a dot per note made on it. */
@Composable
private fun Day(day: CalendarDay, picked: Boolean, today: Boolean, onDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (picked) colors.primaryContainer else colors.surface)
            .clickable { onDay(day.date) }
            .semantics { selected = picked }
            .testTag(Tags.day(day.date)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (today) FontWeight.Bold else null,
            color = when {
                picked -> colors.onPrimaryContainer
                today -> colors.primary
                else -> colors.onSurface
            },
        )
        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (day.notes.isNotEmpty()) {
                Row(Modifier.testTag(Tags.dayMarks(day.date)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(minOf(day.notes.size, MAX_MARKS)) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (picked) colors.onPrimaryContainer else colors.primary))
                    }
                }
            }
        }
    }
}

/** The picked day spelled out, or a line saying nothing was made on it. */
@Composable
private fun DayHeading(day: CalendarDay) {
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
        Text(
            day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)).uppercase(locale),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (day.notes.isEmpty()) {
            Text(
                stringResource(R.string.board_day_empty),
                modifier = Modifier.padding(top = 12.dp).testTag(Tags.DAY_EMPTY),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
