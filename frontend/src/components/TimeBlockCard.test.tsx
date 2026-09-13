import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { TimeBlock } from '../lib/api'
import { TimeBlockCard } from './TimeBlockCard'

const originalSetPointerCapture = HTMLElement.prototype.setPointerCapture
const originalReleasePointerCapture = HTMLElement.prototype.releasePointerCapture

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
  lane?: 'planned' | 'actual'
  timeEditingDisabled?: boolean
  isSelected?: boolean
  sameLaneBlocks?: TimeBlock[]
  resizeMaxEndMinute?: number
  resizeMinStartMinute?: number
  onPlacementError?: (message: string) => void
  onPatch?: (patch: { start_minute?: number; end_minute?: number }) => Promise<void>
  onBlockClick?: () => boolean | void
  onDragSessionChange?: (active: boolean) => void
}) {
  const onPatch = overrides?.onPatch ?? vi.fn(() => Promise.resolve())
  const onBlockClick = overrides?.onBlockClick ?? vi.fn(() => true)

  const view = render(
    <div style={{ position: 'relative', height: 400 }}>
      <TimeBlockCard
        block={{ ...block, lane: overrides?.lane ?? 'planned' }}
        lane={overrides?.lane ?? 'planned'}
        timeEditingDisabled={overrides?.timeEditingDisabled}
        visibleStartMin={480}
        visibleEndMin={600}
        slotHeightPx={20}
        readOnly={false}
        sameLaneBlocks={overrides?.sameLaneBlocks ?? [block]}
        resizeMinStartMinute={overrides?.resizeMinStartMinute ?? 0}
        resizeMaxEndMinute={overrides?.resizeMaxEndMinute ?? 1440}
        onPlacementError={overrides?.onPlacementError}
        getMinuteFromClientY={(clientY) => clientY}
        onPatch={onPatch}
        onBlockClick={onBlockClick}
        onDragSessionChange={overrides?.onDragSessionChange}
        isSelected={overrides?.isSelected ?? false}
      />
    </div>,
  )

  const body = screen.getByRole('button', { name: `Edit ${overrides?.lane ?? 'planned'} block` })
  const shell = view.container.querySelector('[data-block-id="10"]') as HTMLDivElement

  return { ...view, body, shell, onPatch, onBlockClick }
}

function dragBlock(body: HTMLElement) {
  fireEvent.pointerDown(body, { button: 0, pointerId: 1, clientX: 10, clientY: 100 })
  fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 120 })
  fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 180 })
  fireEvent.pointerUp(window, { pointerId: 1, clientX: 10, clientY: 180 })
}

function renderIdentityCard(identityBlock: TimeBlock) {
  return render(
    <div style={{ position: 'relative', height: 400 }}>
      <TimeBlockCard
        block={identityBlock}
        lane={identityBlock.lane}
        visibleStartMin={480}
        visibleEndMin={1200}
        slotHeightPx={30}
        readOnly
        sameLaneBlocks={[identityBlock]}
        resizeMinStartMinute={0}
        resizeMaxEndMinute={1440}
        getMinuteFromClientY={(clientY) => clientY}
        onPatch={vi.fn(() => Promise.resolve())}
        isSelected={false}
      />
    </div>,
  )
}

