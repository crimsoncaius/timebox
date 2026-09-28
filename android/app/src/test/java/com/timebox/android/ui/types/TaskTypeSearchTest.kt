package com.timebox.android.ui.types

import com.timebox.android.data.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTypeSearchTest {
    private val groups = listOf(
        TypeGroup("coding", listOf(TaskType(1, "coding", 0), TaskType(2, "coding/ai", 0))),
        TypeGroup("exercise", listOf(TaskType(3, "exercise/cardio", 0))),
    )

    private fun visible(input: String) =
        TypesUiState(groups = groups, input = input).visibleGroups.map { g -> g.root to g.items.map { it.name } }

    @Test
    fun `blank input shows every type`() {
        assertEquals(listOf("coding" to listOf("coding", "coding/ai"), "exercise" to listOf("exercise/cardio")), visible("  "))
    }

    @Test
    fun `input narrows to matching types and drops empty groups`() {
        assertEquals(listOf("coding" to listOf("coding", "coding/ai")), visible("Cod"))
        assertEquals(listOf("coding" to listOf("coding/ai")), visible("ai"))
        assertEquals(listOf("exercise" to listOf("exercise/cardio")), visible("card"))
    }

    @Test
    fun `no match leaves nothing visible`() {
        assertEquals(emptyList<Pair<String, List<String>>>(), visible("nomatchxyz"))
    }
}
