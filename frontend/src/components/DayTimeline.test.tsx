import { DragDropProvider } from '@dnd-kit/react'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DayRead } from '../lib/api'
import { DayTimeline } from './DayTimeline'
import { nowLineScrollDelta } from '../lib/dayView'

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

function renderTimeline(onDraftTimeChange: (startMin: number, endMin: number) => void, dayValue = day) {
  const view = render(
    <DragDropProvider>
      <DayTimeline now={Date.now}
        day={dayValue}
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

function timeline(dayValue: DayRead, autoScrollToNow = false, scrollToNowRequest = 0) {
  return (
    <DragDropProvider>
      <DayTimeline now={Date.now}
        day={dayValue}
        readOnly={false}
        draft={null}
        selectedBlockId={null}
        onLaneSlotClick={vi.fn()}
        onPatchBlock={vi.fn(() => Promise.resolve())}
        autoScrollToNow={autoScrollToNow}
        scrollToNowRequest={scrollToNowRequest}
      />
    </DragDropProvider>
  )
}

function mockNowLineGeometry(lineTop: number, timelineTop: number, viewportHeight: number) {
  const scrollBy = vi.spyOn(window, 'scrollBy').mockImplementation(() => undefined)
  Object.defineProperty(window, 'innerHeight', { configurable: true, value: viewportHeight })
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
    const top = this.dataset.testid === 'day-timeline' ? timelineTop : lineTop
    return {
      x: 0, y: top, top, right: 300, bottom: top + 2, left: 0, width: 300, height: 2,
      toJSON: () => ({}),
    }
  })
  return scrollBy
}

describe('DayTimeline half-hour creation requests', () => {
  it.each([false, true])('floors clicks in both lanes with task selection %s', (placementSelected) => {
    const clicked = vi.fn()
    const view = render(<DragDropProvider>
      <DayTimeline now={Date.now} day={day} readOnly={false} draft={null} selectedBlockId={null}
        placementSelected={placementSelected} onLaneSlotClick={clicked} onPatchBlock={vi.fn()} />
    </DragDropProvider>)
    for (const lane of ['planned', 'actual']) {
      const node = view.container.querySelector<HTMLElement>(`[data-day-lane="${lane}"]`)!
      const slotHeight = Number(node.dataset.slotHeight)
      const target = lane === 'planned' && placementSelected
        ? screen.getByTestId('planned-placement-target') : node
      for (const [minute, expected] of [[980, 960], [990, 990], [1199, 1170]]) {
        fireEvent.click(target, { clientY: (minute - 480) / 30 * slotHeight })
        expect(clicked).toHaveBeenLastCalledWith(lane, expected, expected + 30)
      }
    }
  })
})

