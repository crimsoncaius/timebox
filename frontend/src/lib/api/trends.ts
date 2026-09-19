export type TrendNode = {
  path: string
  name: string
  duration_seconds: number
  direct_seconds: number
  days: Record<string, number>
  direct_days: Record<string, number>
  children: TrendNode[]
}

export type TrendsReport = {
  start: string
  end: string
  today: string
  timezone: string
  captured_at: string
  duration_seconds: number
  types: TrendNode[]
}
