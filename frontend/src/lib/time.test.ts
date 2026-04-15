import { describe, expect, it } from 'vitest'
import {
  addDaysIso,
  addMonthsIso,
  firstOfMonthIso,
  floorToSlotMinute,
  minuteFromPointerYInVisibleLane,
  monthGridForIso,
  monthYearLabelForIso,
  MOVE_PREVIEW_BLOCK_HYSTERESIS_MINUTES,
  resolveSameLaneMovePreviewStart,
  resolveSameLaneMoveStart,
  gapBoundsForDraft,
  sameLaneResizeBounds,
  snapToSlot,
  validStartMinuteRangesForDuration,
  visibleMinuteRange,
} from './time'

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

  it('floorToSlotMinute floors to 30-minute grid', () => {
    expect(floorToSlotMinute(0)).toBe(0)
    expect(floorToSlotMinute(29)).toBe(0)
    expect(floorToSlotMinute(30)).toBe(30)
    expect(floorToSlotMinute(541)).toBe(540)
  })

  it('resolveSameLaneMoveStart: only moving block in lane — free move', () => {
    const a = { id: 1, start_minute: 480, end_minute: 510 }
    expect(resolveSameLaneMoveStart([a], 1, 30, 600, 480)).toBe(600)
    expect(resolveSameLaneMoveStart([a], 1, 30, 600, 600)).toBe(600)
  })

  it('resolveSameLaneMoveStart: jump down past blocker into next gap', () => {
    const a = { id: 1, start_minute: 480, end_minute: 510 }
    const b = { id: 2, start_minute: 540, end_minute: 600 }
    const lane = [a, b]
    // Candidate 540–570 overlaps B; moving down from 480 → land at 600
    expect(resolveSameLaneMoveStart(lane, 1, 30, 540, 480)).toBe(600)
    expect(resolveSameLaneMoveStart(lane, 1, 30, 570, 510)).toBe(600)
  })

  it('resolveSameLaneMoveStart: jump up past blocker into previous gap', () => {
    const a = { id: 1, start_minute: 480, end_minute: 510 }
    const b = { id: 2, start_minute: 540, end_minute: 600 }
    const lane = [a, b]
    // Candidate 540–570 overlaps B; moving up from 600 → land at 510
    expect(resolveSameLaneMoveStart(lane, 1, 30, 540, 600)).toBe(510)
    expect(resolveSameLaneMoveStart(lane, 1, 30, 520, 600)).toBe(510)
  })

  it('resolveSameLaneMoveStart: moving block B does not block A', () => {
    const a = { id: 1, start_minute: 480, end_minute: 510 }
    const b = { id: 2, start_minute: 540, end_minute: 600 }
    const lane = [a, b]
    expect(resolveSameLaneMoveStart(lane, 2, 30, 510, 540)).toBe(510)
    expect(resolveSameLaneMoveStart(lane, 2, 30, 630, 540)).toBe(630)
  })

  const laneWithGap = () => {
    const a = { id: 1, start_minute: 480, end_minute: 510 }
    const b = { id: 2, start_minute: 540, end_minute: 600 }
    return [a, b]
  }

  it('resolveSameLaneMovePreviewStart: valid candidate passes through', () => {
    const lane = laneWithGap()
    expect(resolveSameLaneMovePreviewStart(lane, 1, 30, 600, 480)).toBe(600)
  })

  it('resolveSameLaneMovePreviewStart: stays at committed until threshold toward higher naive', () => {
    const lane = laneWithGap()
    const h = MOVE_PREVIEW_BLOCK_HYSTERESIS_MINUTES
    // Invalid overlap strip; instant resolve would snap to 600, preview stays 510 until c >= 600 - h
    expect(resolveSameLaneMovePreviewStart(lane, 1, 30, 570, 510)).toBe(510)
    expect(resolveSameLaneMovePreviewStart(lane, 1, 30, 600 - h - 1, 510)).toBe(510)
    expect(resolveSameLaneMovePreviewStart(lane, 1, 30, 600 - h, 510)).toBe(600)
  })

  it('resolveSameLaneMovePreviewStart: stays at committed until threshold toward lower naive', () => {
    const lane = laneWithGap()
    const h = MOVE_PREVIEW_BLOCK_HYSTERESIS_MINUTES
    expect(resolveSameLaneMovePreviewStart(lane, 1, 30, 540, 600)).toBe(600)
    expect(resolveSameLaneMovePreviewStart(lane, 1, 30, 510 + h + 1, 600)).toBe(600)
    expect(resolveSameLaneMovePreviewStart(lane, 1, 30, 510 + h, 600)).toBe(510)
  })

  it('resolveSameLaneMovePreviewStart: does not oscillate mid-gap candidate stream', () => {
    const lane = laneWithGap()
    let preview = 510
    preview = resolveSameLaneMovePreviewStart(lane, 1, 30, 593, preview)
    expect(preview).toBe(600)
    preview = resolveSameLaneMovePreviewStart(lane, 1, 30, 540, preview)
    expect(preview).toBe(600)
    preview = resolveSameLaneMovePreviewStart(lane, 1, 30, 570, preview)
    expect(preview).toBe(600)
    preview = resolveSameLaneMovePreviewStart(lane, 1, 30, 515, preview)
    expect(preview).toBe(510)
  })

  it('validStartMinuteRangesForDuration: exposes gaps for fixed duration', () => {
    const a = { id: 1, start_minute: 480, end_minute: 510 }
    const b = { id: 2, start_minute: 540, end_minute: 600 }
    const obstacles = [a, b].sort((x, y) => x.start_minute - y.start_minute)
    expect(validStartMinuteRangesForDuration(obstacles, 30)).toEqual([
      { lo: 0, hi: 450 },
      { lo: 510, hi: 510 },
      { lo: 600, hi: 1410 },
    ])
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

  it('monthGridForIso returns 42 Sunday-first cells with inMonth flags', () => {
    const grid = monthGridForIso('2026-04-22')
    expect(grid).toHaveLength(42)
    // April 2026: 1st is Wednesday (UTC) → grid starts Sunday 2026-03-29
    expect(grid[0].iso).toBe('2026-03-29')
    expect(grid[0].inMonth).toBe(false)
    expect(grid[3].iso).toBe('2026-04-01')
    expect(grid[3].inMonth).toBe(true)
    expect(grid[24].iso).toBe('2026-04-22')
    expect(grid[24].inMonth).toBe(true)
    expect(grid[41].iso).toBe('2026-05-09')
  })

  it('monthYearLabelForIso uses UTC', () => {
    expect(monthYearLabelForIso('2026-04-13', 'en-US')).toMatch(/April 2026/)
  })
})
