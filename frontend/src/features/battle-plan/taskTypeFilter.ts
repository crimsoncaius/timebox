import type { TaskType } from '../../lib/api'

/**
 * Battle Plan Task Type filter rules, shared with Android.
 *
 * The saved filter holds the Task Type ids the user chose directly, plus `unset`.
 * A chosen type covers its whole branch: `coding` also matches `coding/ai/evals`.
 */

export function isUnderTaskType(type: TaskType, ancestor: TaskType): boolean {
  return type.name.startsWith(`${ancestor.name}/`)
}

/** Every filter key that matches: the chosen keys plus the ids of their descendants. */
export function taskTypeFilterCoverage(filter: string[], taskTypes: TaskType[]): Set<string> {
  const chosen = taskTypes.filter((type) => filter.includes(String(type.id)))
  const covered = new Set(filter)
  for (const type of taskTypes) {
    if (chosen.some((ancestor) => isUnderTaskType(type, ancestor))) covered.add(String(type.id))
  }
  return covered
}

/** The shallowest chosen ancestor that covers `type`, or null when `type` is not covered that way. */
export function coveringTaskType(type: TaskType, filter: string[], taskTypes: TaskType[]): TaskType | null {
  return taskTypes
    .filter((ancestor) => filter.includes(String(ancestor.id)) && isUnderTaskType(type, ancestor))
    .sort((a, b) => a.name.length - b.name.length)[0] ?? null
}

/**
 * Chooses or clears `key`. Choosing a parent absorbs any descendants chosen individually,
 * since the parent already covers them.
 */
export function toggleTaskTypeFilter(filter: string[], key: string, taskTypes: TaskType[]): string[] {
  if (filter.includes(key)) return filter.filter((value) => value !== key)
  const type = taskTypes.find((row) => String(row.id) === key)
  if (!type) return [...filter, key]
  const absorbed = new Set(taskTypes.filter((row) => isUnderTaskType(row, type)).map((row) => String(row.id)))
  return [...filter.filter((value) => !absorbed.has(value)), key]
}
