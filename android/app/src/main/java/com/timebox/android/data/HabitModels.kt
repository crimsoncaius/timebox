package com.timebox.android.data

import com.timebox.android.data.remote.HabitDayDto
import com.timebox.android.data.remote.HabitDto
import com.timebox.android.data.remote.HabitItemDto
import com.timebox.android.data.remote.HabitTotalDto
import com.timebox.android.data.remote.HabitsWeekDto
import java.time.LocalDate

/** How one day of a Habit row reads; see Habit Period in CONTEXT.md. */
enum class HabitDayState(val wire: String) {
    NotDue("not_due"), Excused("excused"), Upcoming("upcoming"), Open("open"), Met("met"),
    Missed("missed"), Partial("partial"), Count("count"), Empty("empty");

    companion object {
        fun fromWire(value: String): HabitDayState = entries.firstOrNull { it.wire == value } ?: Excused
    }
}

enum class HabitTotalTone { Met, Missed, Open }

data class HabitDay(
    val date: LocalDate,
    val state: HabitDayState,
    val count: Int,
    val target: Int?,
    val tickable: Boolean,
)

/** [done] of [target] days, sessions, or month-to-date sessions when [month] is set. */
data class HabitTotal(
    val done: Int,
    val target: Int,
    val unit: String,
    val month: LocalDate?,
    val tone: HabitTotalTone,
)

/** A Checklist Item Habit, shown under its series. */
data class HabitItem(
    val itemId: Int,
    val title: String,
    val days: List<HabitDay>,
    val total: HabitTotal,
)

/**
 * A series' row. When [tracked] is false the series is not itself a Habit and only
 * heads its tracked [items]; its [days] and [total] are then not shown.
 */
data class Habit(
    val templateId: Int,
    val title: String,
    val mode: RecurrenceMode,
    val status: RecurrenceStatus,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val weekdays: List<Int>,
    val monthDay: Int?,
    val quotaCount: Int?,
    val days: List<HabitDay>,
    val total: HabitTotal,
    val tracked: Boolean = true,
    val items: List<HabitItem> = emptyList(),
)

data class HabitsWeek(
    val today: LocalDate,
    val weekStart: LocalDate,
    val earliestWeekStart: LocalDate,
    val habits: List<Habit>,
)

internal fun HabitDto.toModel() = Habit(
    templateId = templateId,
    title = title,
    mode = RecurrenceMode.fromWire(mode),
    status = RecurrenceStatus.fromWire(status),
    frequency = RecurrenceFrequency.fromWire(frequency),
    interval = interval,
    weekdays = weekdays,
    monthDay = monthDay,
    quotaCount = quotaCount,
    days = days.map { it.toModel() },
    total = total.toModel(),
    tracked = tracked,
    items = items.map { it.toModel() },
)

internal fun HabitItemDto.toModel() = HabitItem(
    itemId = itemId,
    title = title,
    days = days.map { it.toModel() },
    total = total.toModel(),
)

private fun HabitDayDto.toModel() =
    HabitDay(LocalDate.parse(date), HabitDayState.fromWire(state), count, target, tickable)

private fun HabitTotalDto.toModel() = HabitTotal(
    done = done,
    target = target,
    unit = unit,
    month = month?.let(LocalDate::parse),
    tone = when (tone) {
        "met" -> HabitTotalTone.Met
        "missed" -> HabitTotalTone.Missed
        else -> HabitTotalTone.Open
    },
)

internal fun HabitsWeekDto.toModel() = HabitsWeek(
    today = LocalDate.parse(today),
    weekStart = LocalDate.parse(weekStart),
    earliestWeekStart = LocalDate.parse(earliestWeekStart),
    habits = habits.map { it.toModel() },
)
