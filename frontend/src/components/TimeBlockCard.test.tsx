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

  it('does not call onBlockClick twice for a tap (pointer down + click)', () => {
    const onBlockClick = vi.fn(() => true)
    const { body } = renderCard({ onBlockClick })

    fireEvent.pointerDown(body, { button: 0, pointerId: 1, clientX: 10, clientY: 100 })
    expect(onBlockClick).toHaveBeenCalledTimes(1)

    fireEvent.pointerUp(window, { pointerId: 1, clientX: 10, clientY: 100 })
    fireEvent.click(body)

    expect(onBlockClick).toHaveBeenCalledTimes(1)
  })
})
