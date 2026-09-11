import type { DayRead } from '../../lib/api'
import { addDaysIso, zonedLocalDateTimeCandidates } from '../../lib/time'
import { dateInTimeZone } from '../../lib/battlePlan'
import { minuteInTimeZone } from '../today/workModeExecution'
import type { ActivitySnapshot } from './activityRepository'

/** Project the durable journal into the existing Day, including offline history. */
export function activityDay(date: string, snapshot: ActivitySnapshot, day: DayRead | null, now: number): DayRead {
  const zone = snapshot.reporting_timezone ?? day?.meta.timezone ?? 'UTC'
  const midnight = (date: string) => {
    // Some zones advance at midnight. A calendar day starts at its first valid minute.
    for (let minute = 0; minute < 1440; minute++) {
      const choices = zonedLocalDateTimeCandidates(`${date}T${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`, zone)
      if (choices.length) return Date.parse(choices[0])
    }
    return Date.parse(zonedLocalDateTimeCandidates(`${addDaysIso(date, 1)}T00:00`, zone)[0])
  }
  const start = midnight(date), end = midnight(addDaysIso(date, 1))
  const actual_blocks = snapshot.records.filter(r => Date.parse(r.start_at) < end && Date.parse(r.end_at ?? new Date(now).toISOString()) > start).map(actual_block => {
    const a = Math.max(start, Date.parse(actual_block.start_at)), b = Math.min(end, Date.parse(actual_block.end_at ?? new Date(now).toISOString()))
    return { actual_block, date, start_minute: a === start ? 0 : minuteInTimeZone(new Date(a).toISOString(), zone), end_minute: b === end ? 1440 : minuteInTimeZone(new Date(b).toISOString(), zone), duration_minutes: Math.floor((b - a) / 60000), day_length_minutes: (end - start) / 60000 }
  })
  return { id: 0, date, start_hour: 0, end_hour: 24, show_full_day: true, created_at: snapshot.server_at, updated_at: snapshot.server_at, time_blocks: [], ...(day?.date === date ? day : {}),
    actual_blocks, actual_minutes: actual_blocks.reduce((sum, p) => sum + p.duration_minutes, 0), meta: { timezone: zone, today: dateInTimeZone(new Date(now).toISOString(), zone), server_now_iso: new Date(now).toISOString() } }
}
