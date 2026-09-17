import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { DragDropProvider } from '@dnd-kit/react'
import { DayTimeline } from '../../components/DayTimeline'
import type { DayRead } from '../../lib/api'
import { DayViewOptions } from './DayViewOptions'
import { DAY_VIEW_STORAGE_KEY, readDayViewPreferences, useDayViewPreferences } from './dayViewPreferences'

function Harness() {
  const { preferences, change, storageError } = useDayViewPreferences()
  return <DayViewOptions preferences={preferences} onChange={change} zoom={1} onResetZoom={() => {}} storageError={storageError} onClose={() => {}} />
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
it('resets timeline zoom from the menu while the zoom bar is hidden', () => {
  const day: DayRead = { id: 1, date: '2026-09-13', start_hour: 10, end_hour: 14,
    show_full_day: false, time_blocks: [], actual_blocks: [], created_at: '', updated_at: '',
    meta: { timezone: 'UTC', today: '2026-09-13', server_now_iso: '2026-09-13T12:00:00Z' } }
  function Page() {
    const [zoom, setZoom] = useState(1)
    return <DragDropProvider>
      <DayViewOptions preferences={{ calendar: true, tracking: false, zoom: false }} onChange={() => {}}
        zoom={zoom} onResetZoom={() => setZoom(1)} storageError={null} onClose={() => {}} />
      <DayTimeline day={day} showZoomControls={false} zoom={zoom} onZoomChange={setZoom} readOnly={false} draft={null}
        selectedBlockId={null} onPatchBlock={async () => {}} onLaneSlotClick={() => {}} />
    </DragDropProvider>
  }
  const view = render(<Page />)
  const reset = screen.getByRole('button', { name: /Reset zoom/ })
  const lane = view.container.querySelector('[data-day-lane="planned"]')!
  const timeline = screen.getByTestId('day-timeline')
  expect(reset).toBeDisabled()
  fireEvent.wheel(timeline, { ctrlKey: true, deltaY: -100 })
  expect(reset).toBeEnabled()
  expect(reset).not.toHaveTextContent('1.0×')
  fireEvent.click(reset)
  expect(reset).toBeDisabled()
  expect(reset).toHaveTextContent('1.0×')
  expect(lane).toHaveAttribute('data-slot-height', '46')
  // Gestures continue from the reset scale rather than the pre-reset one.
  fireEvent.wheel(timeline, { ctrlKey: true, deltaY: 10 })
  expect(Number(lane.getAttribute('data-slot-height'))).toBeCloseTo(46 * Math.exp(-0.1))
})
