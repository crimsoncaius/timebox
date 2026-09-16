import type { TaskType } from './api'

const UNSPECIFIED = 'unspecified'
const WHITESPACE_RUN = /\s+/g

export function canonicalizeTaskTypePathInput(input: string): string | null {
  const segments = input
    .split('/')
    .map((segment) => segment.trim().toLowerCase().replace(WHITESPACE_RUN, ' '))
    .filter((segment) => segment.length > 0)
  if (segments.length === 0) return null
  return segments.join('/')
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

export function taskTypePathPrefixes(path: string): string[] {
  const segments = path.split('/')
  return segments.map((_, index) => segments.slice(0, index + 1).join('/'))
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

function canonicalName(name: string): string {
  return canonicalizeTaskTypePathInput(name) ?? name.trim().toLowerCase()
}

function usageCount(type: TaskType): number {
  return type.usage_count ?? 0
}

function rankEmptyQuery(taskTypes: TaskType[], currentTypeId?: number | null): TaskType[] {
  const current = currentTypeId == null ? [] : taskTypes.filter((type) => type.id === currentTypeId)
  const rest = taskTypes.filter((type) => type.id !== currentTypeId)
  const unspecified = rest.filter((type) => type.name === UNSPECIFIED)
  const ranked = rest
    .filter((type) => type.name !== UNSPECIFIED)
    .sort((a, b) => {
      const usage = usageCount(b) - usageCount(a)
      if (usage !== 0) return usage
      return a.name.localeCompare(b.name)
    })
  return [...current, ...ranked, ...unspecified]
}

/**
 * Picker ranking: empty query pins the current type, then Block usage, then name,
 * with `unspecified` last. Typed queries use path-match score, then name.
 */
export function rankTaskTypes(
  taskTypes: TaskType[],
  query: string,
  currentTypeId?: number | null,
): TaskType[] {
  const canonicalQuery = canonicalizeTaskTypePathInput(query)
  if (!canonicalQuery) return rankEmptyQuery(taskTypes, currentTypeId)
  return taskTypes
    .filter((row) => pathMatchesQuery(canonicalName(row.name), canonicalQuery))
    .sort((a, b) => {
      const score = pathMatchScore(canonicalName(a.name), canonicalQuery) - pathMatchScore(canonicalName(b.name), canonicalQuery)
      if (score !== 0) return score
      return a.name.localeCompare(b.name)
    })
}

/**
 * Returns task types that match the path-aware query.
 * Empty query returns all types sorted by name (Task Types page search).
 */
export function filterTaskTypesByQuery(taskTypes: TaskType[], query: string): TaskType[] {
  const canonicalQuery = canonicalizeTaskTypePathInput(query)
  const sorted = [...taskTypes].sort((a, b) => a.name.localeCompare(b.name))
  if (!canonicalQuery) return sorted
  return rankTaskTypes(taskTypes, query)
}

export function buildTaskTypeSuggestions(
  taskTypes: TaskType[],
  query: string,
  currentTypeId?: number | null,
): {
  rows: TaskType[]
  createPath: string | null
} {
  const canonicalQuery = canonicalizeTaskTypePathInput(query)
  const rows = rankTaskTypes(taskTypes, query, currentTypeId)
  if (!canonicalQuery) return { rows, createPath: null }
  const exact = taskTypes.some((row) => canonicalName(row.name) === canonicalQuery)
  return { rows, createPath: exact ? null : canonicalQuery }
}

export type CreateAncestorHint = {
  lead: string
  path: string
  tail?: string
}

export function createAncestorHint(
  taskTypes: TaskType[],
  canonicalPath: string,
): CreateAncestorHint | null {
  const ancestors = taskTypePathPrefixes(canonicalPath).slice(0, -1)
  if (ancestors.length === 0) return null
  const existing = new Set(taskTypes.map((type) => canonicalName(type.name)))
  const missing = ancestors.filter((path) => !existing.has(path))
  if (missing.length === 0) {
    return { lead: 'Adds under existing ', path: ancestors[ancestors.length - 1]! }
  }
  return { lead: 'Also creates ', path: missing.join(', ') }
}
