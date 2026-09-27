function durationUnits(seconds: number, includeSeconds: boolean): string {
  const units: [number, string][] = [
    [Math.floor(seconds / 86400), 'day'],
    [Math.floor(seconds / 3600) % 24, 'hour'],
    [Math.floor(seconds / 60) % 60, 'min'],
    ...(includeSeconds ? [[seconds % 60, 'sec'] as [number, string]] : []),
  ]
  return units.filter(([value]) => value > 0)
    .map(([value, unit]) => `${value} ${unit}${value === 1 ? '' : 's'}`)
    .join(' ') || (includeSeconds ? '0 secs' : '0 mins')
}

/** Whole-minute elapsed durations, using fixed 24-hour days. */
export function formatDuration(minutes: number): string {
  return durationUnits(minutes * 60, false)
}

/**
 * Running Time of the Current Activity. Focus Mode always shows seconds;
 * the compact control shows seconds only until the first whole minute.
 */
export function formatRunningTime(seconds: number, alwaysSeconds: boolean): string {
  const total = Math.max(0, Math.floor(seconds))
  return durationUnits(total, alwaysSeconds || total < 60)
}

/** Compact Block Duration label, e.g. "45m", "2h", "1h 30m"; whole minutes, rounded down. */
export function formatBlockDuration(minutes: number): string {
  const total = Math.max(0, Math.floor(minutes))
  const h = Math.floor(total / 60)
  const m = total % 60
  if (h === 0) return `${m}m`
  return m === 0 ? `${h}h` : `${h}h ${m}m`
}
