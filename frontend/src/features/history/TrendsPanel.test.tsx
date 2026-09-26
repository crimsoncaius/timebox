import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { TrendsPanel } from './TrendsPanel'
import { canAdvanceTrendRange, shiftTrendRange, trendRangeDayCount, trendRangeLabel, type TrendsReport } from './trends'
import { api } from '../../lib/api'

const report: TrendsReport = {
  start: '2026-09-14', end: '2026-09-20', today: '2026-09-19', timezone: 'Asia/Singapore', captured_at: '2026-09-19T05:00:00Z', duration_seconds: 7200,
  types: [{ path: 'work', name: 'work', duration_seconds: 7200, direct_seconds: 1800, days: { '2026-09-18': 7200 }, direct_days: { '2026-09-18': 1800 }, children: [
    { path: 'work/coding', name: 'coding', duration_seconds: 5400, direct_seconds: 5400, days: { '2026-09-18': 5400 }, direct_days: { '2026-09-18': 5400 }, children: [] },
  ] }],
}
afterEach(() => vi.restoreAllMocks())

it('expands child totals on the range scale and drills direct time separately', async () => {
  vi.spyOn(api, 'trends').mockResolvedValue(report)
  const onDrill = vi.fn()
  render(<TrendsPanel active onDrill={onDrill} />)
  const user = userEvent.setup()
  expect(await screen.findByTestId('trends-total')).toHaveTextContent('2h 0m')
  await user.click(screen.getByRole('button', { name: 'Week' }))
  expect(screen.getByTestId('trends-total')).toHaveTextContent('2h 0m')
  await user.click(screen.getByRole('button', { name: 'work' }))
  expect(screen.getByText('75.0%')).toBeInTheDocument()
  expect(screen.getByText('25.0%')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Show contributing days for Directly under work' }))
  expect(onDrill).toHaveBeenCalledWith('Directly under work', { '2026-09-18': 1800 }, { period: 'week', start: '2026-09-14', end: '2026-09-20' })
})

it('offers no drill-through for a Day range', async () => {
  vi.spyOn(api, 'trends').mockResolvedValue({ ...report, start: '2026-09-18', end: '2026-09-18' })
  const onDrill = vi.fn()
  render(<TrendsPanel active onDrill={onDrill} />)
  const user = userEvent.setup()
  await screen.findByTestId('trends-total')
  await user.click(screen.getByRole('button', { name: 'Day' }))
  await screen.findByTestId('trends-total')
  expect(screen.queryByRole('button', { name: /Show contributing days/ })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'work' }))
  expect(screen.getByText('coding')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'coding' })).not.toBeInTheDocument()
  expect(onDrill).not.toHaveBeenCalled()
})

it('labels the drilled range and counts its days', () => {
  expect(trendRangeLabel({ period: 'week', start: '2026-09-21', end: '2026-09-27' })).toBe('Week · Sep 21 – 27')
  expect(trendRangeLabel({ period: 'week', start: '2026-09-28', end: '2026-10-04' })).toBe('Week · Sep 28 – Oct 4')
  expect(trendRangeLabel({ period: 'month', start: '2026-09-01', end: '2026-09-30' })).toBe('Month · September 2026')
  expect(trendRangeLabel({ period: 'custom', start: '2025-12-29', end: '2026-01-04' })).toBe('Custom range · Dec 29, 2025 – Jan 4, 2026')
  expect(trendRangeDayCount({ period: 'week', start: '2026-09-21', end: '2026-09-27' })).toBe(7)
  expect(trendRangeDayCount({ period: 'month', start: '2026-02-01', end: '2026-02-28' })).toBe(28)
})

it('uses server date boundaries for navigation and inclusive custom ranges', async () => {
  const get = vi.spyOn(api, 'trends').mockImplementation(async query => query.get('anchor') === '2026-09-07'
    ? { ...report, start: '2026-09-07', end: '2026-09-13' }
    : report)
  render(<TrendsPanel active onDrill={() => {}} />)
  const user = userEvent.setup()
  await screen.findByTestId('trends-total')
  expect(screen.getByRole('button', { name: 'Next week' })).toBeDisabled()
  await user.click(screen.getByRole('button', { name: 'Previous week' }))
  await waitFor(() => expect(get.mock.calls.at(-1)?.[0].get('anchor')).toBe('2026-09-07'))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Next week' })).toBeEnabled())
  await user.click(screen.getByRole('button', { name: 'This week' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Next week' })).toBeDisabled())
  await user.click(screen.getByRole('button', { name: 'Custom' }))
  await waitFor(() => expect(get.mock.calls.at(-1)?.[0].get('start')).toBe('2026-09-14'))
  expect(get.mock.calls.at(-1)?.[0].get('end')).toBe('2026-09-19')
  expect(screen.getByLabelText('Range end')).toHaveAttribute('max', '2026-09-19')
  const callCount = get.mock.calls.length
  fireEvent.change(screen.getByLabelText('Range end'), { target: { value: '2026-09-20' } })
  expect(screen.getByRole('alert')).toHaveTextContent('Choose dates on or before Today')
  expect(screen.getByLabelText('Range end')).toHaveValue('2026-09-20')
  expect(get).toHaveBeenCalledTimes(callCount)
  fireEvent.change(screen.getByLabelText('Range end'), { target: { value: '2026-09-19' } })
  await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(callCount))
})

it('navigates across leap months and year boundaries without local timezone drift', () => {
  expect(shiftTrendRange('2024-03-01', 'month', -1)).toBe('2024-02-01')
  expect(shiftTrendRange('2026-12-01', 'month', 1)).toBe('2027-01-01')
  expect(shiftTrendRange('2026-01-01', 'day', -1)).toBe('2025-12-31')
  expect(canAdvanceTrendRange('2026-09-18', 'day', '2026-09-19')).toBe(true)
  expect(canAdvanceTrendRange('2026-09-19', 'day', '2026-09-19')).toBe(false)
  expect(canAdvanceTrendRange('2026-09-07', 'week', '2026-09-19')).toBe(true)
  expect(canAdvanceTrendRange('2026-09-14', 'week', '2026-09-19')).toBe(false)
  expect(canAdvanceTrendRange('2026-08-01', 'month', '2026-09-19')).toBe(true)
  expect(canAdvanceTrendRange('2026-09-01', 'month', '2026-09-19')).toBe(false)
})
