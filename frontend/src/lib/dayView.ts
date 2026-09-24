import type { DayRead } from './api'

/** Wall-clock grids cannot represent a repeated/skipped hour faithfully. */
export const needsElapsedDayView = (day: DayRead) => day.actual_blocks.some(p => (p.day_length_minutes ?? 1440) !== 1440)

/** Capture before changing scale; restore against the new layout, including browser scroll anchoring. */
export function captureTimelineCentre(timeline: HTMLElement | null): () => void {
  const lane = timeline?.querySelector<HTMLElement>('[data-day-lane="planned"]')
  if (!lane) return () => {}
  const before = lane.getBoundingClientRect()
  const top = Math.max(0, before.top)
  const bottom = Math.min(window.innerHeight, before.bottom)
  if (before.height <= 0 || bottom <= top) return () => {}
  const centre = (top + bottom) / 2
  const fraction = (centre - before.top) / before.height
  return () => {
    if (!lane.isConnected) return
    const after = lane.getBoundingClientRect()
    window.scrollBy({ top: after.top + fraction * after.height - centre, behavior: 'instant' })
  }
}

/** Place the Now Line one-third down the visible timeline, favoring upcoming work. */
export function nowLineScrollDelta(lineTop: number, timelineTop: number, viewportHeight: number) {
  const visibleTop = Math.max(0, timelineTop)
  const visibleHeight = Math.max(0, viewportHeight - visibleTop)
  if (visibleHeight <= 0) return 0
  return lineTop - (visibleTop + visibleHeight / 3)
}
