import { describe, expect, it } from 'vitest'
import {
  addDaysIso,
  addMonthsIso,
  calendarIsoDateInTimeZone,
  firstOfMonthIso,
  formatHourLabelGcal12,
  formatTimeRangeGcal12,
  gapBoundsForDraft,
  minuteFromPointerYInVisibleLane,
  minuteOfDayWithSecondsInTimeZone,
  monthGridForIso,
  monthYearLabelForIso,
  sameLaneResizeBounds,
  visibleMinuteRange,
  zonedLocalDateTimeToIso,
  zonedLocalToIso,
} from './time'

describe('time helpers', () => {
  it('requires an explicit choice for repeated local times', () => {
    expect(() => zonedLocalDateTimeToIso('2025-11-02T01:30', 'America/New_York')).toThrow(/occurs twice/i)
    expect(zonedLocalDateTimeToIso('2025-11-02T01:30', 'America/New_York', 'earlier')).toBe('2025-11-02T05:30:00.000Z')
    expect(zonedLocalDateTimeToIso('2025-11-02T01:30', 'America/New_York', 'later')).toBe('2025-11-02T06:30:00.000Z')
  })

  it('converts minute-accurate wall-clock values in the configured timezone', () => {
    expect(zonedLocalDateTimeToIso('2026-08-30T09:17', 'Asia/Singapore')).toBe('2026-08-30T01:17:00.000Z')
    expect(zonedLocalDateTimeToIso('2026-08-31T00:20', 'Asia/Singapore')).toBe('2026-08-30T16:20:00.000Z')
  })

  it('rejects invalid and nonexistent wall-clock values', () => {
    expect(() => zonedLocalDateTimeToIso('2026-02-30T09:00', 'UTC')).toThrow(/valid local date/i)
    expect(() => zonedLocalDateTimeToIso('2026-03-08T02:30', 'America/New_York')).toThrow(/does not exist/i)
  })

  it('visibleMinuteRange respects day window', () => {
    expect(
      visibleMinuteRange({ show_full_day: false, start_hour: 8, end_hour: 20 }),
    ).toEqual({ start: 8 * 60, end: 20 * 60 })
    expect(visibleMinuteRange({ show_full_day: true, start_hour: 8, end_hour: 20 })).toEqual({
      start: 0,
      end: 24 * 60,
    })
  })

  it('minuteFromPointerYInVisibleLane clamps Y to the visible slot rows', () => {
    const vs = 8 * 60
    const ve = 20 * 60
    const h = 46
    expect(minuteFromPointerYInVisibleLane(-80, vs, ve, h)).toBe(vs)
    expect(minuteFromPointerYInVisibleLane(0, vs, ve, h)).toBe(vs)
    expect(minuteFromPointerYInVisibleLane(h - 1, vs, ve, h)).toBe(vs)
    expect(minuteFromPointerYInVisibleLane(h, vs, ve, h)).toBe(vs + 30)
    const lastRow = ((ve - vs) / 30 - 1) * h
    expect(minuteFromPointerYInVisibleLane(lastRow, vs, ve, h)).toBe(ve - 30)
    expect(minuteFromPointerYInVisibleLane(lastRow + h * 4, vs, ve, h)).toBe(ve - 30)
  })

  it('sameLaneResizeBounds clamps to neighbors in sorted order', () => {
    const a = { id: 1, start_minute: 480, end_minute: 510 }
    const b = { id: 2, start_minute: 540, end_minute: 600 }
    const c = { id: 3, start_minute: 600, end_minute: 630 }
    const lane = [b, a, c] // unsorted input still works
    expect(sameLaneResizeBounds(lane, 1)).toEqual({ minStartMinute: 0, maxEndMinute: 540 })
    expect(sameLaneResizeBounds(lane, 2)).toEqual({ minStartMinute: 510, maxEndMinute: 600 })
    expect(sameLaneResizeBounds(lane, 3)).toEqual({ minStartMinute: 600, maxEndMinute: 24 * 60 })
  })

  it('sameLaneResizeBounds returns full day when id missing', () => {
    expect(sameLaneResizeBounds([], 99)).toEqual({ minStartMinute: 0, maxEndMinute: 24 * 60 })
  })

  it('gapBoundsForDraft returns gap containing the draft interval', () => {
    const a = { id: 1, start_minute: 480, end_minute: 510 }
    const b = { id: 2, start_minute: 540, end_minute: 600 }
    const lane = [b, a]
    expect(gapBoundsForDraft(lane, 510, 540)).toEqual({ minStartMinute: 510, maxEndMinute: 540 })
    expect(gapBoundsForDraft(lane, 600, 630)).toEqual({ minStartMinute: 600, maxEndMinute: 24 * 60 })
    expect(gapBoundsForDraft(lane, 0, 30)).toEqual({ minStartMinute: 0, maxEndMinute: 480 })
  })

  it('gapBoundsForDraft empty lane is full day', () => {
    expect(gapBoundsForDraft([], 120, 180)).toEqual({ minStartMinute: 0, maxEndMinute: 24 * 60 })
  })

  it('gapBoundsForDraft falls back when interval does not fit any gap', () => {
    const a = { id: 1, start_minute: 480, end_minute: 510 }
    expect(gapBoundsForDraft([a], 500, 520)).toEqual({ minStartMinute: 0, maxEndMinute: 24 * 60 })
  })

  it('addDaysIso shifts UTC calendar dates', () => {
    expect(addDaysIso('2026-06-01', 1)).toBe('2026-06-02')
    expect(addDaysIso('2026-06-01', -1)).toBe('2026-05-31')
  })

  it('firstOfMonthIso returns first of month', () => {
    expect(firstOfMonthIso('2026-06-15')).toBe('2026-06-01')
    expect(firstOfMonthIso('2026-01-31')).toBe('2026-01-01')
  })

  it('addMonthsIso moves by calendar months in UTC', () => {
    expect(addMonthsIso('2026-06-15', 1)).toBe('2026-07-01')
    expect(addMonthsIso('2026-01-15', -1)).toBe('2025-12-01')
    expect(addMonthsIso('2026-12-15', 1)).toBe('2027-01-01')
  })

  it('monthGridForIso returns 42 Monday-first cells with inMonth flags', () => {
    const grid = monthGridForIso('2026-04-22')
    expect(grid).toHaveLength(42)
    // April 2026: 1st is Wednesday (UTC) → grid starts Monday 2026-03-30
    expect(grid[0].iso).toBe('2026-03-30')
    expect(grid[0].inMonth).toBe(false)
    expect(grid[2].iso).toBe('2026-04-01')
    expect(grid[2].inMonth).toBe(true)
    expect(grid[23].iso).toBe('2026-04-22')
    expect(grid[23].inMonth).toBe(true)
    expect(grid[41].iso).toBe('2026-05-10')
  })

  it('monthYearLabelForIso uses UTC', () => {
    expect(monthYearLabelForIso('2026-04-13', 'en-US')).toMatch(/April 2026/)
  })

  it('calendarIsoDateInTimeZone formats YYYY-MM-DD in the given zone', () => {
    const d = new Date('2026-06-01T12:00:00Z')
    expect(calendarIsoDateInTimeZone(d, 'UTC')).toBe('2026-06-01')
  })

  it('minuteOfDayWithSecondsInTimeZone returns fractional minutes in the given zone', () => {
    const d = new Date('2026-06-01T12:34:56Z')
    expect(minuteOfDayWithSecondsInTimeZone(d, 'UTC')).toBe(12 * 60 + 34 + 56 / 60)
  })

  it('formatTimeRangeGcal12 uses compact 12h like Google Calendar', () => {
    expect(formatTimeRangeGcal12(4 * 60 + 15, 5 * 60 + 45)).toBe('4:15 – 5:45am')
    expect(formatTimeRangeGcal12(9 * 60, 10 * 60 + 30)).toBe('9 – 10:30am')
    expect(formatTimeRangeGcal12(11 * 60 + 30, 13 * 60 + 15)).toBe('11:30am – 1:15pm')
  })

  it('formatHourLabelGcal12 labels hour on the 12h clock', () => {
    expect(formatHourLabelGcal12(4 * 60)).toBe('4 AM')
    expect(formatHourLabelGcal12(12 * 60)).toBe('12 PM')
  })

  it('floors fractional range endpoints only for display', () => {
    const end = minuteOfDayWithSecondsInTimeZone(new Date('2026-06-01T10:23:21Z'), 'UTC')
    expect(end).toBeCloseTo(623.35)
    expect(formatTimeRangeGcal12(540, end)).toBe('9 – 10:23am')
    expect(formatTimeRangeGcal12(540.9, 600.9)).toBe('9 – 10am')
    expect(formatTimeRangeGcal12(719.99, 720.5)).toBe('11:59am – 12pm')
    expect(formatTimeRangeGcal12(1439.99, 1440.5)).toBe('11:59pm – 12am')
  })

  describe('zonedLocalToIso', () => {
    it('converts an ordinary wall-clock time in the given zone', () => {
      expect(zonedLocalToIso('2026-06-01T09:30', 'Asia/Singapore')).toBe('2026-06-01T01:30:00.000Z')
    })

    it('accepts a bare date as local midnight', () => {
      expect(zonedLocalToIso('2026-06-01', 'Asia/Singapore')).toBe('2026-05-31T16:00:00.000Z')
    })

    it('takes the earlier occurrence of an ambiguous autumn time', () => {
      // 01:30 happens twice when New York leaves DST on 2026-11-01.
      expect(zonedLocalToIso('2026-11-01T01:30', 'America/New_York')).toBe('2026-11-01T05:30:00.000Z')
    })

    it('moves a spring-forward gap to the first instant that exists', () => {
      // 02:30 never happens when New York enters DST on 2026-03-08.
      expect(zonedLocalToIso('2026-03-08T02:30', 'America/New_York')).toBe('2026-03-08T07:00:00.000Z')
    })

    it('returns an empty string for an empty value', () => {
      expect(zonedLocalToIso('', 'UTC')).toBe('')
    })
  })
})
