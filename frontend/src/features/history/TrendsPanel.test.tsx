import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { TrendsPanel } from './TrendsPanel'
import { shiftTrendRange, type TrendsReport } from './trends'
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
  expect(onDrill).toHaveBeenCalledWith('Directly under work', { '2026-09-18': 1800 })
})

it('uses server date boundaries for navigation and inclusive custom ranges', async () => {
  const get = vi.spyOn(api, 'trends').mockResolvedValue(report)
  render(<TrendsPanel active onDrill={() => {}} />)
  const user = userEvent.setup()
  await screen.findByTestId('trends-total')
  await user.click(screen.getByRole('button', { name: 'Previous week' }))
  await waitFor(() => expect(get.mock.calls.at(-1)?.[0].get('anchor')).toBe('2026-09-07'))
  await screen.findByTestId('trends-total')
  await user.click(screen.getByRole('button', { name: 'Custom' }))
  await waitFor(() => expect(get.mock.calls.at(-1)?.[0].get('start')).toBe('2026-09-14'))
  expect(get.mock.calls.at(-1)?.[0].get('end')).toBe('2026-09-20')
})

it('navigates across leap months and year boundaries without local timezone drift', () => {
  expect(shiftTrendRange('2024-03-01', 'month', -1)).toBe('2024-02-01')
  expect(shiftTrendRange('2026-12-01', 'month', 1)).toBe('2027-01-01')
  expect(shiftTrendRange('2026-01-01', 'day', -1)).toBe('2025-12-31')
})
