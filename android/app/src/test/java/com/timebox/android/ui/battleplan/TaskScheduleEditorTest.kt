package com.timebox.android.ui.battleplan

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TaskScheduleEditorTest {
    private val blank = TaskComposerDraft(title = "Task").toDetailDraft()
    private val suggested = LocalDate.parse("2099-09-19").atTime(15, 0).atZone(ZoneId.of("Asia/Singapore"))
    private val day = LocalDate.parse("2099-09-20")

    @Test fun pickingADateSetsADateOnlyDeadline() {
        assertNull(scheduleDate(blank, reminder = false))
        val changed = withScheduleDate(blank, reminder = false, date = day, suggestedReminder = suggested)
        assertEquals(TaskDeadlineMode.DateOnly, changed.deadlineMode)
        assertEquals(day, scheduleDate(changed, reminder = false))
        assertNull(scheduleTime(changed, reminder = false))
    }

    @Test fun timeCannotBeChosenBeforeADate() {
        assertEquals(blank, withScheduleTime(blank, reminder = false, time = LocalTime.of(17, 0)))
        assertEquals(blank, withScheduleTime(blank, reminder = true, time = LocalTime.of(17, 0)))
    }

    @Test fun addingAndRemovingATimeTogglesTheDeadlineMode() {
        val dated = withScheduleDate(blank, reminder = false, date = day, suggestedReminder = suggested)
        val timed = withScheduleTime(dated, reminder = false, time = LocalTime.of(17, 30))
        assertEquals(TaskDeadlineMode.DateTime, timed.deadlineMode)
        assertEquals(LocalTime.of(17, 30), scheduleTime(timed, reminder = false))
        val untimed = withoutDeadlineTime(timed)
        assertEquals(TaskDeadlineMode.DateOnly, untimed.deadlineMode)
        assertEquals(day, scheduleDate(untimed, reminder = false))
    }

    @Test fun changingTheDateKeepsAChosenTime() {
        val timed = withScheduleTime(withScheduleDate(blank, false, day, suggested), false, LocalTime.of(8, 0))
        val moved = withScheduleDate(timed, reminder = false, date = day.plusDays(1), suggestedReminder = suggested)
        assertEquals(TaskDeadlineMode.DateTime, moved.deadlineMode)
        assertEquals(LocalTime.of(8, 0), scheduleTime(moved, reminder = false))
    }

    @Test fun staleValuesAreIgnoredWhileUnset() {
        val stale = blank.copy(deadlineDate = "2099-01-01", deadlineTime = "10:00", reminderDate = "2099-01-01", reminderTime = "10:00")
        assertNull(scheduleDate(stale, reminder = false))
        assertNull(scheduleDate(stale, reminder = true))
        assertEquals(TaskDeadlineMode.DateOnly, withScheduleDate(stale, false, day, suggested).deadlineMode)
    }

    @Test fun newReminderStartsAtTheSuggestedTimeOnTheSuggestedDay() {
        val today = withScheduleDate(blank, reminder = true, date = suggested.toLocalDate(), suggestedReminder = suggested)
        assertTrue(today.reminderEnabled)
        assertEquals("15:00", today.reminderTime)
        val later = withScheduleDate(blank, reminder = true, date = day, suggestedReminder = suggested)
        assertEquals("09:00", later.reminderTime)
    }

    @Test fun movingAReminderKeepsItsTime() {
        val set = withScheduleTime(withScheduleDate(blank, true, day, suggested), true, LocalTime.of(20, 15))
        val moved = withScheduleDate(set, reminder = true, date = day.plusWeeks(1), suggestedReminder = suggested)
        assertEquals("20:15", moved.reminderTime)
    }

    @Test fun clearingRemovesOnlyThatSchedule() {
        val both = withScheduleDate(withScheduleDate(blank, false, day, suggested), true, day, suggested)
        val noDeadline = withoutSchedule(both, reminder = false)
        assertEquals(TaskDeadlineMode.None, noDeadline.deadlineMode)
        assertTrue(noDeadline.reminderEnabled)
        val noReminder = withoutSchedule(both, reminder = true)
        assertFalse(noReminder.reminderEnabled)
        assertEquals(TaskDeadlineMode.DateOnly, noReminder.deadlineMode)
    }
}
