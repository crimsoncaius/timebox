import { DragDropProvider } from '@dnd-kit/react'
import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DayRead } from '../lib/api'
import { DayTimeline } from './DayTimeline'

const originalSetPointerCapture = HTMLElement.prototype.setPointerCapture
const originalReleasePointerCapture = HTMLElement.prototype.releasePointerCapture

const day: DayRead = {
  id: 1,
  date: '2026-06-01',
  start_hour: 8,
  end_hour: 20,
  show_full_day: false,
  created_at: '',
  updated_at: '',
  time_blocks: [],
  actual_blocks: [],
  meta: { timezone: 'UTC', today: '2026-06-01', server_now_iso: '2026-06-01T12:00:00Z' },
}

function renderTimeline(onDraftTimeChange: (startMin: number, endMin: number) => void) {
  const view = render(
    <DragDropProvider>
      <DayTimeline
        day={day}
        readOnly={false}
        draft={{ lane: 'planned', start_minute: 480, end_minute: 510 }}
        selectedBlockId={null}
        onLaneSlotClick={vi.fn()}
        onDraftTimeChange={onDraftTimeChange}
        onPatchBlock={vi.fn(() => Promise.resolve())}
      />
    </DragDropProvider>,
  )
  const draft = view.container.querySelector('[data-testid="draft-block"]') as HTMLDivElement
  const resizeEnd = screen.getByRole('button', { name: 'Resize draft block end (Planned)' })
  return { ...view, draft, resizeEnd }
}

function timeline(dayValue: DayRead, autoScrollToNow = false) {
  return (
    <DragDropProvider>
      <DayTimeline
        day={dayValue}
        readOnly={false}
        draft={null}
        selectedBlockId={null}
        onLaneSlotClick={vi.fn()}
        onPatchBlock={vi.fn(() => Promise.resolve())}
        autoScrollToNow={autoScrollToNow}
      />
    </DragDropProvider>
  )
}

describe('DayTimeline draft resize', () => {
  beforeEach(() => {
    HTMLElement.prototype.setPointerCapture = vi.fn()
    HTMLElement.prototype.releasePointerCapture = vi.fn()
  })

  afterEach(() => {
    HTMLElement.prototype.setPointerCapture = originalSetPointerCapture
    HTMLElement.prototype.releasePointerCapture = originalReleasePointerCapture
  })

  it('cancels without changing the draft and remains resizable after cancellation or capture loss', () => {
    const onDraftTimeChange = vi.fn()
    const { draft, resizeEnd } = renderTimeline(onDraftTimeChange)

    fireEvent.pointerDown(resizeEnd, { button: 0, pointerId: 4, clientY: 46 })
    fireEvent.pointerMove(window, { pointerId: 4, clientY: 92 })
    expect(draft).toHaveAttribute('data-dragging', 'true')

    fireEvent.pointerCancel(window, { pointerId: 4, clientY: 92 })

    expect(draft).not.toHaveAttribute('data-dragging')
    expect(draft.style.height).toBe('46px')
    expect(onDraftTimeChange).not.toHaveBeenCalled()

    fireEvent.pointerDown(resizeEnd, { button: 0, pointerId: 5, clientY: 46 })
    fireEvent.pointerMove(window, { pointerId: 5, clientY: 92 })
    fireEvent.lostPointerCapture(resizeEnd, { pointerId: 5 })

    expect(draft).not.toHaveAttribute('data-dragging')
    expect(onDraftTimeChange).not.toHaveBeenCalled()

    fireEvent.pointerDown(resizeEnd, { button: 0, pointerId: 6, clientY: 46 })
    fireEvent.pointerMove(window, { pointerId: 6, clientY: 92 })
    fireEvent.pointerUp(window, { pointerId: 6, clientY: 92 })

    expect(onDraftTimeChange).toHaveBeenCalledWith(480, 540)
    expect(onDraftTimeChange).toHaveBeenCalledTimes(1)
  })
})

