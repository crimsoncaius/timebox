import { describe, expect, it } from 'vitest'
import {
  addCalendarDays,
  dateInTimeZone,
  deadlineBadge,
  orderedPlannedDates,
  plannedDateSummary,
  validPlannedDates,
} from './battlePlan'

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

describe('planned date presentation', () => {
  const now = '2026-08-22T12:00:00Z'

  it.each([
    [['2026-08-20', '2026-08-22', '2026-08-24'], '2026-08-22', 'Today', 'today'],
    [['2026-08-20', '2026-08-24', '2026-08-23'], '2026-08-23', 'Tomorrow', 'future'],
    [['2026-08-20', '2026-08-21'], '2026-08-21', 'Yesterday', 'past'],
    [['2026-08-20', '2026-08-24'], '2026-08-24', null, 'future'],
  ])('chooses the primary date for %o', (dates, primaryDate, relativeLabel, tone) => {
    expect(plannedDateSummary(dates, now, 'UTC', 'en-US')).toMatchObject({
      primaryDate,
      relativeLabel,
      tone,
      additionalCount: dates.length - 1,
    })
  })

  it('orders today, ascending future dates, then descending past dates', () => {
    expect(orderedPlannedDates(
      ['2026-08-19', '2026-08-24', '2026-08-21', '2026-08-23', '2026-08-22'],
      '2026-08-22',
    )).toEqual(['2026-08-22', '2026-08-23', '2026-08-24', '2026-08-21', '2026-08-19'])
  })

  it('deduplicates dates, rejects invalid values, and adds a year only across years', () => {
    expect(validPlannedDates(['2026-08-22', '2026-08-22', 'bad', '2026-02-30'])).toEqual(['2026-08-22'])
    expect(plannedDateSummary(['2027-01-02'], now, 'UTC', 'en-US')).toMatchObject({
      dateLabel: 'Jan 2, 2027',
    })
    expect(plannedDateSummary(undefined, now, 'UTC')).toBeNull()
  })

  it('uses the configured timezone at a calendar-day boundary', () => {
    expect(plannedDateSummary(
      ['2026-08-23'],
      '2026-08-22T16:30:00Z',
      'Asia/Singapore',
      'en-US',
    )).toMatchObject({ relativeLabel: 'Today', tone: 'today' })
  })
})
