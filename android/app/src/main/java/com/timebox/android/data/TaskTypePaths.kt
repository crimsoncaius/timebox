package com.timebox.android.data

/**
 * Path handling for the task type picker.
 *
 * Task types are slash paths (`coding/ai/agents`) and the backend materialises every
 * missing ancestor when a deep path is created, so the picker has to reason about
 * segments rather than whole names.
 *
 * The ranking here is the one specified in the picker design handoff, which is *not*
 * the web frontend's `taskTypePaths.ts` ranking — see [rankTaskTypes].
 */

/**
 * What the backend will actually store for `input`, or null when there is nothing to store.
 *
 * Deliberately more forgiving than the server's own `canonicalize_task_type_path`, which
 * rejects empty segments outright: a half-typed `coding/` or a fat-fingered `coding//ai`
 * should offer to create `coding/ai` rather than blocking on an error the user can't see.
 * Because the create row sends this exact string, the two never disagree about the result.
 */
fun canonicalizeTaskTypePath(input: String): String? {
    val segments = input
        .split('/')
        .map { segment -> segment.trim().lowercase().replace(WHITESPACE_RUN, " ") }
        .filter { it.isNotEmpty() }
    return segments.joinToString("/").ifEmpty { null }
}

private val WHITESPACE_RUN = Regex("\\s+")

/** `coding/ai/agents` -> every path the backend touches, shallowest first. */
fun taskTypePathPrefixes(path: String): List<String> {
    val segments = path.split('/')
    return segments.indices.map { index -> segments.take(index + 1).joinToString("/") }
}

/**
 * A path split for two-tone rendering: dimmed ancestors, full-contrast leaf.
 *
 * [ancestors] already carries the design's spaced separators (`coding / ai / `) and is
 * empty for a single-segment path.
 */
data class TaskTypePathParts(val ancestors: String, val leaf: String)

fun taskTypePathParts(path: String, separator: String = " / "): TaskTypePathParts {
    val segments = path.split('/')
    val leaf = segments.last()
    if (segments.size == 1) return TaskTypePathParts("", leaf)
    return TaskTypePathParts(
        ancestors = segments.dropLast(1).joinToString(separator) + separator,
        leaf = leaf,
    )
}

/**
 * Ranked matches for `query`, best first.
 *
 * Three tiers, per the handoff: the exact path, then prefix matches, then substring
 * matches, each tier ordered by usage count and then by name so the list is stable.
 * A single-segment query matches per segment, so `ai` finds `coding/ai/agents`; once the
 * query contains a slash it is matched against the whole path instead, which is what
 * makes a fully-typed new path like `coding/ai/tooling` report no matches at all.
 *
 * [currentTypeId] — the block's existing type — floats to the top whenever it matches,
 * so reassigning never hides what the block is set to today.
 *
 * Note this diverges from the web frontend's matcher, which scores by segment alignment
 * and will order the same query differently. The design pins this behaviour for mobile.
 */
fun rankTaskTypes(
    taskTypes: List<TaskType>,
    query: String,
    currentTypeId: Int? = null,
): List<TaskType> {
    val canonical = canonicalizeTaskTypePath(query)
        ?: return taskTypes.sortedWith(defaultOrder(currentTypeId))

    return taskTypes
        .mapNotNull { type -> matchTier(type, canonical)?.let { tier -> type to tier } }
        .sortedWith(
            compareBy<Pair<TaskType, Int>> { (type, _) -> if (type.id == currentTypeId) 0 else 1 }
                .thenBy { (_, tier) -> tier }
                .thenByDescending { (type, _) -> type.usageCount }
                .thenBy { (type, _) -> type.name },
        )
        .map { (type, _) -> type }
}

/** 0 exact, 1 prefix, 2 substring, null when the type should not be listed. */
private fun matchTier(type: TaskType, canonicalQuery: String): Int? {
    if (type.name == canonicalQuery) return 0
    // A slashed query is already a path, so comparing it segment-by-segment would never
    // match. Fall back to the whole name and let it narrow to nothing as the user types.
    val haystacks = if ('/' in canonicalQuery) listOf(type.name) else type.name.split('/')
    return when {
        haystacks.any { it.startsWith(canonicalQuery) } -> 1
        haystacks.any { it.contains(canonicalQuery) } -> 2
        else -> null
    }
}

private fun defaultOrder(currentTypeId: Int?): Comparator<TaskType> =
    compareBy<TaskType> { if (it.id == currentTypeId) 0 else 1 }
        .thenByDescending { it.usageCount }
        .thenBy { it.name }

/** True when the query names a path that does not exist yet, so creating is on offer. */
fun shouldOfferCreate(taskTypes: List<TaskType>, query: String): Boolean {
    val canonical = canonicalizeTaskTypePath(query) ?: return false
    return taskTypes.none { it.name == canonical }
}

/** Split so the caller can set [path] in the monospace face the design asks for. */
data class CreateAncestorHint(val lead: String, val path: String, val tail: String = ".")

/**
 * The line under the list explaining what a create will touch besides the leaf itself,
 * or null for a top-level path where there is nothing to explain.
 */
fun createAncestorHint(taskTypes: List<TaskType>, canonicalPath: String): CreateAncestorHint? {
    val ancestors = taskTypePathPrefixes(canonicalPath).dropLast(1)
    if (ancestors.isEmpty()) return null
    val existing = taskTypes.mapTo(mutableSetOf()) { it.name }
    val missing = ancestors.filterNot { it in existing }
    return when {
        missing.isEmpty() -> CreateAncestorHint("Adds under existing ", ancestors.last())
        else -> CreateAncestorHint("Also creates ", missing.joinToString(", "))
    }
}
