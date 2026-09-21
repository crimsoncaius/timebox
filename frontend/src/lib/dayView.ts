import type { DayRead } from './api'

/** Wall-clock grids cannot represent a repeated/skipped hour faithfully. */
export const needsElapsedDayView = (day: DayRead) => day.actual_blocks.some(p => (p.day_length_minutes ?? 1440) !== 1440)

/** Place the Now Line one-third down the visible timeline, favoring upcoming work. */
export function nowLineScrollDelta(lineTop: number, timelineTop: number, viewportHeight: number) {
  const visibleTop = Math.max(0, timelineTop)
  const visibleHeight = Math.max(0, viewportHeight - visibleTop)
  if (visibleHeight <= 0) return 0
  return lineTop - (visibleTop + visibleHeight / 3)
}
