import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { TimeBlockCard } from './TimeBlockCard'
import { BlockTimeFields } from './BlockTimeFields'
import { DayTimeline } from './DayTimeline'
import { DragDropProvider } from '@dnd-kit/react'
import type { DayRead, TimeBlock } from '../lib/api'

const block: TimeBlock = { id: 1, lane: 'planned', task_type_id: 1,
  task_type: { id: 1, name: 'Work', created_at: '', updated_at: '' },
  name: 'Quick reply', note: null, start_minute: 600, end_minute: 605, created_at: '', updated_at: '' }

it('keeps a five-minute footprint, reveals handles with zoom, and retains an active resize', () => {
  const onPatch = vi.fn(async () => {})
  const card = (scale: number) => <TimeBlockCard block={block} lane="planned" visibleStartMin={600} visibleEndMin={840}
    slotHeightPx={46 * scale} readOnly={false} sameLaneBlocks={[block]} resizeMinStartMinute={600} resizeMaxEndMinute={840}
    getMinuteFromClientY={y => y} onPatch={onPatch} />
  const view = render(card(1))
  const shell = view.container.querySelector('[data-block]') as HTMLElement
  expect(parseFloat(shell.style.height)).toBeCloseTo(46 / 6)
  expect(screen.queryByText('Quick reply')).toBeNull()
  expect(screen.queryByRole('button', { name: 'Resize block end' })).toBeNull()
  view.rerender(card(12))
  HTMLElement.prototype.setPointerCapture = vi.fn()
  HTMLElement.prototype.releasePointerCapture = vi.fn()
  const handle = screen.getByRole('button', { name: 'Resize block end' })
  fireEvent.pointerDown(handle, { button: 0, pointerId: 1, clientY: 605 })
  fireEvent.pointerMove(window, { pointerId: 1, clientY: 601 })
  expect(screen.getByRole('button', { name: 'Resize block end' })).toBeInTheDocument()
  fireEvent.pointerUp(window, { pointerId: 1, clientY: 601 })
  expect(onPatch).toHaveBeenCalledWith({ start_minute: 600, end_minute: 601 })
  expect(screen.queryByRole('button', { name: 'Resize block end' })).toBeNull()
})

it('saves a one-minute interval and retains server failure for correction', async () => {
  const save = vi.fn().mockRejectedValue(new Error('This time overlaps another block'))
  render(<BlockTimeFields block={block} onSave={save} />)
  fireEvent.change(screen.getByLabelText('End'), { target: { value: '10:01' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save times' }))
  await waitFor(() => expect(save).toHaveBeenCalledWith({ start_minute: 600, end_minute: 601 }))
  expect(await screen.findByRole('alert')).toHaveTextContent('overlaps')
  expect(screen.getByLabelText('End')).toHaveValue('10:01')
})

it('clamps zoom and changes creation coordinates without patching saved blocks', () => {
  const day: DayRead = { id: 1, date: '2026-09-13', start_hour: 10, end_hour: 14,
    show_full_day: false, time_blocks: [block], actual_blocks: [], created_at: '', updated_at: '',
    meta: { timezone: 'UTC', today: '2026-09-13', server_now_iso: '2026-09-13T12:00:00Z' } }
  const patch = vi.fn(async () => {}), create = vi.fn()
  const view = render(<DragDropProvider><DayTimeline now={Date.now} day={day} readOnly={false} draft={null} selectedBlockId={null}
    onPatchBlock={patch} onLaneSlotClick={create} /></DragDropProvider>)
  const timeline = screen.getByTestId('day-timeline')
  fireEvent.wheel(timeline, { ctrlKey: true, deltaY: -1000 })
  const lane = view.container.querySelector('[data-day-lane="planned"]')!
  expect(lane).toHaveAttribute('data-slot-height', '552')
  fireEvent.click(lane, { clientY: 552 })
  expect(create).toHaveBeenCalledWith('planned', 630, 660)
  expect(patch).not.toHaveBeenCalled()
  fireEvent.wheel(timeline, { ctrlKey: true, deltaY: 1000 })
  expect(lane).toHaveAttribute('data-slot-height', '23')
})

it('cancels a pending block move when a second touch starts a pinch', () => {
  const day: DayRead = { id: 1, date: '2026-09-13', start_hour: 10, end_hour: 14,
    show_full_day: false, time_blocks: [block], actual_blocks: [], created_at: '', updated_at: '',
    meta: { timezone: 'UTC', today: '2026-09-13', server_now_iso: '2026-09-13T12:00:00Z' } }
  const patch = vi.fn(async () => {})
  render(<DragDropProvider><DayTimeline now={Date.now} day={day} readOnly={false} draft={null} selectedBlockId={null}
    onPatchBlock={patch} onLaneSlotClick={vi.fn()} /></DragDropProvider>)
  const card = screen.getByRole('button', { name: 'Edit planned block' })
  fireEvent.pointerDown(card, { button: 0, pointerType: 'touch', pointerId: 1, clientY: 100 })
  fireEvent.pointerMove(window, { pointerId: 1, clientY: 150 })
  fireEvent.pointerDown(card, { button: 0, pointerType: 'touch', pointerId: 2, clientY: 200 })
  fireEvent.pointerMove(window, { pointerId: 1, clientY: 300 })
  fireEvent.pointerUp(window, { pointerId: 1, clientY: 300 })
  fireEvent.pointerUp(window, { pointerId: 2, clientY: 200 })
  expect(patch).not.toHaveBeenCalled()
})
