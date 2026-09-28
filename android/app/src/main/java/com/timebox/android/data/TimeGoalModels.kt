package com.timebox.android.data

import com.timebox.android.data.remote.TimeGoalDto
import com.timebox.android.data.remote.TimeGoalsWeekDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class GoalBlock(val id: Int, val taskType: String, val name: String, val startAt: Instant,
                     val endAt: Instant?, val creditedSeconds: Double)
data class GoalPeriod(val start: LocalDate, val end: LocalDate, val targetMinutes: Int,
                      val durationSeconds: Double, val outcome: String, val blocks: List<GoalBlock>)
data class TimeGoal(
    val id: Int, val taskTypeId: Int, val taskType: String, val unit: String, val interval: Int,
    val startDate: LocalDate, val endDate: LocalDate?, val targetMinutes: Int,
    val nextTargetMinutes: Int?, val nextTargetDate: LocalDate, val period: GoalPeriod,
    val days: Map<LocalDate, Double>,
)
data class TimeGoalsWeek(val today: LocalDate, val weekStart: LocalDate, val earliestWeekStart: LocalDate,
                         val timezone: ZoneId, val capturedAt: Instant, val goals: List<TimeGoal>)

// SQLite may return a naive UTC timestamp; PostgreSQL returns its offset explicitly.
private fun goalInstant(value: String): Instant = Instant.parse(if (value.endsWith("Z") ||
    value.drop(10).contains('+') || value.drop(10).contains('-')) value else value + "Z")

internal fun TimeGoalDto.toModel() = TimeGoal(
    id, taskTypeId, taskType, unit, interval, LocalDate.parse(startDate), endDate?.let(LocalDate::parse),
    targetMinutes, nextTargetMinutes, LocalDate.parse(nextTargetDate),
    GoalPeriod(LocalDate.parse(period.start), LocalDate.parse(period.end), period.targetMinutes,
        period.durationSeconds, period.outcome, period.blocks.map {
            GoalBlock(it.id, it.taskType, it.name, goalInstant(it.startAt), it.endAt?.let(::goalInstant), it.creditedSeconds)
        }), days.mapKeys { LocalDate.parse(it.key) },
)

internal fun TimeGoalsWeekDto.toModel() = TimeGoalsWeek(LocalDate.parse(today), LocalDate.parse(weekStart),
    LocalDate.parse(earliestWeekStart), ZoneId.of(timezone), goalInstant(capturedAt), goals.map { it.toModel() })
