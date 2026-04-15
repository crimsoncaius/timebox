/** 30-minute grid (matches backend). */
export const SLOT_MINUTES = 30
export const MINUTES_PER_DAY = 24 * 60

/** Calendar math in UTC to match API `YYYY-MM-DD` dates. */
export function addDaysIso(iso: string, delta: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return iso
  const dt = new Date(Date.UTC(y, m - 1, d))
  dt.setUTCDate(dt.getUTCDate() + delta)
  return dt.toISOString().slice(0, 10)
}

/** First day of the month containing `iso` (`YYYY-MM-DD`), UTC. */
export function firstOfMonthIso(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return iso
  return `${y}-${String(m).padStart(2, '0')}-01`
}

/** Move to the first day of the month `delta` months from the month containing `iso`. */
export function addMonthsIso(iso: string, delta: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return iso
  const dt = new Date(Date.UTC(y, m - 1 + delta, 1))
  return dt.toISOString().slice(0, 10)
}

export type MonthGridCell = { iso: string; inMonth: boolean }

/**
 * 42 cells (6×7), week starting Sunday, UTC calendar dates matching API `YYYY-MM-DD`.
 */
export function monthGridForIso(iso: string): MonthGridCell[] {
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return []
  const first = new Date(Date.UTC(y, m - 1, 1))
  const startWeekday = first.getUTCDay()
  const gridStart = new Date(Date.UTC(y, m - 1, 1))
  gridStart.setUTCDate(gridStart.getUTCDate() - startWeekday)
  const out: MonthGridCell[] = []
  for (let i = 0; i < 42; i++) {
    const cell = new Date(gridStart)
    cell.setUTCDate(cell.getUTCDate() + i)
    const cellIso = cell.toISOString().slice(0, 10)
    const cy = cell.getUTCFullYear()
    const cm = cell.getUTCMonth() + 1
    out.push({ iso: cellIso, inMonth: cy === y && cm === m })
  }
  return out
}

