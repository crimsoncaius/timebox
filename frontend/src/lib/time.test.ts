import { describe, expect, it } from 'vitest'
import { snapToSlot, visibleMinuteRange } from './time'

describe('time helpers', () => {
  it('snapToSlot rounds to 30 minutes', () => {
    expect(snapToSlot(0)).toBe(0)
    expect(snapToSlot(29)).toBe(30)
    expect(snapToSlot(31)).toBe(30)
    expect(snapToSlot(45)).toBe(60)
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
})
