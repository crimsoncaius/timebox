package com.timebox.android.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockIdentityTest {
    private val linkedTask = LinkedTask(
        id = 7,
        title = "Prepare launch",
        status = TaskStatus.Open,
        taskTypeId = 2,
        archivedAt = null,
        deletedAt = null,
    )

    private val linked = TimeBlock(
        id = 1,
        lane = Lane.Planned,
        taskTypeId = 2,
        taskTypeName = "Deep work",
        taskId = linkedTask.id,
        task = linkedTask,
        note = null,
        plannedBlockId = null,
        startMinute = 600,
        endMinute = 660,
        name = "Outline session",
    )

    @Test
    fun `linked name is primary and Battle Plan Task is secondary`() {
        assertEquals("Outline session", linked.primaryIdentity())
        assertEquals("Prepare launch", linked.secondaryIdentity())

        val unnamed = linked.copy(name = null)
        assertEquals("Prepare launch", unnamed.primaryIdentity())
        assertEquals("Deep work", unnamed.secondaryIdentity())
    }

    @Test
    fun `unlink keeps name and unnamed unlink uses normal fallback`() {
        val namedUnlinked = linked.copy(taskId = null, task = null)
        assertEquals("Outline session", namedUnlinked.primaryIdentity())
        assertEquals("Deep work", namedUnlinked.secondaryIdentity())

        val unnamedUnlinked = namedUnlinked.copy(name = null)
        assertEquals("Deep work", unnamedUnlinked.primaryIdentity())
        assertNull(unnamedUnlinked.secondaryIdentity())
    }

    @Test
    fun `Actual Block follows the same linked identity precedence`() {
        val actual = ActualBlock(
            id = 3,
            taskTypeId = 2,
            taskTypeName = "Deep work",
            taskId = linkedTask.id,
            task = linkedTask,
            note = null,
            plannedBlockId = null,
            startAt = Instant.EPOCH,
            endAt = Instant.EPOCH.plusSeconds(1800),
            name = "Review session",
        )
        assertEquals("Review session", actual.primaryIdentity())
        assertEquals("Prepare launch", actual.secondaryIdentity())
        assertEquals("Prepare launch", actual.copy(name = null).primaryIdentity())
    }
}