/** Month + year label for the month containing `iso` (UTC). */
export function monthYearLabelForIso(iso: string, locale?: string): string {
  const first = firstOfMonthIso(iso)
  const [y, mo] = first.split('-').map(Number)
  if (!y || !mo) return ''
  const dt = new Date(Date.UTC(y, mo - 1, 1))
  return dt.toLocaleDateString(locale ?? undefined, {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

/** Short weekday labels for a Sunday-first row (UTC convention). */
export const WEEKDAY_LABELS_SUN_FIRST = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'] as const

/** 24-hour label for minute 0–1439 (e.g. 08:00, 08:30). */
export function formatMinuteLabel24(minuteFromMidnight: number): string {
  const m = ((minuteFromMidnight % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
  const h = Math.floor(m / 60)
  const min = m % 60
  return `${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}`
}

export function snapToSlot(minute: number): number {
  return Math.round(minute / SLOT_MINUTES) * SLOT_MINUTES
}

export function visibleMinuteRange(day: {
  show_full_day: boolean
  start_hour: number
  end_hour: number
}): { start: number; end: number } {
  if (day.show_full_day) {
    return { start: 0, end: MINUTES_PER_DAY }
  }
  return {
    start: day.start_hour * 60,
    end: day.end_hour * 60,
  }
}

export function slotCountInRange(startMin: number, endMin: number): number {
  return Math.max(0, (endMin - startMin) / SLOT_MINUTES)
}

/** Same-lane blocks sorted by start; used to clamp resize so edges cannot cross neighbors. */
export interface TimeBlockLike {
  id: number
  start_minute: number
  end_minute: number
}

/**
 * Hard limits for resizing a block without overlapping another block in the same lane.
 * - Start edge cannot go below previous block's end (or 0).
 * - End edge cannot go above next block's start (or end of day).
 */
export function sameLaneResizeBounds(
  sortedSameLaneBlocks: TimeBlockLike[],
  blockId: number,
): { minStartMinute: number; maxEndMinute: number } {
  const sorted = [...sortedSameLaneBlocks].sort((a, b) => a.start_minute - b.start_minute)
  const idx = sorted.findIndex((b) => b.id === blockId)
  if (idx === -1) {
    return { minStartMinute: 0, maxEndMinute: MINUTES_PER_DAY }
  }
  const minStartMinute = idx > 0 ? sorted[idx - 1].end_minute : 0
  const maxEndMinute = idx < sorted.length - 1 ? sorted[idx + 1].start_minute : MINUTES_PER_DAY
  return { minStartMinute, maxEndMinute }
}

/** Floor to slot grid (matches timeline Y → minute mapping). */
export function floorToSlotMinute(minute: number): number {
  const m = Math.floor(minute / SLOT_MINUTES) * SLOT_MINUTES
  return Math.max(0, m)
}

function intervalsOverlapHalfOpen(
  aStart: number,
  aEnd: number,
  bStart: number,
  bEnd: number,
): boolean {
  return aStart < bEnd && bStart < aEnd
}

function obstaclesExcluding(
  sortedSameLaneBlocks: TimeBlockLike[],
  movingBlockId: number,
): TimeBlockLike[] {
  return [...sortedSameLaneBlocks]
    .filter((b) => b.id !== movingBlockId)
    .sort((a, b) => a.start_minute - b.start_minute)
}

/** Inclusive [lo, hi] of valid start minutes for a block of fixed duration in gaps between obstacles. */
function validStartMinuteRangesForDuration(
  obstaclesSorted: TimeBlockLike[],
  durationMinutes: number,
): Array<{ lo: number; hi: number }> {
  const ranges: Array<{ lo: number; hi: number }> = []
  let cursor = 0
  for (const o of obstaclesSorted) {
    const gapEnd = o.start_minute
    const lo = cursor
    const hi = gapEnd - durationMinutes
    if (lo <= hi) ranges.push({ lo, hi })
    cursor = o.end_minute
  }
  const loLast = cursor
  const hiLast = MINUTES_PER_DAY - durationMinutes
  if (loLast <= hiLast) ranges.push({ lo: loLast, hi: hiLast })
  return ranges
}

function isValidMoveStart(
  start: number,
  durationMinutes: number,
  obstaclesSorted: TimeBlockLike[],
): boolean {
  if (start < 0 || start % SLOT_MINUTES !== 0) return false
  const end = start + durationMinutes
  if (end > MINUTES_PER_DAY) return false
  for (const o of obstaclesSorted) {
    if (intervalsOverlapHalfOpen(start, end, o.start_minute, o.end_minute)) return false
  }
  return true
}

function pickMinValidStartAtOrAfter(
  c: number,
  ranges: Array<{ lo: number; hi: number }>,
): number | null {
  let best: number | null = null
  for (const r of ranges) {
    const s = Math.max(r.lo, c)
    if (s <= r.hi) {
      if (best === null || s < best) best = s
    }
  }
  return best
}

function pickMaxValidStartAtOrBefore(
  c: number,
  ranges: Array<{ lo: number; hi: number }>,
): number | null {
  let best: number | null = null
  for (const r of ranges) {
    const s = Math.min(r.hi, c)
    if (s >= r.lo) {
      if (best === null || s > best) best = s
    }
  }
  return best
}

function pickNearestValidStart(
  c: number,
  ranges: Array<{ lo: number; hi: number }>,
): number | null {
  let best: number | null = null
  let bestDist = Infinity
  for (const r of ranges) {
    if (c < r.lo) {
      const s = r.lo
      const d = s - c
      if (d < bestDist) {
        bestDist = d
        best = s
      }
    } else if (c > r.hi) {
      const s = r.hi
      const d = c - s
      if (d < bestDist) {
        bestDist = d
        best = s
      }
    } else {
      return c
    }
  }
  return best
}

/**
 * Resolves a valid start minute for moving a block in the same lane without overlapping others.
 * Candidate and previous resolved values should be on the slot grid (use floorToSlotMinute).
 * When the candidate would overlap, picks the nearest valid start in the direction of
 * (candidate − previousResolved); if equal, picks the nearest valid start overall.
 */
export function resolveSameLaneMoveStart(
  sameLaneBlocks: TimeBlockLike[],
  movingBlockId: number,
  durationMinutes: number,
  candidateStart: number,
  previousResolvedStart: number,
): number {
  const c = floorToSlotMinute(candidateStart)
  const prev = floorToSlotMinute(previousResolvedStart)
  const obstacles = obstaclesExcluding(sameLaneBlocks, movingBlockId)
  const ranges = validStartMinuteRangesForDuration(obstacles, durationMinutes)

  const maxStart = MINUTES_PER_DAY - durationMinutes
  if (ranges.length === 0) {
    return Math.min(Math.max(prev, 0), maxStart)
  }

  if (isValidMoveStart(c, durationMinutes, obstacles)) {
    return Math.min(c, maxStart)
  }

  const dir = c === prev ? 0 : c > prev ? 1 : -1
  if (dir > 0) {
    const picked = pickMinValidStartAtOrAfter(c, ranges)
    if (picked !== null) return Math.min(picked, maxStart)
    const fallbackMax = Math.max(...ranges.map((r) => r.hi))
    return Math.min(fallbackMax, maxStart)
  }
  if (dir < 0) {
    const picked = pickMaxValidStartAtOrBefore(c, ranges)
    if (picked !== null) return Math.min(picked, maxStart)
    const fallbackMin = Math.min(...ranges.map((r) => r.lo))
    return Math.min(fallbackMin, maxStart)
  }
  const nearest = pickNearestValidStart(c, ranges)
  return Math.min(nearest ?? ranges[0]!.lo, maxStart)
}
