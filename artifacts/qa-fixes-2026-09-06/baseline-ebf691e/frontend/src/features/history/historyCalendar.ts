import type { DayListItem } from '../../lib/api'

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

/** `month` is 1–12. Returns `YYYY-MM-DD` in UTC. */
function isoDateUTC(year: number, month: number, day: number): string {
  return `${year}-${pad2(month)}-${pad2(day)}`
}

/** Move calendar month by `delta` (−1 / +1). `month` is 1–12. */
export function shiftMonth(year: number, month: number, delta: number): { year: number; month: number } {
  const d = new Date(Date.UTC(year, month - 1 + delta, 1))
  return { year: d.getUTCFullYear(), month: d.getUTCMonth() + 1 }
}

export type MonthGridCell = {
  iso: string
  dayOfMonth: number
  inMonth: boolean
}

/**
 * Six rows × seven columns (Mon–Sun), UTC. Leading/trailing cells belong to adjacent months
 * (`inMonth` false).
 */
export function buildMonthGridUTC(year: number, month: number): MonthGridCell[] {
  const firstMs = Date.UTC(year, month - 1, 1)
  const dow = new Date(firstMs).getUTCDay()
  const mondayOffset = (dow + 6) % 7
  const startMs = firstMs - mondayOffset * 86_400_000
  const cells: MonthGridCell[] = []
  for (let i = 0; i < 42; i++) {
    const t = new Date(startMs + i * 86_400_000)
    const y = t.getUTCFullYear()
    const m = t.getUTCMonth() + 1
    const d = t.getUTCDate()
    cells.push({
      iso: isoDateUTC(y, m, d),
      dayOfMonth: d,
      inMonth: y === year && m === month,
    })
  }
  return cells
}

export function daysByDate(items: DayListItem[]): Map<string, DayListItem> {
  const m = new Map<string, DayListItem>()
  for (const row of items) {
    m.set(row.date, row)
  }
  return m
}

/** e.g. `June 2026`, UTC month label for the grid header. */
export function formatMonthYearUTC(year: number, month: number): string {
  const dt = new Date(Date.UTC(year, month - 1, 1))
  return dt.toLocaleDateString('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' })
}
