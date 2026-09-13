import type { DayRead } from './api'
import { minuteOfDayWithSecondsInTimeZone, SLOT_MINUTES, visibleMinuteRange } from './time'

/** Historical corrections cannot include time that has not occurred. */
export function actualPlacementEnd(day: DayRead): number {
  if (day.date < day.meta.today) return visibleMinuteRange(day).end
  if (day.date > day.meta.today) return 0
  return Math.min(visibleMinuteRange(day).end,
    Math.floor(minuteOfDayWithSecondsInTimeZone(new Date(day.meta.server_now_iso), day.meta.timezone)))
}

export const NO_NEARBY_BLOCK_SPACE = 'No available space in this day'

type OccupiedRange = { start_minute: number; end_minute: number }

/** Closest full-duration placement; exact boundaries are valid and equal distances prefer later. */
export function nearestBlockStart(
  occupied: OccupiedRange[],
  intendedStart: number,
  duration: number,
  visibleStart: number,
  visibleEnd: number,
  minimumDuration = SLOT_MINUTES,
): number | null {
  if (duration < minimumDuration || duration > visibleEnd - visibleStart) return null
  let nearest: number | null = null
  let gapStart = visibleStart
  const considerGap = (end: number) => {
    if (end - gapStart < duration) return
    const candidate = Math.max(gapStart, Math.min(intendedStart, end - duration))
    const distance = Math.abs(candidate - intendedStart)
    if (nearest === null || distance < Math.abs(nearest - intendedStart)
      || (distance === Math.abs(nearest - intendedStart) && candidate > nearest)) nearest = candidate
  }
  for (const block of [...occupied].sort((a, b) => a.start_minute - b.start_minute)) {
    if (block.end_minute <= visibleStart || block.start_minute >= visibleEnd) continue
    considerGap(block.start_minute)
    gapStart = Math.max(gapStart, block.end_minute)
  }
  considerGap(visibleEnd)
  return nearest
}

/** Validate the preview as-is; release must never pick a replacement destination. */
export function blockRangeAvailable(
  occupied: OccupiedRange[], start: number, end: number, visibleStart: number, visibleEnd: number,
  minimumDuration = SLOT_MINUTES,
): boolean {
  return end - start >= minimumDuration && start >= visibleStart && end <= visibleEnd
    && !occupied.some(block => start < block.end_minute && block.start_minute < end)
}
