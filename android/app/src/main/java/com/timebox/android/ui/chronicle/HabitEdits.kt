package com.timebox.android.ui.chronicle

import com.timebox.android.data.Habit
import com.timebox.android.data.HabitDay
import com.timebox.android.data.HabitDayState
import com.timebox.android.data.HabitItem
import com.timebox.android.data.HabitTotal
import com.timebox.android.data.HabitTotalTone
import com.timebox.android.data.HabitsWeek
import com.timebox.android.data.RecurrenceFrequency
import com.timebox.android.data.RecurrenceMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * The week as it should read once the server records a tick or untick of [key].
 *
 * This mirrors the backend's Habit Period rules closely enough to show a tap at once;
 * the server's recomputed week always replaces it when the request settles.
 */
internal fun HabitsWeek.withHabitEdit(key: HabitCellKey, tick: Boolean): HabitsWeek {
    val weekEnded = weekStart.plusDays(6).isBefore(today)
    return copy(
        habits = habits.map { habit ->
            when {
                habit.templateId != key.templateId -> habit
                key.itemId == null -> habit.withEdit(key.date, tick, today, weekEnded)
                else -> habit.copy(
                    items = habit.items.map { item ->
                        if (item.itemId == key.itemId) item.withEdit(key.date, tick, today, weekEnded) else item
                    },
                )
            }
        },
    )
}

/** A scheduled day's state after a tick or untick, or null when the tap changes nothing. */
private fun HabitDay.scheduledEdit(tick: Boolean, today: LocalDate): HabitDay? = when {
    tick && (state == HabitDayState.Open || state == HabitDayState.Missed) -> copy(state = HabitDayState.Met)
    !tick && state == HabitDayState.Met -> copy(state = if (date == today) HabitDayState.Open else HabitDayState.Missed)
    else -> null
}

/** A Checklist Item's Habit Periods follow its series' occurrences, so it edits like a scheduled row. */
private fun HabitItem.withEdit(date: LocalDate, tick: Boolean, today: LocalDate, weekEnded: Boolean): HabitItem {
    val day = days.firstOrNull { it.date == date && it.tickable } ?: return this
    val edited = day.scheduledEdit(tick, today) ?: return this
    val edits = days.map { if (it.date == date) edited else it }
    return copy(days = edits, total = total.copy(done = edits.count { it.state == HabitDayState.Met }).withPeriodTone(weekEnded))
}

private fun Habit.withEdit(date: LocalDate, tick: Boolean, today: LocalDate, weekEnded: Boolean): Habit {
    val day = days.firstOrNull { it.date == date && it.tickable } ?: return this
    val edited = if (mode == RecurrenceMode.Scheduled) {
        day.scheduledEdit(tick, today) ?: return this
    } else {
        val count = day.count + if (tick) 1 else -1
        if (count < 0) return this
        val state = if (frequency == RecurrenceFrequency.Daily) {
            val target = day.target ?: quotaCount ?: 0
            when {
                target in 1..count -> HabitDayState.Met
                date == today -> HabitDayState.Open
                count > 0 -> HabitDayState.Partial
                else -> HabitDayState.Missed
            }
        } else {
            when {
                count > 0 -> HabitDayState.Count
                date == today -> HabitDayState.Open
                else -> HabitDayState.Empty
            }
        }
        day.copy(count = count, state = state)
    }
    val edits = days.map { if (it.date == date) edited else it }
    val sessionTotal = mode == RecurrenceMode.Quota && frequency != RecurrenceFrequency.Daily
    val newTotal = if (sessionTotal) {
        val counted = total.month == null || YearMonth.from(date) == YearMonth.from(total.month)
        if (counted) total.copy(done = total.done + if (tick) 1 else -1).withSessionTone() else total
    } else {
        // Per-day periods: the total counts met days, as the server's period total does.
        total.copy(done = edits.count { it.state == HabitDayState.Met }).withPeriodTone(weekEnded)
    }
    return copy(days = edits, total = newTotal)
}

private fun HabitTotal.reached() = target > 0 && done >= target

private fun HabitTotal.withPeriodTone(weekEnded: Boolean) = copy(
    tone = when {
        reached() -> HabitTotalTone.Met
        weekEnded -> HabitTotalTone.Missed
        else -> HabitTotalTone.Open
    },
)

private fun HabitTotal.withSessionTone() = copy(
    tone = when {
        reached() -> HabitTotalTone.Met
        tone == HabitTotalTone.Met -> HabitTotalTone.Open
        else -> tone
    },
)
