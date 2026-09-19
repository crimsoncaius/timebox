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
