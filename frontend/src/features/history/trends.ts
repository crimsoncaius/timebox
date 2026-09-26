export type { TrendNode, TrendsReport } from '../../lib/api/trends'

export function trendDuration(seconds: number) {
  if (seconds > 0 && seconds < 60) return '<1m'
  const minutes = Math.floor(seconds / 60)
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`
}

export function shiftTrendRange(start: string, period: string, step: number) {
  const date = new Date(`${start}T00:00:00Z`)
  if (period === 'month') date.setUTCMonth(date.getUTCMonth() + step, 1)
  else date.setUTCDate(date.getUTCDate() + step * (period === 'week' ? 7 : 1))
  return date.toISOString().slice(0, 10)
}

export function canAdvanceTrendRange(start: string, period: string, today: string) {
  return shiftTrendRange(start, period, 1) <= today
}

/** The Trends range a Calendar highlight was drilled from. */
export type TrendRange = { period: string; start: string; end: string }

const SHORT_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const LONG_MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

export function trendRangeDayCount({ start, end }: TrendRange) {
  return Math.round((Date.parse(`${end}T00:00:00Z`) - Date.parse(`${start}T00:00:00Z`)) / 86_400_000) + 1
}

/** "Week · Sep 21 – 27", "Month · September 2026", "Custom range · Sep 28 – Oct 4". */
export function trendRangeLabel(range: TrendRange) {
  const [sy, sm, sd] = range.start.split('-').map(Number)
  const [ey, em, ed] = range.end.split('-').map(Number)
  const kind = range.period === 'custom' ? 'Custom range' : range.period[0].toUpperCase() + range.period.slice(1)
  const dates = range.period === 'month' ? `${LONG_MONTHS[sm - 1]} ${sy}`
    : range.start === range.end ? `${SHORT_MONTHS[sm - 1]} ${sd}`
    : sy !== ey ? `${SHORT_MONTHS[sm - 1]} ${sd}, ${sy} – ${SHORT_MONTHS[em - 1]} ${ed}, ${ey}`
    : sm === em ? `${SHORT_MONTHS[sm - 1]} ${sd} – ${ed}`
    : `${SHORT_MONTHS[sm - 1]} ${sd} – ${SHORT_MONTHS[em - 1]} ${ed}`
  return `${kind} · ${dates}`
}
