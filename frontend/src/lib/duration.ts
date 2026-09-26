/** Whole-minute elapsed durations, using fixed 24-hour days. */
export function formatDuration(minutes: number): string {
  const units: [number, string][] = [
    [Math.floor(minutes / 1440), 'day'],
    [Math.floor(minutes / 60) % 24, 'hour'],
    [minutes % 60, 'min'],
  ]
  return units.filter(([value]) => value > 0)
    .map(([value, unit]) => `${value} ${unit}${value === 1 ? '' : 's'}`)
    .join(' ') || '0 mins'
}

/** Compact Block Duration label, e.g. "45m", "2h", "1h 30m"; whole minutes, rounded down. */
export function formatBlockDuration(minutes: number): string {
  const total = Math.max(0, Math.floor(minutes))
  const h = Math.floor(total / 60)
  const m = total % 60
  if (h === 0) return `${m}m`
  return m === 0 ? `${h}h` : `${h}h ${m}m`
}
