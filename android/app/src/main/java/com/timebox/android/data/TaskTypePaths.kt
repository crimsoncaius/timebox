package com.timebox.android.data

/**
 * Path handling for the task type picker.
 *
 * Task types are slash paths (`coding/ai/agents`) and the backend materialises every
 * missing ancestor when a deep path is created, so the picker has to reason about
 * segments rather than whole names.
 *
 * Matching and empty-query ranking follow ADR-0006 and ADR-0007: path-aware scoring
 * on a repaired query, Block usage only when the query is empty.
 */

private const val UNSPECIFIED = "unspecified"

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
 * Ranked matches for [query], best first.
 *
 * Empty query: current type first, then Block [TaskType.usageCount] descending, then name,
 * with `unspecified` last. Typed query: web path-match score, then name — not usage.
 */
fun rankTaskTypes(
    taskTypes: List<TaskType>,
    query: String,
    currentTypeId: Int? = null,
): List<TaskType> {
    val canonical = canonicalizeTaskTypePath(query) ?: return rankEmptyQuery(taskTypes, currentTypeId)
    return taskTypes
        .filter { pathMatchesQuery(canonicalName(it.name), canonical) }
        .sortedWith(
            compareBy<TaskType> { pathMatchScore(canonicalName(it.name), canonical) }
                .thenBy { it.name },
        )
}

private fun rankEmptyQuery(taskTypes: List<TaskType>, currentTypeId: Int?): List<TaskType> {
    val current = if (currentTypeId == null) emptyList() else taskTypes.filter { it.id == currentTypeId }
    val rest = taskTypes.filter { it.id != currentTypeId }
    val unspecified = rest.filter { it.name == UNSPECIFIED }
    val ranked = rest
        .filter { it.name != UNSPECIFIED }
        .sortedWith(compareByDescending<TaskType> { it.usageCount }.thenBy { it.name })
    return current + ranked + unspecified
}

private fun canonicalName(name: String): String =
    canonicalizeTaskTypePath(name) ?: name.trim().lowercase()

private fun segmentPrefixMatch(a: String, b: String): Boolean =
    a.startsWith(b) || b.startsWith(a)

private fun segmentsAlign(pathSegments: List<String>, querySegments: List<String>): Boolean {
    if (pathSegments.size != querySegments.size) return false
    return pathSegments.indices.all { segmentPrefixMatch(pathSegments[it], querySegments[it]) }
}

private fun minQueryAlignmentStart(path: String, query: String): Int? {
    val ps = path.split('/')
    val qs = query.split('/')
    if (qs.size > ps.size) return null
    for (start in 0..ps.size - qs.size) {
        if (segmentsAlign(ps.subList(start, start + qs.size), qs)) return start
    }
    return null
}

private fun pathMatchesQuery(path: String, query: String): Boolean {
    if (path == query) return true
    if (path.startsWith("$query/")) return true
    if (query.startsWith("$path/")) return true
    if (minQueryAlignmentStart(path, query) != null) return true
    val ps = path.split('/')
    val qs = query.split('/')
    val k = minOf(ps.size, qs.size)
    for (i in 0 until k) {
        if (!segmentPrefixMatch(ps[i], qs[i])) return false
    }
    return true
}

private fun pathMatchScore(path: String, query: String): Int {
    if (path == query) return 0
    if (path.startsWith("$query/")) return 1
    if (query.startsWith("$path/")) return 2
    if (path.startsWith(query)) return 3
    val start = minQueryAlignmentStart(path, query)
    if (start != null) return if (start == 0) 4 else 5
    val ps = path.split('/')
    val qs = query.split('/')
    val k = minOf(ps.size, qs.size)
    for (i in 0 until k) {
        if (!segmentPrefixMatch(ps[i], qs[i])) return 6
    }
    return 4
}

/** True when the query names a path that does not exist yet, so creating is on offer. */
fun shouldOfferCreate(taskTypes: List<TaskType>, query: String): Boolean {
    val canonical = canonicalizeTaskTypePath(query) ?: return false
    return taskTypes.none { canonicalName(it.name) == canonical }
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
    val existing = taskTypes.mapTo(mutableSetOf()) { canonicalName(it.name) }
    val missing = ancestors.filterNot { it in existing }
    return when {
        missing.isEmpty() -> CreateAncestorHint("Adds under existing ", ancestors.last())
        else -> CreateAncestorHint("Also creates ", missing.joinToString(", "))
    }
}
