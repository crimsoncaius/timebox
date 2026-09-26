package com.timebox.android.ui.battleplan

import com.timebox.android.data.TaskType

/**
 * Battle Plan Task Type filter rules.
 *
 * The filter holds the Task Type ids the user chose directly. A chosen type covers its
 * whole branch, so choosing `coding` also shows tasks typed `coding/ai` or `coding/ai/evals`.
 */

internal fun TaskType.isUnder(ancestor: TaskType): Boolean = name.startsWith(ancestor.name + "/")

/** Every Task Type id the filter matches: the chosen ids plus their descendants. */
internal fun taskTypeFilterCoverage(filter: Set<String>, taskTypes: List<TaskType>): Set<String> {
    val chosen = taskTypes.filter { it.id.toString() in filter }
    return filter + taskTypes.filter { type -> chosen.any { type.isUnder(it) } }.map { it.id.toString() }
}

/** The shallowest chosen ancestor that covers [type], or null when [type] is not covered that way. */
internal fun coveringTaskType(type: TaskType, filter: Set<String>, taskTypes: List<TaskType>): TaskType? =
    taskTypes.filter { it.id.toString() in filter && type.isUnder(it) }.minByOrNull { it.name.length }

/**
 * Chooses or clears [id]. Choosing a parent absorbs any descendants chosen individually,
 * since the parent already covers them.
 */
internal fun toggleTaskTypeFilter(filter: Set<String>, id: String, taskTypes: List<TaskType>): Set<String> {
    if (id in filter) return filter - id
    val type = taskTypes.find { it.id.toString() == id } ?: return filter + id
    val absorbed = taskTypes.filter { it.isUnder(type) }.map { it.id.toString() }.toSet()
    return filter - absorbed + id
}
