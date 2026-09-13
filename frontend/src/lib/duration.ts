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
