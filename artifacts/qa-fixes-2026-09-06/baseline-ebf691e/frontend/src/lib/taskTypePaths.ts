import type { TaskType } from './api'

export function canonicalizeTaskTypePathInput(input: string): string | null {
  const raw = input.trim()
  if (!raw) return null
  const rawSegments = raw.split('/')
  if (rawSegments.some((segment) => segment.trim().length === 0)) return null
  return rawSegments.map((segment) => segment.trim().toLowerCase()).join('/')
}

export function formatTaskTypePathParts(path: string): {
  ancestorsLabel: string
  leafLabel: string
  fullLabel: string
} {
  const segments = path.split('/')
  const leafLabel = segments[segments.length - 1] ?? path
  const ancestorsLabel = segments.slice(0, -1).join(' / ')
  return { fullLabel: path, leafLabel, ancestorsLabel }
}

/** First path segment, used to group related types (e.g. `coding` for `coding/ai`). */
export function taskTypeRootSegment(path: string): string {
  const s = path.split('/')[0]
  return s?.trim() ? s : path
}

/** 0 for a single segment, 1 for `a/b`, etc. */
export function pathDepth(path: string): number {
  return Math.max(0, path.split('/').length - 1)
}

/**
 * Sort by full path, then bucket by top-level segment for grouped UI.
 */
export function groupTaskTypesByRoot(types: TaskType[]): { root: string; items: TaskType[] }[] {
  const sorted = [...types].sort((a, b) => a.name.localeCompare(b.name))
  const roots = [...new Set(sorted.map((t) => taskTypeRootSegment(t.name)))].sort((a, b) =>
    a.localeCompare(b),
  )
  return roots.map((root) => ({
    root,
    items: sorted.filter((t) => taskTypeRootSegment(t.name) === root),
  }))
}

function segmentPrefixMatch(a: string, b: string): boolean {
  return a.startsWith(b) || b.startsWith(a)
}

/** Same segment list length; each pair must satisfy two-way prefix match. */
function segmentsAlign(pathSegments: readonly string[], querySegments: readonly string[]): boolean {
  if (pathSegments.length !== querySegments.length) return false
  for (let i = 0; i < querySegments.length; i++) {
    const a = pathSegments[i]!
    const b = querySegments[i]!
    if (!segmentPrefixMatch(a, b)) return false
  }
  return true
}

/**
 * Minimum segment index where `query` aligns with a contiguous slice of `path`, or null if none.
 * Only defined when `path` and `query` are non-empty canonical paths.
 */
function minQueryAlignmentStart(path: string, query: string): number | null {
  const ps = path.split('/')
  const qs = query.split('/')
  if (qs.length > ps.length) return null
  for (let start = 0; start <= ps.length - qs.length; start++) {
    if (segmentsAlign(ps.slice(start, start + qs.length), qs)) return start
  }
  return null
}

/** True when `path` should appear while typing `query` (both canonical paths). */
function pathMatchesQuery(path: string, query: string): boolean {
  if (path === query) return true
  if (path.startsWith(`${query}/`)) return true
  if (query.startsWith(`${path}/`)) return true
  if (minQueryAlignmentStart(path, query) !== null) return true
  const ps = path.split('/')
  const qs = query.split('/')
  const k = Math.min(ps.length, qs.length)
  for (let i = 0; i < k; i++) {
    if (!segmentPrefixMatch(ps[i]!, qs[i]!)) return false
  }
  return true
}

function pathMatchScore(path: string, query: string): number {
  if (path === query) return 0
  if (path.startsWith(`${query}/`)) return 1
  if (query.startsWith(`${path}/`)) return 2
  if (path.startsWith(query)) return 3
  const start = minQueryAlignmentStart(path, query)
  if (start !== null) return start === 0 ? 4 : 5
  const ps = path.split('/')
  const qs = query.split('/')
  const k = Math.min(ps.length, qs.length)
  for (let i = 0; i < k; i++) {
    if (!segmentPrefixMatch(ps[i]!, qs[i]!)) return 6
  }
  return 4
}

/**
 * Returns task types that match the path-aware query, with the same ordering as combobox suggestions.
 * Empty or invalid query returns all types sorted by name.
 */
export function filterTaskTypesByQuery(taskTypes: TaskType[], query: string): TaskType[] {
  const canonicalQuery = canonicalizeTaskTypePathInput(query)
  const sorted = [...taskTypes].sort((a, b) => a.name.localeCompare(b.name))
  if (!canonicalQuery) {
    return sorted
  }
  return sorted
    .filter((row) => pathMatchesQuery(row.name, canonicalQuery))
    .sort((a, b) => {
      const da = pathMatchScore(a.name, canonicalQuery)
      const db = pathMatchScore(b.name, canonicalQuery)
      if (da !== db) return da - db
      return a.name.localeCompare(b.name)
    })
}

export function buildTaskTypeSuggestions(taskTypes: TaskType[], query: string): {
  rows: TaskType[]
  createPath: string | null
} {
  const canonicalQuery = canonicalizeTaskTypePathInput(query)
  const sorted = [...taskTypes].sort((a, b) => a.name.localeCompare(b.name))

  if (!canonicalQuery) {
    return { rows: sorted, createPath: null }
  }

  const exact = sorted.some((row) => row.name === canonicalQuery)
  const rows = filterTaskTypesByQuery(taskTypes, query)

  return { rows, createPath: exact ? null : canonicalQuery }
}
