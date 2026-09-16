package com.timebox.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTypePathsTest {

    private val types = listOf(
        taskType(1, "coding", 64),
        taskType(2, "coding/ai", 41),
        taskType(3, "coding/ai/agents", 32),
        taskType(4, "coding/ai/evals", 11),
        taskType(5, "reading", 19),
        taskType(6, "reading/ai-papers", 7),
        taskType(7, "admin", 12),
        taskType(8, "admin/email", 5),
        taskType(9, "meeting", 28),
        taskType(10, "unspecified", 400),
    )

    private fun taskType(id: Int, name: String, usageCount: Int) =
        TaskType(id = id, name = name, usageCount = usageCount)

    private fun names(rows: List<TaskType>) = rows.map { it.name }

    // ---- canonicalization -------------------------------------------------

    @Test
    fun `canonicalize trims and lowercases every segment`() {
        assertEquals("coding/ai/tooling", canonicalizeTaskTypePath("Coding/AI/Tooling "))
    }

    @Test
    fun `canonicalize collapses repeated and trailing slashes`() {
        assertEquals("coding/ai", canonicalizeTaskTypePath("coding//ai/"))
        assertEquals("coding", canonicalizeTaskTypePath("/coding"))
        assertEquals("coding", canonicalizeTaskTypePath("coding/"))
    }

    @Test
    fun `canonicalize collapses internal whitespace`() {
        assertEquals("deep work/writing", canonicalizeTaskTypePath("Deep   Work / Writing"))
    }

    @Test
    fun `canonicalize returns null when nothing survives`() {
        assertNull(canonicalizeTaskTypePath(""))
        assertNull(canonicalizeTaskTypePath("   "))
        assertNull(canonicalizeTaskTypePath("///"))
    }

    @Test
    fun `prefixes list every path the backend touches`() {
        assertEquals(
            listOf("coding", "coding/ai", "coding/ai/agents"),
            taskTypePathPrefixes("coding/ai/agents"),
        )
        assertEquals(listOf("coding"), taskTypePathPrefixes("coding"))
    }

    // ---- two-tone rendering -----------------------------------------------

    @Test
    fun `path parts separate ancestors from the leaf`() {
        assertEquals(
            TaskTypePathParts("coding / ai / ", "agents"),
            taskTypePathParts("coding/ai/agents"),
        )
    }

    @Test
    fun `a single segment is all leaf`() {
        assertEquals(TaskTypePathParts("", "meeting"), taskTypePathParts("meeting"))
    }

    // ---- ranking ----------------------------------------------------------

    @Test
    fun `query ai matches path-aware segments, not email substring`() {
        val ranked = names(rankTaskTypes(types, "ai", currentTypeId = 2))
        assertEquals(
            listOf("coding/ai", "coding/ai/agents", "coding/ai/evals", "reading/ai-papers"),
            ranked,
        )
        assertFalse(ranked.contains("admin/email"))
    }

    @Test
    fun `an exact path outranks a more used prefix match`() {
        val ranked = rankTaskTypes(types, "coding")
        assertEquals("coding", ranked.first().name)
    }

    @Test
    fun `typed query does not pin the current type above a better path match`() {
        val ranked = names(rankTaskTypes(types, "coding/ai", currentTypeId = 1))
        assertEquals("coding/ai", ranked.first())
        assertTrue(ranked.indexOf("coding") > ranked.indexOf("coding/ai/agents"))
    }

    @Test
    fun `a slashed query keeps ancestors and descendants`() {
        assertEquals(
            listOf("coding/ai", "coding/ai/agents", "coding/ai/evals", "coding"),
            names(rankTaskTypes(types, "coding/ai")),
        )
    }

    @Test
    fun `a fully typed new path still shows nearby existing paths`() {
        assertEquals(
            listOf("coding", "coding/ai"),
            names(rankTaskTypes(types, "coding/ai/tooling")),
        )
    }

    @Test
    fun `an empty query pins current, then usage, with unspecified last`() {
        val ranked = names(rankTaskTypes(types, "  ", currentTypeId = 2))
        assertEquals("coding/ai", ranked.first())
        assertEquals("unspecified", ranked.last())
        assertEquals(listOf("coding", "coding/ai/agents", "meeting"), ranked.drop(1).take(3))
    }

    @Test
    fun `unspecified is not repeated when it is already current`() {
        val ranked = names(rankTaskTypes(types, "", currentTypeId = 10))
        assertEquals("unspecified", ranked.first())
        assertEquals(1, ranked.count { it == "unspecified" })
    }

    // ---- create affordance ------------------------------------------------

    @Test
    fun `create is offered only for a path that does not exist`() {
        assertTrue(shouldOfferCreate(types, "coding/ai/tooling"))
        assertTrue(shouldOfferCreate(types, "Coding/AI/Tooling "))
        assertTrue(shouldOfferCreate(types, "coding//ai/tooling/"))
        assertFalse(shouldOfferCreate(types, "coding/ai"))
        assertFalse(shouldOfferCreate(types, "  CODING / AI  "))
        assertFalse(shouldOfferCreate(types, ""))
    }

    @Test
    fun `hint names the parent when every ancestor already exists`() {
        assertEquals(
            CreateAncestorHint("Adds under existing ", "coding/ai"),
            createAncestorHint(types, "coding/ai/tooling"),
        )
    }

    @Test
    fun `hint lists only the ancestors that will be created`() {
        assertEquals(
            CreateAncestorHint("Also creates ", "coding/tooling"),
            createAncestorHint(types, "coding/tooling/scripts"),
        )
    }

    @Test
    fun `a top level path has nothing to explain`() {
        assertNull(createAncestorHint(types, "errands"))
    }
}