describe('DayTimeline draft resize', () => {
  beforeEach(() => {
    HTMLElement.prototype.setPointerCapture = vi.fn()
    HTMLElement.prototype.releasePointerCapture = vi.fn()
  })

  afterEach(() => {
    HTMLElement.prototype.setPointerCapture = originalSetPointerCapture
    HTMLElement.prototype.releasePointerCapture = originalReleasePointerCapture
  })

  it('moves an Actual draft and clamps its resize at the current-time boundary', () => {
    const changed = vi.fn()
    render(<DragDropProvider><DayTimeline now={Date.now} day={day} readOnly={false}
      draft={{ lane: 'actual', start_minute: 660, end_minute: 690 }} selectedBlockId={null}
      onLaneSlotClick={vi.fn()} onPatchBlock={vi.fn()} onDraftTimeChange={changed} />
    </DragDropProvider>)
    const edge = screen.getByRole('button', { name: 'Resize draft block end (Actual)' })
    fireEvent.pointerDown(edge, { button: 0, pointerId: 1, clientY: 322 })
    fireEvent.pointerMove(window, { pointerId: 1, clientY: 460 })
    fireEvent.pointerUp(window, { pointerId: 1, clientY: 460 })
    expect(changed).toHaveBeenCalledWith(660, 720)
    expect(screen.getByRole('button', { name: 'Move draft actual block' })).toBeInTheDocument()
  })

  it('shows the draft Block Duration alongside its range', () => {
    renderTimeline(vi.fn())
    expect(screen.getByRole('button', { name: 'Move draft planned block' })).toHaveTextContent('8 – 8:30am · 30m')
  })

  it('resizes a draft exactly to a neighboring off-grid boundary', () => {
    const changed = vi.fn()
    const neighbor = { id: 1, lane: 'planned' as const, start_minute: 547, end_minute: 600,
      task_type_id: 1, task_type: { id: 1, name: 'work', created_at: '', updated_at: '' },
      note: null, created_at: '', updated_at: '' }
    const { resizeEnd } = renderTimeline(changed, { ...day, time_blocks: [neighbor] })
    fireEvent.pointerDown(resizeEnd, { button: 0, pointerId: 4, clientY: 46 })
    fireEvent.pointerMove(window, { pointerId: 4, clientY: 184 })
    fireEvent.pointerUp(window, { pointerId: 4, clientY: 184 })
    expect(changed).toHaveBeenCalledWith(480, 547)
  })

  it('moves a draft into the closest fitting gap without shortening it', () => {
    const changed = vi.fn()
    const neighbor = { id: 1, lane: 'planned' as const, start_minute: 540, end_minute: 600,
      task_type_id: 1, task_type: { id: 1, name: 'work', created_at: '', updated_at: '' },
      note: null, created_at: '', updated_at: '' }
    renderTimeline(changed, { ...day, time_blocks: [neighbor] })
    const body = screen.getByRole('button', { name: 'Move draft planned block' })
    fireEvent.pointerDown(body, { button: 0, pointerId: 4, clientY: 20 })
    fireEvent.pointerMove(window, { pointerId: 4, clientY: 112 })
    fireEvent.pointerUp(window, { pointerId: 4, clientY: 112 })
    expect(changed).toHaveBeenCalledWith(510, 540)
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

  it('moves an Actual Block preserving its off-grid start and duration through the lane-aware patch callback', () => {
    const onPatchBlock = vi.fn(() => Promise.resolve())
    render(
      <DragDropProvider>
        <DayTimeline now={Date.now}
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
      { start_minute: 593, end_minute: 622 },
      'actual',
    )
  })

  it('identifies standalone Actual Blocks by Name, meaningful Task Type, then Unnamed activity', () => {
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
    expect(screen.getByText('Unnamed activity')).toBeInTheDocument()
    expect(screen.queryByText('unspecified')).not.toBeInTheDocument()
  })
})

describe('DayTimeline hour gutter placement', () => {
  it('locks the hour gutter to the same grid row as the lanes', () => {
    const view = render(timeline({
      ...day,
      time_blocks: [{
        id: 7, lane: 'planned', start_minute: 540, end_minute: 600,
        task_type_id: 1, task_type: { id: 1, name: 'focus', created_at: '', updated_at: '' },
        name: 'Morning', note: null, created_at: '', updated_at: '',
      }],
    }))
    const gutter = view.container.querySelector('[data-hour="540"]')?.parentElement?.parentElement
    expect(gutter?.className).toMatch(/col-start-1/)
    expect(gutter?.className).toMatch(/row-start-2/)
    const planned = view.container.querySelector('[data-day-lane="planned"]')
    expect(planned?.className).toMatch(/row-start-2/)
  })
})

describe('Now Line scroll target', () => {
  it('places the Now Line one-third down the visible timeline', () => {
    expect(nowLineScrollDelta(600, 0, 900)).toBe(300)
    expect(nowLineScrollDelta(600, 300, 900)).toBe(100)
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

  it('places today\'s Now Line one-third down the visible timeline once and does not pull after rerender', () => {
    const scrollBy = mockNowLineGeometry(600, 0, 900)

    const view = render(timeline(day, true))
    expect(scrollBy).toHaveBeenCalledWith({ top: 300, behavior: 'auto' })

    view.rerender(timeline({ ...day }, true))
    fireEvent.scroll(window)
    expect(scrollBy).toHaveBeenCalledTimes(1)
  })

  it('accounts for chrome above the timeline', () => {
    const scrollBy = mockNowLineGeometry(600, 300, 900)
    render(timeline(day, true))
    expect(scrollBy).toHaveBeenCalledWith({ top: 100, behavior: 'auto' })
  })

  it('re-scrolls when Today is requested again', () => {
    const scrollBy = mockNowLineGeometry(600, 0, 900)
    const view = render(timeline(day, true, 0))
    expect(scrollBy).toHaveBeenCalledTimes(1)
    view.rerender(timeline(day, true, 1))
    expect(scrollBy).toHaveBeenCalledTimes(2)
  })

  it('does not scroll another date', () => {
    const scrollBy = mockNowLineGeometry(600, 0, 900)
    render(timeline({ ...day, date: '2026-06-02' }, true))
    expect(scrollBy).not.toHaveBeenCalled()
  })

  it('does not scroll when the Now Line is outside the hours shown, including after time later enters range', () => {
    const scrollBy = mockNowLineGeometry(600, 0, 900)
    vi.setSystemTime(new Date('2026-06-01T03:00:00Z'))
    render(timeline(day, true))
    expect(scrollBy).not.toHaveBeenCalled()

    vi.setSystemTime(new Date('2026-06-01T12:00:00Z'))
    act(() => { vi.advanceTimersByTime(30_000) })
    expect(scrollBy).not.toHaveBeenCalled()
  })
})


describe('DayTimeline authoritative clock', () => {
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks() })

  it('shows whole-minute running labels without rounding second-based geometry', () => {
    const now = () => Date.parse('2026-06-01T10:23:21Z')
    const onPatchBlock = vi.fn()
    render(<DragDropProvider><DayTimeline now={now} day={{ ...day, actual_blocks: [{
      date: day.date, start_minute: 540, end_minute: 570, duration_minutes: 30,
      actual_block: { id: 1, task_type_id: 1, task_type: { id: 1, name: 'Work', created_at: '', updated_at: '' },
        task_id: null, task: null, name: null, note: null, planned_block_id: null,
        start_at: '2026-06-01T09:00:00Z', end_at: null, created_at: '', updated_at: '' },
    }] }} readOnly={false} draft={null} selectedBlockId={null}
      onLaneSlotClick={vi.fn()} onPatchBlock={onPatchBlock} /></DragDropProvider>)
    expect(screen.getByText('9 – 10:23am')).toBeInTheDocument()
    const block = screen.getByRole('button', { name: 'Edit actual block' })
    expect(block).toHaveAccessibleDescription('Work, 9 – 10:23am, 1h 23m')
    expect(parseFloat(block.parentElement!.style.height)).toBeCloseTo((623.35 - 540) / 30 * 46)
    const line = screen.getByTestId('day-now-line').firstElementChild as HTMLElement
    expect(parseFloat(line.style.top)).toBeCloseTo((623.35 - 480) / 30 * 46)
    expect(onPatchBlock).not.toHaveBeenCalled()
  })

  it('shows the whole Block Duration of an Actual Block that began the previous day', () => {
    const now = () => Date.parse('2026-06-01T12:00:00Z')
    render(<DragDropProvider><DayTimeline now={now} day={{ ...day, actual_blocks: [{
      date: day.date, start_minute: 0, end_minute: 90, duration_minutes: 90,
      actual_block: { id: 1, task_type_id: 1, task_type: { id: 1, name: 'Work', created_at: '', updated_at: '' },
        task_id: null, task: null, name: null, note: null, planned_block_id: null,
        start_at: '2026-05-31T23:00:00Z', end_at: '2026-06-01T01:30:00Z', created_at: '', updated_at: '' },
    }] }} readOnly={false} draft={null} selectedBlockId={null}
      onLaneSlotClick={vi.fn()} onPatchBlock={vi.fn()} /></DragDropProvider>)
    const block = screen.getByRole('button', { name: 'Edit actual block' })
    expect(block).toHaveAccessibleDescription('Work, 12 – 1:30am, 2h 30m')
  })

  it('shows the Block Duration beside a Planned Block range', () => {
    const now = () => Date.parse('2026-06-01T12:00:00Z')
    render(<DragDropProvider><DayTimeline now={now} day={{ ...day, time_blocks: [{
      id: 7, lane: 'planned', task_type_id: 1, task_type: { id: 1, name: 'Work', created_at: '', updated_at: '' },
      note: null, start_minute: 540, end_minute: 630, created_at: '', updated_at: '',
    }] }} readOnly={false} draft={null} selectedBlockId={null}
      onLaneSlotClick={vi.fn()} onPatchBlock={vi.fn()} /></DragDropProvider>)
    const block = screen.getByRole('button', { name: 'Edit planned block' })
    expect(block).toHaveAccessibleDescription('Work, 9 – 10:30am, 1h 30m')
    expect(block.querySelector('[data-block-duration]')).toHaveTextContent('1h 30m')
  })

  it('positions the line and running range from the same live instant', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2030-01-01T12:00:00Z'))
    const anchor = Date.now()
    const now = () => Date.parse('2026-06-01T12:00:00Z') + Date.now() - anchor
    const onPatchBlock = vi.fn()
    render(<DragDropProvider><DayTimeline now={now} day={{ ...day, actual_blocks: [{
      date: day.date, start_minute: 660, end_minute: 690, duration_minutes: 30,
      actual_block: { id: 1, task_type_id: 1, task_type: { id: 1, name: 'Work', created_at: '', updated_at: '' },
        task_id: null, task: null, name: null, note: null, planned_block_id: null,
        start_at: '2026-06-01T11:00:00Z', end_at: null, created_at: '', updated_at: '' },
    }] }} readOnly={false} draft={null} selectedBlockId={null}
      onLaneSlotClick={vi.fn()} onPatchBlock={onPatchBlock} /></DragDropProvider>)
    const line = screen.getByTestId('day-now-line').firstElementChild as HTMLElement
    expect(line.style.top).toBe('368px')
    expect(screen.getByText('11am – 12pm')).toBeInTheDocument()
    act(() => vi.advanceTimersByTime(60_000))
    expect(parseFloat(line.style.top)).toBeCloseTo(369.5333)
    expect(screen.getByText('11am – 12:01pm')).toBeInTheDocument()
    expect(onPatchBlock).not.toHaveBeenCalled()
  })

  it('uses reporting time for the Now Line and updates across midnight with a skewed device clock', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2030-01-01T12:00:00Z'))
    const anchor = Date.now()
    const now = () => Date.parse('2026-06-01T15:59:30Z') + Date.now() - anchor
    const renderDay = (date: string) => <DragDropProvider><DayTimeline
      day={{ ...day, date, show_full_day: true, meta: { ...day.meta, timezone: 'Asia/Singapore' } }}
      now={now} readOnly={false} draft={null} selectedBlockId={null}
      onLaneSlotClick={vi.fn()} onPatchBlock={vi.fn()} /></DragDropProvider>
    const view = render(renderDay('2026-06-01'))
    expect(screen.getByTestId('day-now-line')).toBeInTheDocument()
    view.rerender(renderDay('2026-06-02'))
    expect(screen.queryByTestId('day-now-line')).not.toBeInTheDocument()
    act(() => vi.advanceTimersByTime(30_000))
    expect(screen.getByTestId('day-now-line')).toBeInTheDocument()
    view.rerender(renderDay('2026-06-01'))
    expect(screen.queryByTestId('day-now-line')).not.toBeInTheDocument()
  })
})
