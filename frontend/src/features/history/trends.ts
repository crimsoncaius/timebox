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

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

/**
 * The range heading beside the arrows: "Sun, Sep 27", "Sep 21 – 27", "September 2026"; other years add the year.
 * A custom range spanning years names both, since it can run longer than a year.
 */
export function trendRangeHeading(period: string, start: string, end: string, today: string) {
  const [sy, sm, sd] = start.split('-').map(Number)
  const [ey, em, ed] = end.split('-').map(Number)
  const year = ey !== Number(today.slice(0, 4)) ? `, ${ey}` : ''
  if (period === 'month') return `${LONG_MONTHS[sm - 1]} ${sy}`
  if (start === end) return `${WEEKDAYS[new Date(`${start}T00:00:00Z`).getUTCDay()]}, ${SHORT_MONTHS[sm - 1]} ${sd}${year}`
  if (period === 'custom' && sy !== ey) return `${SHORT_MONTHS[sm - 1]} ${sd}, ${sy} – ${SHORT_MONTHS[em - 1]} ${ed}, ${ey}`
  return sm === em && sy === ey ? `${SHORT_MONTHS[sm - 1]} ${sd} – ${ed}${year}` : `${SHORT_MONTHS[sm - 1]} ${sd} – ${SHORT_MONTHS[em - 1]} ${ed}${year}`
}

/** "Sep 14", or "Sep 14, 2025" outside Today's year. */
export function trendDayLabel(iso: string, today: string) {
  const [y, m, d] = iso.split('-').map(Number)
  return `${SHORT_MONTHS[m - 1]} ${d}${y !== Number(today.slice(0, 4)) ? `, ${y}` : ''}`
}

export function dayCountLabel(days: number) {
  return `${days} ${days === 1 ? 'day' : 'days'}`
}

/** A custom range being chosen on the calendar: the last day is null until it is picked. */
export type RangeDraft = { start: string; end: string | null }

/** A tap starts a new range, unless it picks the last day of one already started. */
export function nextRangeDraft(draft: RangeDraft, iso: string): RangeDraft {
  if (draft.end !== null || iso < draft.start) return { start: iso, end: null }
  return { start: draft.start, end: iso }
}

/**
 * Whether Trends shows the range containing Today, decided from the anchor so it holds while a report is
 * loading: no anchor is the current range; otherwise the anchor's Monday week or month must contain Today.
 */
export function showsCurrentTrendRange(period: string, anchor: string, today: string) {
  if (!anchor) return true
  if (period === 'day') return anchor === today
  if (period === 'month') return anchor.slice(0, 7) === today.slice(0, 7)
  if (period === 'week') return mondayOf(anchor) === mondayOf(today)
  return false
}

function mondayOf(iso: string) {
  const date = new Date(`${iso}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() - (date.getUTCDay() + 6) % 7)
  return date.toISOString().slice(0, 10)
}
