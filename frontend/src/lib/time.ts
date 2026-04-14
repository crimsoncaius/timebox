/** 30-minute grid (matches backend). */
export const SLOT_MINUTES = 30
export const MINUTES_PER_DAY = 24 * 60

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
