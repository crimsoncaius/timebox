package com.timebox.android.ui.day

import com.timebox.android.data.TaskStatus
import com.timebox.android.ui.battleplan.task
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadyToPlanTest {
    @Test
    fun readySelectorFlattensParentsAndSubtasksIndependently() {
        val childReady = task(3, ready = true).copy(parentId = 1, parentTitle = "Parent")
        val childNotReady = task(4).copy(parentId = 1)
        val parent = task(1, ready = false, subtasks = listOf(childReady, childNotReady))
        val standalone = task(2, status = TaskStatus.Blocked, ready = true)

        assertEquals(listOf(3, 2), listOf(parent, standalone).readyToPlanTasks().map { it.id })
    }
}
