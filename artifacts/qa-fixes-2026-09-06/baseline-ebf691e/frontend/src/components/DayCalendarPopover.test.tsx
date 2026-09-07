import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DayCalendarPopover } from './DayCalendarPopover'

describe('DayCalendarPopover', () => {
  it('shows trigger with en-GB style date', () => {
    render(
      <DayCalendarPopover value="2026-04-22" todayIso="2026-04-13" onSelect={vi.fn()} />,
    )
    expect(screen.getByTestId('day-calendar-trigger')).toHaveTextContent('22/04/2026')
  })

  it('opens and closes popover', async () => {
    const user = userEvent.setup()
    render(
      <DayCalendarPopover value="2026-06-01" todayIso="2026-06-15" onSelect={vi.fn()} />,
    )
    await user.click(screen.getByTestId('day-calendar-trigger'))
    expect(screen.getByTestId('day-calendar-popover')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByTestId('day-calendar-popover')).not.toBeInTheDocument()
  })

  it('does not call onSelect when only changing month', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<DayCalendarPopover value="2026-06-01" todayIso="2026-06-15" onSelect={onSelect} />)
    await user.click(screen.getByTestId('day-calendar-trigger'))
    await user.click(screen.getByRole('button', { name: 'Next month' }))
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('calls onSelect with ISO when a day is chosen and closes', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<DayCalendarPopover value="2026-06-01" todayIso="2026-06-15" onSelect={onSelect} />)
    await user.click(screen.getByTestId('day-calendar-trigger'))
    await user.click(screen.getByRole('button', { name: '2026-06-10' }))
    expect(onSelect).toHaveBeenCalledWith('2026-06-10')
    expect(screen.queryByTestId('day-calendar-popover')).not.toBeInTheDocument()
  })

  it('selects out-of-month cell', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<DayCalendarPopover value="2026-06-01" todayIso="2026-06-15" onSelect={onSelect} />)
    await user.click(screen.getByTestId('day-calendar-trigger'))
    await user.click(screen.getByRole('button', { name: '2026-05-31' }))
    expect(onSelect).toHaveBeenCalledWith('2026-05-31')
  })

  it('Today navigates to todayIso', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<DayCalendarPopover value="2026-06-01" todayIso="2026-06-15" onSelect={onSelect} />)
    await user.click(screen.getByTestId('day-calendar-trigger'))
    await user.click(screen.getByRole('button', { name: 'Today' }))
    expect(onSelect).toHaveBeenCalledWith('2026-06-15')
  })

  it('closes on outside pointer down', async () => {
    const user = userEvent.setup()
    render(
      <div>
        <DayCalendarPopover value="2026-06-01" todayIso="2026-06-15" onSelect={vi.fn()} />
        <button type="button">outside</button>
      </div>,
    )
    await user.click(screen.getByTestId('day-calendar-trigger'))
    expect(screen.getByTestId('day-calendar-popover')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'outside' }))
    expect(screen.queryByTestId('day-calendar-popover')).not.toBeInTheDocument()
  })
})
