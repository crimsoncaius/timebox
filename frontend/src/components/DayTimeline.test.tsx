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
