import { describe, expect, it } from 'vitest'
import { addCalendarDays, dateInTimeZone, deadlineBadge } from './battlePlan'

describe('deadlineBadge', () => {
  const now = '2026-08-15T16:30:00Z'
  const timezone = 'Asia/Singapore'
  const shortDate = (value: string) => new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${value}T12:00:00Z`))

  it.each([
    [{ deadline_date: '2026-08-15', deadline_at: null, overdue: true }, `Overdue · ${shortDate('2026-08-15')}`, 'overdue'],
    [{ deadline_date: '2026-08-16', deadline_at: null, overdue: false }, 'Today', 'today'],
    [{ deadline_date: '2026-08-17', deadline_at: null, overdue: false }, 'Tomorrow', 'tomorrow'],
    [{ deadline_date: '2026-08-18', deadline_at: null, overdue: false }, 'Tuesday', 'upcoming'],
    [{ deadline_date: '2026-08-31', deadline_at: null, overdue: false }, shortDate('2026-08-31'), 'later'],
  ])('formats %o as %s', (task, label, tone) => {
    expect(deadlineBadge(task, now, timezone)).toEqual({ label, tone })
  })

  it('includes a configured-timezone time for timed deadlines across a day boundary', () => {
    const time = new Intl.DateTimeFormat(undefined, {
      hour: 'numeric',
      minute: '2-digit',
      timeZone: timezone,
    }).format(new Date('2026-08-16T17:45:00Z'))
    expect(deadlineBadge(
      { deadline_date: null, deadline_at: '2026-08-16T17:45:00Z', overdue: false },
      now,
      timezone,
    )).toEqual({ label: `Tomorrow · ${time}`, tone: 'tomorrow' })
  })

  it('returns no badge without a deadline', () => {
    expect(deadlineBadge(
      { deadline_date: null, deadline_at: null, overdue: false },
      now,
      timezone,
    )).toBeNull()
  })
})

describe('calendar helpers', () => {
  it('derives the app date from the server instant and adds calendar days', () => {
    expect(dateInTimeZone('2026-08-15T16:30:00Z', 'Asia/Singapore')).toBe('2026-08-16')
    expect(addCalendarDays('2026-12-31', 1)).toBe('2027-01-01')
  })
})
