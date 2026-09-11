import { zonedLocalDateTimeCandidates, zonedLocalDateTimeToIso } from '../../lib/time'

export function localActivityTime(instant: string, timezone: string) {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(instant))
  const p = (name: string) => parts.find(p => p.type === name)!.value
  return `${p('year')}-${p('month')}-${p('day')}T${p('hour')}:${p('minute')}`
}
export type ActivityTimeValue = { local: string; occurrence?: 'earlier' | 'later'; original?: string }
export function activityTimeValue(instant: string, timezone: string): ActivityTimeValue {
  const local = localActivityTime(instant, timezone)
  const candidates = zonedLocalDateTimeCandidates(local, timezone)
  return { local, original: instant, occurrence: candidates.length > 1 ? (Date.parse(instant) >= Date.parse(candidates[1]) ? 'later' : 'earlier') : undefined }
}
export function resolveActivityTime(value: ActivityTimeValue, timezone: string) {
  return value.original ?? zonedLocalDateTimeToIso(value.local, timezone, value.occurrence)
}
