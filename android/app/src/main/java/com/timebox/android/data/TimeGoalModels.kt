package com.timebox.android.data

import com.timebox.android.data.remote.TimeGoalDto
import com.timebox.android.data.remote.TimeGoalsWeekDto
import com.timebox.android.data.remote.ArchivedTimeGoalDto
import com.timebox.android.data.remote.GoalPeriodDto
import com.timebox.android.data.remote.TimeGoalArchiveDto
import com.timebox.android.data.remote.TimeGoalHistoryDto
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

data class ArchivedTimeGoal(val id: Int, val taskTypeId: Int, val taskType: String, val unit: String,
                            val interval: Int, val startDate: LocalDate, val endDate: LocalDate, val targetMinutes: Int) {
    fun inPeriod(period: GoalPeriod) = TimeGoal(id, taskTypeId, taskType, unit, interval, startDate, endDate,
        targetMinutes, null, endDate.plusDays(1), period, emptyMap())
}
data class TimeGoalArchive(val today: LocalDate, val timezone: ZoneId, val capturedAt: Instant, val goals: List<ArchivedTimeGoal>)
data class TimeGoalHistory(val goal: ArchivedTimeGoal, val timezone: ZoneId, val capturedAt: Instant,
                           val periods: List<GoalPeriod>, val nextBefore: LocalDate?)

// SQLite may return a naive UTC timestamp; PostgreSQL returns its offset explicitly.
private fun goalInstant(value: String): Instant = Instant.parse(if (value.endsWith("Z") ||
    value.drop(10).contains('+') || value.drop(10).contains('-')) value else value + "Z")

internal fun TimeGoalDto.toModel() = TimeGoal(
    id, taskTypeId, taskType, unit, interval, LocalDate.parse(startDate), endDate?.let(LocalDate::parse),
    targetMinutes, nextTargetMinutes, LocalDate.parse(nextTargetDate),
    period.toModel(), days.mapKeys { LocalDate.parse(it.key) },
)

internal fun TimeGoalsWeekDto.toModel() = TimeGoalsWeek(LocalDate.parse(today), LocalDate.parse(weekStart),
    LocalDate.parse(earliestWeekStart), ZoneId.of(timezone), goalInstant(capturedAt), goals.map { it.toModel() })

internal fun GoalPeriodDto.toModel() = GoalPeriod(LocalDate.parse(start), LocalDate.parse(end), targetMinutes,
    durationSeconds, outcome, blocks.map {
        GoalBlock(it.id, it.taskType, it.name, goalInstant(it.startAt), it.endAt?.let(::goalInstant), it.creditedSeconds)
    })
internal fun ArchivedTimeGoalDto.toModel() = ArchivedTimeGoal(id, taskTypeId, taskType, unit, interval,
    LocalDate.parse(startDate), LocalDate.parse(endDate), targetMinutes)
internal fun TimeGoalArchiveDto.toModel() = TimeGoalArchive(LocalDate.parse(today), ZoneId.of(timezone),
    goalInstant(capturedAt), goals.map { it.toModel() })
internal fun TimeGoalHistoryDto.toModel() = TimeGoalHistory(goal.toModel(), ZoneId.of(timezone), goalInstant(capturedAt),
    periods.map { it.toModel() }, nextBefore?.let(LocalDate::parse))
