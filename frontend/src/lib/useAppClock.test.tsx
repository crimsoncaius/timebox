import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { dateInTimeZone } from './battlePlan'
import { millisecondsUntilNextAppMidnight, useAppClock } from './useAppClock'

function ClockProbe({ serverNowIso, timezone }: { serverNowIso: string; timezone: string }) {
  const now = useAppClock(serverNowIso, timezone)
  return <span>{dateInTimeZone(now, timezone)}</span>
}

describe('app clock', () => {
  afterEach(() => vi.useRealTimers())

  it('finds the next midnight in the configured timezone', () => {
    expect(millisecondsUntilNextAppMidnight('2026-08-22T15:59:59Z', 'Asia/Singapore')).toBe(1000)
  })

  it('recomputes the app date at midnight without reloading data', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-22T15:59:59Z'))
    render(<ClockProbe serverNowIso="2026-08-22T15:59:59Z" timezone="Asia/Singapore" />)
    expect(screen.getByText('2026-08-22')).toBeInTheDocument()

    act(() => { vi.advanceTimersByTime(1001) })

    expect(screen.getByText('2026-08-23')).toBeInTheDocument()
  })
})