describe('TimeBlockCard', () => {
  beforeEach(() => {
    HTMLElement.prototype.setPointerCapture = vi.fn()
    HTMLElement.prototype.releasePointerCapture = vi.fn()
  })

  afterEach(() => {
    HTMLElement.prototype.setPointerCapture = originalSetPointerCapture
    HTMLElement.prototype.releasePointerCapture = originalReleasePointerCapture
  })

  it('moves Actual Blocks to the nearest gap including a distant original slot', () => {
    const onPlacementError = vi.fn()
    const neighbor = { ...block, id: 11, lane: 'actual' as const, start_minute: 540, end_minute: 600 }
    const view = renderCard({ lane: 'actual', sameLaneBlocks: [block, neighbor], onPlacementError })
    dragBlock(view.body)
    expect(view.onPatch).toHaveBeenCalledWith({ start_minute: 510, end_minute: 540 })
    view.unmount()
    const rejected = renderCard({ lane: 'actual', sameLaneBlocks: [block, { ...neighbor, start_minute: 510 }], onPlacementError })
    dragBlock(rejected.body)
    expect(rejected.onPatch).not.toHaveBeenCalled()
    expect(onPlacementError).not.toHaveBeenCalled()
  })

  it('clamps Actual resize at exact neighbor boundaries', () => {
    const neighbor = { ...block, id: 11, start_minute: 547, end_minute: 600 }
    const { onPatch } = renderCard({ lane: 'actual', sameLaneBlocks: [block, neighbor], resizeMaxEndMinute: 547 })
    const edge = screen.getByRole('button', { name: 'Resize block end' })
    fireEvent.pointerDown(edge, { button: 0, pointerId: 1, clientY: 510 })
    fireEvent.pointerMove(window, { pointerId: 1, clientY: 600 })
    fireEvent.pointerUp(window, { pointerId: 1, clientY: 600 })
    expect(onPatch).toHaveBeenCalledWith({ start_minute: 480, end_minute: 547 })
  })

  it('keeps running Actual time fixed while allowing selection', () => {
    const { body, onPatch, onBlockClick } = renderCard({ lane: 'actual', timeEditingDisabled: true })
    dragBlock(body)
    expect(onPatch).not.toHaveBeenCalled()
    expect(onBlockClick).toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Resize block end' })).not.toBeInTheDocument()
  })

  it('commits an end resize at an exact off-grid neighbor boundary', () => {
    const neighbor = { ...block, id: 11, start_minute: 547, end_minute: 600 }
    const { onPatch } = renderCard({ sameLaneBlocks: [block, neighbor], resizeMaxEndMinute: 547 })
    const edge = screen.getByRole('button', { name: 'Resize block end' })
    fireEvent.pointerDown(edge, { button: 0, pointerId: 1, clientY: 510 })
    fireEvent.pointerMove(window, { pointerId: 1, clientY: 600 })
    fireEvent.pointerUp(window, { pointerId: 1, clientY: 600 })
    expect(onPatch).toHaveBeenCalledWith({ start_minute: 480, end_minute: 547 })
  })

  it('keeps the original slot when it is the closest available space', () => {
    const onPlacementError = vi.fn()
    const neighbor = { ...block, id: 11, start_minute: 510, end_minute: 600 }
    const { body, onPatch, shell } = renderCard({ sameLaneBlocks: [block, neighbor], onPlacementError })
    dragBlock(body) // intended start 540; the only available start is 480 (60 minutes away)
    expect(onPatch).not.toHaveBeenCalled()
    expect(onPlacementError).not.toHaveBeenCalled()
    expect(shell.style.top).toBe('0px')
  })

  it('ignores movement direction and saves the closest start shown in the preview', () => {
    const neighbor = { ...block, id: 11, start_minute: 540, end_minute: 600 }
    const { body, onPatch } = renderCard({ sameLaneBlocks: [block, neighbor] })
    dragBlock(body) // 510 is closer than the later side at 600
    expect(onPatch).toHaveBeenCalledWith({ start_minute: 510, end_minute: 540 })
  })

  it('rejects a preview occupied immediately before release without selecting another slot', () => {
    const onPatch = vi.fn(() => Promise.resolve())
    const onPlacementError = vi.fn()
    const view = renderCard({ onPatch, onPlacementError })
    fireEvent.pointerDown(view.body, { button: 0, pointerId: 1, clientX: 10, clientY: 100 })
    fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 180 })
    expect(view.shell.style.top).toBe('40px')
    const neighbor = { ...block, id: 11, start_minute: 540, end_minute: 570 }
    view.rerender(<div style={{ position: 'relative', height: 400 }}>
      <TimeBlockCard block={block} lane="planned" visibleStartMin={480} visibleEndMin={600}
        slotHeightPx={20} readOnly={false} sameLaneBlocks={[block, neighbor]}
        resizeMinStartMinute={480} resizeMaxEndMinute={540}
        getMinuteFromClientY={y => y} onPatch={onPatch} onPlacementError={onPlacementError} />
    </div>)
    fireEvent.pointerUp(window, { pointerId: 1, clientX: 10, clientY: 180 })
    expect(onPatch).not.toHaveBeenCalled()
    expect(onPlacementError).toHaveBeenCalledWith('That time is no longer available')
    expect(view.shell.style.top).toBe('0px')
  })

  it('shows linked Block Name as Day identity and the Battle Plan Task as context', () => {
    const linked = {
      ...block,
      name: 'Outline session',
      task_id: 42,
      task: { id: 42, title: 'Prepare launch', status: 'in_progress' as const, task_type_id: 1 },
      end_minute: 600,
    }
    const view = renderIdentityCard(linked)
    expect(screen.getByText('Outline session')).toBeInTheDocument()
    expect(screen.getByText('Prepare launch')).toHaveAttribute('data-block-context')

    const unnamed: TimeBlock = { ...linked, name: null }
    view.rerender(
      <div style={{ position: 'relative', height: 400 }}>
        <TimeBlockCard
          block={unnamed}
          lane="planned"
          visibleStartMin={480}
          visibleEndMin={1200}
          slotHeightPx={30}
          readOnly
          sameLaneBlocks={[unnamed]}
          resizeMinStartMinute={0}
          resizeMaxEndMinute={1440}
          getMinuteFromClientY={(clientY) => clientY}
          onPatch={vi.fn(() => Promise.resolve())}
          isSelected={false}
        />
      </div>,
    )
    expect(screen.getByText('Prepare launch')).toHaveAttribute('data-block-title')
    expect(screen.getByText('alpha')).toHaveAttribute('data-block-context')
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

  it('cancels a move without persisting and remains draggable after pointer cancellation', () => {
    const onPatch = vi.fn(() => Promise.resolve())
    const onDragSessionChange = vi.fn()
    const { body, shell } = renderCard({ onPatch, onDragSessionChange })

    fireEvent.pointerDown(body, { button: 0, pointerId: 1, clientX: 10, clientY: 100 })
    fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 120 })
    fireEvent.pointerMove(window, { pointerId: 1, clientX: 10, clientY: 180 })
    expect(shell).toHaveAttribute('data-dragging', 'true')

    fireEvent.pointerCancel(window, { pointerId: 1, clientX: 10, clientY: 180 })

    expect(shell).not.toHaveAttribute('data-dragging')
    expect(shell.style.top).toBe('0px')
    expect(onPatch).not.toHaveBeenCalled()
    expect(onDragSessionChange).toHaveBeenNthCalledWith(1, true)
    expect(onDragSessionChange).toHaveBeenNthCalledWith(2, false)

    dragBlock(body)

    expect(onPatch).toHaveBeenCalledWith({ start_minute: 540, end_minute: 570 })
    expect(onPatch).toHaveBeenCalledTimes(1)
  })

  it('cancels a resize without persisting and remains resizable after pointer capture is lost', () => {
    const onPatch = vi.fn(() => Promise.resolve())
    const onDragSessionChange = vi.fn()
    const { shell } = renderCard({ onPatch, onDragSessionChange })
    const resizeEnd = screen.getByRole('button', { name: 'Resize block end' })

    fireEvent.pointerDown(resizeEnd, { button: 0, pointerId: 2, clientY: 510 })
    fireEvent.pointerMove(window, { pointerId: 2, clientY: 540 })
    expect(shell).toHaveAttribute('data-dragging', 'true')

    fireEvent.lostPointerCapture(resizeEnd, { pointerId: 2 })

    expect(shell).not.toHaveAttribute('data-dragging')
    expect(shell.style.height).toBe('20px')
    expect(onPatch).not.toHaveBeenCalled()
    expect(onDragSessionChange).toHaveBeenNthCalledWith(1, true)
    expect(onDragSessionChange).toHaveBeenNthCalledWith(2, false)

    fireEvent.pointerDown(resizeEnd, { button: 0, pointerId: 3, clientY: 510 })
    fireEvent.pointerMove(window, { pointerId: 3, clientY: 540 })
    fireEvent.pointerUp(window, { pointerId: 3, clientY: 540 })

    expect(onPatch).toHaveBeenCalledWith({ start_minute: 480, end_minute: 540 })
    expect(onPatch).toHaveBeenCalledTimes(1)
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

  it('shows title and time inline for a 30-minute block', () => {
    render(
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
          onPatch={vi.fn(() => Promise.resolve())}
          isSelected={false}
        />
      </div>,
    )
    expect(screen.getByText(/8 – 8:30am/)).toBeInTheDocument()
    expect(screen.getByText(/alpha/)).toBeInTheDocument()
  })

  it('keeps one card identity while adapting content density to duration', () => {
    const hourBlock: TimeBlock = { ...block, id: 11, start_minute: 540, end_minute: 600 }
    const view = render(
      <>
        <div style={{ position: 'relative', height: 60 }}>
          <TimeBlockCard
            block={block}
            lane="planned"
            visibleStartMin={480}
            visibleEndMin={600}
            slotHeightPx={46}
            readOnly={false}
            sameLaneBlocks={[block]}
            resizeMinStartMinute={0}
            resizeMaxEndMinute={1440}
            getMinuteFromClientY={(clientY) => clientY}
            onPatch={vi.fn(() => Promise.resolve())}
          />
        </div>
        <div style={{ position: 'relative', height: 100 }}>
          <TimeBlockCard
            block={hourBlock}
            lane="planned"
            visibleStartMin={540}
            visibleEndMin={660}
            slotHeightPx={46}
            readOnly={false}
            sameLaneBlocks={[hourBlock]}
            resizeMinStartMinute={0}
            resizeMaxEndMinute={1440}
            getMinuteFromClientY={(clientY) => clientY}
            onPatch={vi.fn(() => Promise.resolve())}
          />
        </div>
      </>,
    )

    const compact = view.container.querySelector('[data-block-id="10"]') as HTMLElement
    const expanded = view.container.querySelector('[data-block-id="11"]') as HTMLElement
    const compactStripe = compact.querySelector('[data-lane-stripe]') as HTMLElement
    const expandedStripe = expanded.querySelector('[data-lane-stripe]') as HTMLElement
    const compactTitle = compact.querySelector('[data-block-title]') as HTMLElement
    const expandedTitle = expanded.querySelector('[data-block-title]') as HTMLElement
    const compactTime = compact.querySelector('[data-block-time]') as HTMLElement
    const expandedTime = expanded.querySelector('[data-block-time]') as HTMLElement

    expect(compact).toHaveAttribute('data-content-density', 'compact')
    expect(expanded).toHaveAttribute('data-content-density', 'expanded')
    expect(compactStripe).toBeInTheDocument()
    expect(expandedStripe).toBeInTheDocument()
    expect(compactStripe.className).toBe(expandedStripe.className)
    expect(compactTitle.className).toBe(expandedTitle.className)
    expect(compactTime.className).toBe(expandedTime.className)
    expect(compact.querySelector('[data-block-content]')).toHaveClass('flex-row')
    expect(expanded.querySelector('[data-block-content]')).toHaveClass('flex-col')
    expect(compact.querySelector('[aria-label="Resize block start"]')).toHaveClass('absolute')
  })

  it('uses the resting paper surface for a compact block', () => {
    const { shell } = renderCard()

    expect(shell).toHaveClass('bg-paper-soft')
    expect(shell.className).toContain('[box-shadow:var(--shadow-engrave-rest)]')
  })

  it('uses the raised paper surface for a selected compact block', () => {
    const { shell } = renderCard({ isSelected: true })

    expect(shell).toHaveClass('bg-paper-raised')
    expect(shell.className).toContain('[box-shadow:var(--shadow-engrave-raise)]')
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
