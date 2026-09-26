import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ChronicleHighlight } from './ChronicleHighlight'

it('summarises contributing days against the drilled range and links back to Trends', async () => {
  const onBack = vi.fn()
  const onClear = vi.fn()
  render(<ChronicleHighlight
    highlight={{ name: 'work/meetings', days: { '2026-09-22': 1800, '2026-09-24': 5700 }, range: { period: 'week', start: '2026-09-21', end: '2026-09-27' } }}
    onBack={onBack}
    onClear={onClear}
  />)
  expect(screen.getByLabelText('Highlight source')).toHaveTextContent('Trends›Week · Sep 21 – 27')
  expect(screen.getByTestId('chronicle-highlight')).toHaveTextContent('work / meetings on 2 of 7 days, 2h 5m in total.')
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: 'Trends' }))
  await user.click(screen.getByRole('button', { name: 'Clear' }))
  expect(onBack).toHaveBeenCalledOnce()
  expect(onClear).toHaveBeenCalledOnce()
})
