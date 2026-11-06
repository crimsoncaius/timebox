package com.timebox.android.ui.calendar

import com.timebox.android.data.TaskStatus
import com.timebox.android.ui.battleplan.task
import org.junit.Assert.*
import org.junit.Test

class CalendarTaskPickerTest {
    @Test fun projectSearchDistinguishesSameTitlesAndExcludesUnschedulableRows() {
        val first = task(1, projectId = 1).copy(title = "Review")
        val second = task(2, projectId = 2).copy(title = "Review")
        val completed = task(3, status = TaskStatus.Completed).copy(title = "Review")
        val quota = task(4).copy(title = "Review", recurrenceKind = "quota_parent")
        assertEquals(listOf(second), calendarTaskMatches(listOf(first, second, completed, quota), "Review Project 2"))
        assertEquals(listOf(first, second), calendarTaskMatches(listOf(first, second, completed, quota), "Review"))
        assertEquals("Project 1", first.calendarContext())
    }
}
