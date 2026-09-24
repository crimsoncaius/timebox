import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CalendarDateField, CalendarDateTimeField } from './CalendarDateField'

describe('CalendarDateField', () => {
  it('shows Monday first and applies the minimum date to the calendar', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<CalendarDateField label="End date" value="2026-04-22" minIso="2026-04-15" onChange={onChange} />)
    await user.click(screen.getByRole('button', { name: 'Choose end date' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getAllByText(/^(Mo|Tu|We|Th|Fr|Sa|Su)$/).map(item => item.textContent))
      .toEqual(['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'])
    expect(within(dialog).getByRole('button', { name: '2026-04-14' })).toBeDisabled()
    await user.click(within(dialog).getByRole('button', { name: '2026-04-16' }))
    expect(onChange).toHaveBeenCalledWith('2026-04-16')
  })

  it('accepts a typed valid date and rejects impossible dates', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<CalendarDateField label="Start date" value="" onChange={onChange} />)
    const input = screen.getByRole('textbox', { name: 'Start date' })
    await user.type(input, '2026-02-30')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(onChange).not.toHaveBeenCalledWith('2026-02-30')
    await user.clear(input)
    await user.type(input, '2026-02-28')
    expect(onChange).toHaveBeenCalledWith('2026-02-28')
  })
})

it('combines a selected Monday-calendar date with an existing time', async () => {
  const user = userEvent.setup()
  const onChange = vi.fn()
  render(<CalendarDateTimeField label="Deadline date and time" value="2026-04-22T14:30" onChange={onChange} />)
  await user.click(screen.getByRole('button', { name: 'Choose deadline date' }))
  await user.click(screen.getByRole('button', { name: '2026-04-23' }))
  expect(onChange).toHaveBeenCalledWith('2026-04-23T14:30')
})
