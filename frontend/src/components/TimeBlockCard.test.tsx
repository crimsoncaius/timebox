import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { TimeBlock } from '../lib/api'
import { TimeBlockCard } from './TimeBlockCard'

const block: TimeBlock = {
  id: 10,
  lane: 'planned',
  task_type_id: 1,
  task_type: { id: 1, name: 'alpha', created_at: '', updated_at: '' },
  note: null,
  start_minute: 480,
  end_minute: 510,
  created_at: '',
  updated_at: '',
}

function renderCard(overrides?: {
  onPatch?: (patch: { start_minute?: number; end_minute?: number }) => Promise<void>
  onBlockClick?: () => boolean | void
}) {
  const onPatch = overrides?.onPatch ?? vi.fn(() => Promise.resolve())
  const onBlockClick = overrides?.onBlockClick ?? vi.fn(() => true)

  const view = render(
    <div style={{ position: 'relative', height: 400 }}>
      <TimeBlockCard
        block={block}
        lane="planned"
        visibleStartMin={480}
        visibleEndMin={600}
        slotHeightPx={20}
        readOnly={false}
        sameLaneBlocks={[block]}
        resizeMinStartMinute={0}
        resizeMaxEndMinute={1440}
        getMinuteFromClientY={(clientY) => clientY}
        onPatch={onPatch}
        onBlockClick={onBlockClick}
        isSelected={false}
      />
    </div>,
  )

  const body = screen.getByRole('button', { name: 'Edit planned block' })
  const shell = view.container.querySelector('[data-block-id="10"]') as HTMLDivElement

  return { ...view, body, shell, onPatch, onBlockClick }
}

function dragBlock(body: HTMLElement) {
  fireEvent.pointerDown(body, { button: 0, pointerId: 1, clientX: 10, clientY: 100 })
  fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 120 })
  fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 180 })
  fireEvent.pointerUp(window, { pointerId: 1, clientX: 10, clientY: 180 })
}

describe('TimeBlockCard', () => {
  beforeEach(() => {
    HTMLElement.prototype.setPointerCapture = vi.fn()
    HTMLElement.prototype.releasePointerCapture = vi.fn()
  })

  it('keeps the moved position visible until the async patch settles', () => {
    const patchControl: { resolve: (() => void) | null } = { resolve: null }
    const onPatch = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          patchControl.resolve = resolve
        }),
    )
    const { body, shell } = renderCard({ onPatch })

    expect(shell.style.top).toBe('0px')

    dragBlock(body)

    expect(onPatch).toHaveBeenCalledWith({ start_minute: 540, end_minute: 570 })
    expect(shell.style.top).toBe('40px')

    patchControl.resolve?.()
  })

  it('selects the block on pointer down when starting a drag', () => {
    const onBlockClick = vi.fn(() => true)
    const { body } = renderCard({ onBlockClick })

    dragBlock(body)

    expect(onBlockClick).toHaveBeenCalledTimes(1)
  })

  it('shows a time range under the title when the block is tall enough', () => {
    const wide: TimeBlock = { ...block, end_minute: 600 }
    render(
      <div style={{ position: 'relative', height: 400 }}>
        <TimeBlockCard
          block={wide}
          lane="planned"
          visibleStartMin={480}
          visibleEndMin={1200}
          slotHeightPx={30}
          readOnly={false}
          sameLaneBlocks={[wide]}
          resizeMinStartMinute={0}
          resizeMaxEndMinute={1440}
          getMinuteFromClientY={(clientY) => clientY}
          onPatch={vi.fn(() => Promise.resolve())}
          isSelected={false}
        />
      </div>,
    )
    expect(screen.getByText('8 – 10am')).toBeInTheDocument()
  })

  it('does not call onBlockClick twice for a tap (pointer down + click)', () => {
    const onBlockClick = vi.fn(() => true)
    const { body } = renderCard({ onBlockClick })

    fireEvent.pointerDown(body, { button: 0, pointerId: 1, clientX: 10, clientY: 100 })
    expect(onBlockClick).toHaveBeenCalledTimes(1)

    fireEvent.pointerUp(window, { pointerId: 1, clientX: 10, clientY: 100 })
    fireEvent.click(body)

    expect(onBlockClick).toHaveBeenCalledTimes(1)
  })

  it('does not oscillate preview top when moving through a same-lane gap past a blocker', () => {
    const blocker: TimeBlock = {
      id: 11,
      lane: 'planned',
      task_type_id: 2,
      task_type: { id: 2, name: 'beta', created_at: '', updated_at: '' },
      note: null,
      start_minute: 540,
      end_minute: 600,
      created_at: '',
      updated_at: '',
    }

    const onPatch = vi.fn(() => Promise.resolve())
    const view = render(
      <div style={{ position: 'relative', height: 800 }}>
        <TimeBlockCard
          block={block}
          lane="planned"
          visibleStartMin={480}
          visibleEndMin={1200}
          slotHeightPx={20}
          readOnly={false}
          sameLaneBlocks={[block, blocker]}
          resizeMinStartMinute={0}
          resizeMaxEndMinute={540}
          getMinuteFromClientY={(clientY) => clientY}
          onPatch={onPatch}
          isSelected={false}
        />
      </div>,
    )

    const body = screen.getByRole('button', { name: 'Edit planned block' })
    const shell = view.container.querySelector('[data-block-id="10"]') as HTMLDivElement

    /**
     * clientY is absolute minute. Body pointer gesture ignores moves until 8px from pointer-down (dead zone).
     * First move past dead zone anchors the vertical axis; then raw = originStart + (clientY - anchor).
     */
    fireEvent.pointerDown(body, { button: 0, pointerId: 1, clientX: 10, clientY: 480 })
    fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 489 })
    /** anchor = 489; raw 510 → preview commits at 510 */
    fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 519 })
    expect(shell.style.top).toBe('20px')

    fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 550 })
    expect(shell.style.top).toBe('20px')

    fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 530 })
    expect(shell.style.top).toBe('20px')

    /** raw ≥ 592 switches block to 600; use anchor 489 → clientY 489 + 112 = 601 */
    fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 601 })
    expect(shell.style.top).toBe('80px')

    fireEvent.pointerUp(window, { pointerId: 1, clientX: 10, clientY: 601 })

    expect(onPatch).toHaveBeenCalledWith({ start_minute: 600, end_minute: 630 })
  })
})
