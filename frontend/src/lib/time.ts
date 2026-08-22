/** 30-minute grid (matches backend). */
export const SLOT_MINUTES = 30
/** Pixel height per 30-minute row in the day timeline UI. */
export const TIMELINE_SLOT_HEIGHT_PX = 46
export const MINUTES_PER_DAY = 24 * 60

/**
 * Minute band during move-drag: ~25% of a slot before snapping to the next valid landing
 * (stable preview at slot boundaries).
 */
export const MOVE_PREVIEW_BLOCK_HYSTERESIS_MINUTES = Math.round(0.25 * SLOT_MINUTES)

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

const normMinute = (minuteFromMidnight: number) =>
  ((minuteFromMidnight % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

/**
 * Google Calendar–style time range, e.g. "4:15 – 5:45am", "9 – 10:30am", "11:30am – 1:15pm".
 * Uses an en dash between start and end; single am/pm when both times share the same half of the day.
 */
export function formatTimeRangeGcal12(startMin: number, endMin: number): string {
  const s = normMinute(startMin)
  const e = normMinute(endMin)
  const sH = Math.floor(s / 60)
  const eH = Math.floor(e / 60)
  const sPart = s % 60
  const ePart = e % 60
  const toCompact = (h24: number, min: number) => {
    const h12 = h24 % 12 === 0 ? 12 : h24 % 12
    return min === 0 ? String(h12) : `${h12}:${String(min).padStart(2, '0')}`
  }
  const sameHalf = sH < 12 === eH < 12
  const a = toCompact(sH, sPart)
  const b = toCompact(eH, ePart)
  if (sameHalf) {
    const p = sH < 12 ? 'am' : 'pm'
    return `${a} – ${b}${p}`
  }
  return `${a}${sH < 12 ? 'am' : 'pm'} – ${b}${eH < 12 ? 'am' : 'pm'}`
}

/** Hour line label for the time gutter, e.g. "4 AM" (12-hour clock). */
export function formatHourLabelGcal12(minuteFromMidnight: number): string {
  const m = normMinute(minuteFromMidnight)
  const h24 = Math.floor(m / 60)
  const h12 = h24 % 12 === 0 ? 12 : h24 % 12
  const p = h24 < 12 ? 'AM' : 'PM'
  return `${h12} ${p}`
}

/** `YYYY-MM-DD` for the calendar day of `date` in `timeZone` (IANA), matching API day strings. */
export function calendarIsoDateInTimeZone(date: Date, timeZone: string): string {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function intPart(parts: Intl.DateTimeFormatPart[], type: string): number {
  const raw = parts.find((p) => p.type === type)?.value
  if (raw === undefined) return 0
  const n = Number.parseInt(raw, 10)
  return Number.isFinite(n) ? n : 0
}

/** Minutes from local midnight in `timeZone`, with fractional seconds (0 ≤ x < 1440). */
export function minuteOfDayWithSecondsInTimeZone(date: Date, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const h = intPart(parts, 'hour')
  const m = intPart(parts, 'minute')
  const s = intPart(parts, 'second')
  return h * 60 + m + s / 60
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

/**
 * Maps pointer distance from the lane's top edge to a slot-aligned minute in the visible window.
 * Caps the slot index so coordinates above the lane (negative Y clamped to 0) cannot yield a minute
 * below `visibleStartMin`, and coordinates past the lane bottom cannot exceed the last row.
 */
export function minuteFromPointerYInVisibleLane(
  yFromLaneTop: number,
  visibleStartMin: number,
  visibleEndMin: number,
  slotHeightPx: number,
): number {
  const range = visibleEndMin - visibleStartMin
  if (range <= 0 || slotHeightPx <= 0) return visibleStartMin
  const rowCount = Math.max(1, Math.floor(range / SLOT_MINUTES))
  const maxIdx = rowCount - 1
  const idx = Math.min(maxIdx, Math.max(0, Math.floor(Math.max(0, yFromLaneTop) / slotHeightPx)))
  return visibleStartMin + idx * SLOT_MINUTES
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

/**
 * Resize limits for a draft block that is not yet in the lane list: the free gap
 * `[minStartMinute, maxEndMinute)` that fully contains `[start, end)`.
 * Falls back to full day when no gap fits (invalid or degenerate interval).
 */
export function gapBoundsForDraft(
  sameLaneBlocks: TimeBlockLike[],
  start: number,
  end: number,
): { minStartMinute: number; maxEndMinute: number } {
  if (end <= start) {
    return { minStartMinute: 0, maxEndMinute: MINUTES_PER_DAY }
  }
  const sorted = [...sameLaneBlocks].sort((a, b) => a.start_minute - b.start_minute)
  let cursor = 0
  for (const b of sorted) {
    const gapLo = cursor
    const gapHi = b.start_minute
    if (start >= gapLo && end <= gapHi) {
      return { minStartMinute: gapLo, maxEndMinute: gapHi }
    }
    cursor = Math.max(cursor, b.end_minute)
  }
  const gapLo = cursor
  const gapHi = MINUTES_PER_DAY
  if (start >= gapLo && end <= gapHi) {
    return { minStartMinute: gapLo, maxEndMinute: gapHi }
  }
  return { minStartMinute: 0, maxEndMinute: MINUTES_PER_DAY }
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
export function validStartMinuteRangesForDuration(
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

/**
 * Same-lane move preview start: stable while the pointer hovers near a blocker boundary.
 * Uses {@link resolveSameLaneMoveStart} for the next "instant" target, but only commits to that
 * target when the raw candidate crosses a threshold toward that target (see spec hysteresis).
 *
 * When the candidate is already a valid non-overlapping start, returns it (free move within a gap).
 */
export function resolveSameLaneMovePreviewStart(
  sameLaneBlocks: TimeBlockLike[],
  movingBlockId: number,
  durationMinutes: number,
  candidateStartMinutes: number,
  committedPreviewStart: number,
  hysteresisMinutes: number = MOVE_PREVIEW_BLOCK_HYSTERESIS_MINUTES,
): number {
  const committed = floorToSlotMinute(committedPreviewStart)
  const obstacles = obstaclesExcluding(sameLaneBlocks, movingBlockId)
  const maxStart = MINUTES_PER_DAY - durationMinutes

  /** Slot floor for instant-resolve; raw minute drives hysteresis edges. */
  const cSlot = floorToSlotMinute(candidateStartMinutes)

  const naive = resolveSameLaneMoveStart(
    sameLaneBlocks,
    movingBlockId,
    durationMinutes,
    cSlot,
    committed,
  )

  if (naive === committed) {
    if (isValidMoveStart(cSlot, durationMinutes, obstacles)) {
      return Math.min(cSlot, maxStart)
    }
    return committed
  }

  if (naive > committed) {
    return candidateStartMinutes >= naive - hysteresisMinutes ? Math.min(naive, maxStart) : committed
  }

  return candidateStartMinutes <= naive + hysteresisMinutes ? Math.min(naive, maxStart) : committed
}
