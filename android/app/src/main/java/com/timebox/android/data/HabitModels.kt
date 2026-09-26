package com.timebox.android.data

import com.timebox.android.data.remote.HabitDto
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
    days = days.map {
        HabitDay(LocalDate.parse(it.date), HabitDayState.fromWire(it.state), it.count, it.target, it.tickable)
    },
    total = HabitTotal(
        done = total.done,
        target = total.target,
        unit = total.unit,
        month = total.month?.let(LocalDate::parse),
        tone = when (total.tone) {
            "met" -> HabitTotalTone.Met
            "missed" -> HabitTotalTone.Missed
            else -> HabitTotalTone.Open
        },
    ),
)

internal fun HabitsWeekDto.toModel() = HabitsWeek(
    today = LocalDate.parse(today),
    weekStart = LocalDate.parse(weekStart),
    earliestWeekStart = LocalDate.parse(earliestWeekStart),
    habits = habits.map { it.toModel() },
)
