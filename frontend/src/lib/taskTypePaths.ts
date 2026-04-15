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

/** True when `path` should appear while typing `query` (both canonical paths). */
function pathMatchesQuery(path: string, query: string): boolean {
  if (path === query) return true
  if (path.startsWith(`${query}/`)) return true
  if (query.startsWith(`${path}/`)) return true
  const ps = path.split('/')
  const qs = query.split('/')
  for (let i = 0; i < Math.min(ps.length, qs.length); i++) {
    const a = ps[i]!
    const b = qs[i]!
    if (!a.startsWith(b) && !b.startsWith(a)) return false
  }
  return true
}

function pathMatchScore(path: string, query: string): number {
  if (path === query) return 0
  if (path.startsWith(`${query}/`)) return 1
  if (query.startsWith(`${path}/`)) return 2
  if (path.startsWith(query)) return 3
  return 4
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
  const rows = sorted
    .filter((row) => pathMatchesQuery(row.name, canonicalQuery))
    .sort((a, b) => {
      const da = pathMatchScore(a.name, canonicalQuery)
      const db = pathMatchScore(b.name, canonicalQuery)
      if (da !== db) return da - db
      return a.name.localeCompare(b.name)
    })

  return { rows, createPath: exact ? null : canonicalQuery }
}
