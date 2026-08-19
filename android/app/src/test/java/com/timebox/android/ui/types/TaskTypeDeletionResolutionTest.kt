package com.timebox.android.ui.types

import com.timebox.android.data.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTypeDeletionResolutionTest {
    @Test
    fun usageSummaryIncludesBlocksTasksAndTemplates() {
        val type = TaskType(1, "coding", usageCount = 2, taskUsageCount = 3, recurringTemplateUsageCount = 4)
        assertEquals(9, type.totalUsageCount)
        assertTrue(type.hasTaskReferences)
    }

    @Test
    fun blockOnlyTypeDoesNotRequestReferenceClearing() {
        val type = TaskType(1, "coding", usageCount = 2)
        assertFalse(type.hasTaskReferences)
    }
}
