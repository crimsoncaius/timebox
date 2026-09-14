import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { DayViewOptions } from './DayViewOptions'
import { DAY_VIEW_STORAGE_KEY, readDayViewPreferences, useDayViewPreferences } from './dayViewPreferences'

function Harness() {
  const { preferences, change, storageError } = useDayViewPreferences()
  return <DayViewOptions preferences={preferences} onChange={change} storageError={storageError} onClose={() => {}} />
}
beforeEach(() => {
  localStorage.clear()
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
})
it('starts with the approved defaults, independently saves changes and restores them on a new visit', () => {
  const view = render(<Harness />)
  expect(screen.getByRole('switch', { name: 'Calendar' })).toHaveAttribute('aria-checked', 'true')
  expect(screen.getByRole('switch', { name: 'Activity Tracking' })).toHaveAttribute('aria-checked', 'false')
  expect(screen.getByRole('switch', { name: 'Zoom' })).toHaveAttribute('aria-checked', 'false')
  fireEvent.click(screen.getByRole('switch', { name: 'Calendar' }))
  fireEvent.click(screen.getByRole('switch', { name: 'Zoom' }))
  view.unmount()
  render(<Harness />)
  expect(screen.getByRole('switch', { name: 'Calendar' })).toHaveAttribute('aria-checked', 'false')
  expect(screen.getByRole('switch', { name: 'Activity Tracking' })).toHaveAttribute('aria-checked', 'false')
  expect(screen.getByRole('switch', { name: 'Zoom' })).toHaveAttribute('aria-checked', 'true')
})
it('recovers from invalid storage and ignores nonboolean fields', () => {
  localStorage.setItem(DAY_VIEW_STORAGE_KEY, 'bad-json')
  expect(readDayViewPreferences()).toEqual({ calendar: true, tracking: false, zoom: false })
  localStorage.setItem(DAY_VIEW_STORAGE_KEY, JSON.stringify({ calendar: false, tracking: 'true' }))
  expect(readDayViewPreferences()).toEqual({ calendar: false, tracking: false, zoom: false })
})
it('keeps controls usable and explains when persistent storage is unavailable', () => {
  const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied') })
  try {
    render(<Harness />)
    fireEvent.click(screen.getByRole('switch', { name: 'Zoom' }))
    expect(screen.getByRole('switch', { name: 'Zoom' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('alert')).toHaveTextContent('for this visit')
  } finally { spy.mockRestore() }
})