describe('DayTimeline Actual Block movement', () => {
  beforeEach(() => {
    HTMLElement.prototype.setPointerCapture = vi.fn()
    HTMLElement.prototype.releasePointerCapture = vi.fn()
  })

  afterEach(() => {
    HTMLElement.prototype.setPointerCapture = originalSetPointerCapture
    HTMLElement.prototype.releasePointerCapture = originalReleasePointerCapture
  })

  it('moves an Actual Block through the lane-aware patch callback', () => {
    const onPatchBlock = vi.fn(() => Promise.resolve())
    render(
      <DragDropProvider>
        <DayTimeline
          day={{
            ...day,
            actual_blocks: [{
              date: day.date,
              start_minute: 556,
              end_minute: 585,
              duration_minutes: 28,
              actual_block: {
                id: 40,
                task_type_id: 1,
                task_type: { id: 1, name: 'focus', created_at: '', updated_at: '' },
                task_id: null,
                task: null,
                note: null,
                planned_block_id: null,
                start_at: '2026-06-01T09:16:38Z',
                end_at: '2026-06-01T09:45:00Z',
                created_at: '',
                updated_at: '',
              },
            }],
          }}
          readOnly={false}
          draft={null}
          selectedBlockId={null}
          onLaneSlotClick={vi.fn()}
          onPatchBlock={onPatchBlock}
        />
      </DragDropProvider>,
    )

    const body = screen.getByRole('button', { name: 'Edit actual block' })
    fireEvent.pointerDown(body, { button: 0, pointerId: 7, clientX: 10, clientY: 115 })
    fireEvent.pointerMove(window, { pointerId: 7, clientX: 10, clientY: 125 })
    fireEvent.pointerMove(window, { pointerId: 7, clientX: 10, clientY: 171 })
    fireEvent.pointerUp(window, { pointerId: 7, clientX: 10, clientY: 171 })

    expect(onPatchBlock).toHaveBeenCalledWith(
      40,
      { start_minute: 570, end_minute: 599 },
      'actual',
    )
  })

  it('identifies standalone Actual Blocks by Name, meaningful Task Type, then Untitled', () => {
    const actual = {
      id: 40,
      task_type_id: 1,
      task_type: { id: 1, name: 'social', created_at: '', updated_at: '' },
      task_id: null,
      task: null,
      name: 'Dinner with Alex',
      note: null,
      planned_block_id: null,
      start_at: '2026-06-01T09:00:00Z',
      end_at: '2026-06-01T10:00:00Z',
      created_at: '',
      updated_at: '',
    }
    const projection = {
      date: day.date,
      start_minute: 540,
      end_minute: 600,
      duration_minutes: 60,
      actual_block: actual,
    }
    const view = render(timeline({ ...day, actual_blocks: [projection] }))

    expect(screen.getByText('Dinner with Alex')).toBeInTheDocument()
    expect(screen.getByText('social')).toBeInTheDocument()

    view.rerender(timeline({
      ...day,
      actual_blocks: [{ ...projection, actual_block: { ...actual, name: null } }],
    }))
    expect(screen.getByText('social')).toBeInTheDocument()

    view.rerender(timeline({
      ...day,
      actual_blocks: [{
        ...projection,
        actual_block: { ...actual, name: null, task_type: { ...actual.task_type, name: 'unspecified' } },
      }],
    }))
    expect(screen.getByText('Untitled')).toBeInTheDocument()
    expect(screen.queryByText('unspecified')).not.toBeInTheDocument()
  })
})

describe('DayTimeline initial current-time positioning', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('places today\'s now line one-third down once and does not pull after rerender', () => {
    const scrollBy = vi.spyOn(window, 'scrollBy').mockImplementation(() => undefined)
    const getBoundingClientRect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 600, top: 600, right: 300, bottom: 602, left: 0, width: 300, height: 2,
      toJSON: () => ({}),
    })
    const originalInnerHeight = window.innerHeight
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 })

    const view = render(timeline(day, true))
    expect(scrollBy).toHaveBeenCalledWith({ top: 300, behavior: 'auto' })
    expect(getBoundingClientRect).toHaveBeenCalledTimes(1)
    expect(getBoundingClientRect.mock.instances[0]).toBe(screen.getByTestId('day-now-line').firstElementChild)

    view.rerender(timeline({ ...day }, true))
    fireEvent.scroll(window)
    expect(scrollBy).toHaveBeenCalledTimes(1)

    Object.defineProperty(window, 'innerHeight', { configurable: true, value: originalInnerHeight })
  })

  it('does not scroll another date', () => {
    const scrollBy = vi.spyOn(window, 'scrollBy').mockImplementation(() => undefined)
    render(timeline({ ...day, date: '2026-06-02' }, true))
    expect(scrollBy).not.toHaveBeenCalled()
  })
})
