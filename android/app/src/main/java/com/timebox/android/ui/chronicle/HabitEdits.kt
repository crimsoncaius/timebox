package com.timebox.android.ui.chronicle

import com.timebox.android.data.Habit
import com.timebox.android.data.HabitDayState
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
            if (habit.templateId == key.templateId) habit.withEdit(key.date, tick, today, weekEnded) else habit
        },
    )
}

private fun Habit.withEdit(date: LocalDate, tick: Boolean, today: LocalDate, weekEnded: Boolean): Habit {
    val day = days.firstOrNull { it.date == date && it.tickable } ?: return this
    val edited = if (mode == RecurrenceMode.Scheduled) {
        val state = when {
            tick && (day.state == HabitDayState.Open || day.state == HabitDayState.Missed) -> HabitDayState.Met
            !tick && day.state == HabitDayState.Met -> if (date == today) HabitDayState.Open else HabitDayState.Missed
            else -> return this
        }
        day.copy(state = state)
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
