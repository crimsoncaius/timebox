import { describe, expect, it } from 'vitest'
import { nearestBlockStart, blockRangeAvailable } from './blockPlacement'

const block = (start_minute: number, end_minute: number) => ({ start_minute, end_minute })

describe('nearest Block placement', () => {
  it('chooses the closest start with later ties, preserving valid starts', () => {
    const occupied = [block(600, 630)]
    expect(nearestBlockStart(occupied, 600, 30, 480, 1200)).toBe(630)
    expect(nearestBlockStart(occupied, 590, 30, 480, 1200)).toBe(570)
    expect(nearestBlockStart(occupied, 547, 30, 480, 1200)).toBe(547)
  })
  it('finds a fitting gap beyond the former 30 minute limit', () => {
    expect(nearestBlockStart([block(480, 630)], 600, 30, 480, 1200)).toBe(630)
    expect(nearestBlockStart([block(480, 631)], 600, 30, 480, 1200)).toBe(631)
    expect(nearestBlockStart([block(600, 1200)], 600, 30, 480, 1200)).toBe(570)
    expect(nearestBlockStart([block(599, 1200)], 600, 30, 480, 1200)).toBe(569)
  })
  it('fits exact minute boundaries without shortening the block', () => {
    const occupied = [block(632, 1200), block(480, 602), block(490, 590)]
    expect(nearestBlockStart(occupied, 600, 30, 480, 1200)).toBe(602)
    expect(nearestBlockStart(occupied, 600, 60, 480, 1200)).toBeNull()
  })
  it('searches inside the configured range and respects full duration', () => {
    expect(nearestBlockStart([block(480, 1200)], 600, 30, 480, 1200)).toBeNull()
    expect(nearestBlockStart([], 1200, 60, 480, 1200)).toBe(1140)
    expect(nearestBlockStart([], 1170, 60, 480, 1200)).toBe(1140)
    expect(nearestBlockStart([], 440, 30, 480, 1200)).toBe(480)
  })
})


describe('short Actual Block placement', () => {
  it('preserves short recorded durations and validates exact boundaries', () => {
    expect(nearestBlockStart([block(540, 557)], 550, 1, 480, 720, 1)).toBe(557)
    expect(blockRangeAvailable([block(540, 557)], 557, 558, 480, 720, 1)).toBe(true)
    expect(blockRangeAvailable([], 710, 730, 480, 720, 1)).toBe(false)
  })
})
