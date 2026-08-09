package com.timebox.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTypePathsTest {

    /** The type list the design handoff's worked example is drawn against. */
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

    /** The exact list the handoff's first frame shows for query "ai" with coding/ai selected. */
    @Test
    fun `query ai ranks the design's example order`() {
        val ranked = rankTaskTypes(types, "ai", currentTypeId = 2)
        assertEquals(
            listOf("coding/ai", "coding/ai/agents", "coding/ai/evals", "reading/ai-papers"),
            names(ranked).take(4),
        )
    }

    @Test
    fun `substring matches rank below every prefix match`() {
        // `email` contains "ai" but starts with "em", so it trails the prefix tier even
        // though reading/ai-papers is used less often.
        val ranked = names(rankTaskTypes(types, "ai", currentTypeId = 2))
        assertTrue(ranked.indexOf("admin/email") > ranked.indexOf("reading/ai-papers"))
    }

    @Test
    fun `an exact path outranks a more used prefix match`() {
        val ranked = rankTaskTypes(types, "coding")
        assertEquals("coding", ranked.first().name)
    }

    @Test
    fun `the current type floats to the top even on a weak match`() {
        val ranked = rankTaskTypes(types, "ai", currentTypeId = 8)
        assertEquals("admin/email", ranked.first().name)
    }

    @Test
    fun `a slashed query matches whole paths, not segments`() {
        assertEquals(
            listOf("coding/ai", "coding/ai/agents", "coding/ai/evals"),
            names(rankTaskTypes(types, "coding/ai")),
        )
    }

    @Test
    fun `a fully typed new path matches nothing`() {
        assertTrue(rankTaskTypes(types, "coding/ai/tooling").isEmpty())
    }

    @Test
    fun `an empty query lists everything, busiest first`() {
        val ranked = rankTaskTypes(types, "  ")
        assertEquals(types.size, ranked.size)
        assertEquals(listOf("coding", "coding/ai", "coding/ai/agents"), names(ranked).take(3))
    }

    // ---- create affordance ------------------------------------------------

    @Test
    fun `create is offered only for a path that does not exist`() {
        assertTrue(shouldOfferCreate(types, "coding/ai/tooling"))
        assertTrue(shouldOfferCreate(types, "Coding/AI/Tooling "))
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
