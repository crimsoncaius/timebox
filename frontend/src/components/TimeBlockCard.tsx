import { useCallback, useEffect, useRef, useState } from 'react'
import type { BlockLane, TimeBlock } from '../lib/api'
import {
  formatTimeRangeGcal12,
  MOVE_PREVIEW_BLOCK_HYSTERESIS_MINUTES,
  resolveSameLaneMovePreviewStart,
  SLOT_MINUTES,
} from '../lib/time'
import type { TimeBlockLike } from '../lib/time'
import { blockPrimaryIdentity, blockSecondaryIdentity } from '../lib/blockIdentity'

/** Ignore tiny jitter before moving a block. */
const SWIPE_AXIS_DEAD_ZONE_PX = 8

type DragState =
  | { kind: 'resize'; edge: 'start' | 'end'; start: number; end: number }
  | { kind: 'move'; start: number; end: number }

export function TimeBlockCard({
  block,
  lane,
  visibleStartMin,
  visibleEndMin,
  slotHeightPx,
  readOnly,
  timeEditingDisabled = false,
  sameLaneBlocks,
  resizeMinStartMinute,
  resizeMaxEndMinute,
  getMinuteFromClientY,
  onPatch,
  onBlockClick,
  onDragSessionChange,
  isSelected = false,
}: {
  block: TimeBlock
  lane: BlockLane
  visibleStartMin: number
  /** End of the visible lane (exclusive); used to clamp moves to the rendered window. */
  visibleEndMin: number
  slotHeightPx: number
  readOnly: boolean
  timeEditingDisabled?: boolean
  sameLaneBlocks: TimeBlockLike[]
  resizeMinStartMinute: number
  resizeMaxEndMinute: number
  getMinuteFromClientY: (clientY: number) => number
  onPatch: (patch: {
    task_type_id?: number
    name?: string | null
    note?: string | null
    start_minute?: number
    end_minute?: number
  }) => Promise<void>
  /** Return false to abort (e.g. user cancelled discard). */
  onBlockClick?: () => boolean | void
  /** Fires when a move or resize drag session begins/ends (for global UI such as disabling inspector hit-testing). */
  onDragSessionChange?: (active: boolean) => void
  /** True when this block is the active editor target (matches `selectedBlockId` on the day). */
  isSelected?: boolean
}) {
  const [drag, setDrag] = useState<DragState | null>(null)
  const [pendingLayout, setPendingLayout] = useState<{
    start: number
    end: number
    sourceStart: number
    sourceEnd: number
  } | null>(null)
  const dragRef = useRef<DragState | null>(drag)
  /** Committed move preview (hysteresis at slot boundaries). */
  const prevBlockRef = useRef(block.start_minute)
  const suppressClickRef = useRef(false)
  /** After a successful pointer-down select, skip the redundant click event. */
  const suppressNextClickSelectRef = useRef(false)

  useEffect(() => {
    dragRef.current = drag
  }, [drag])

  useEffect(() => {
    setPendingLayout((pending) => {
      if (!pending) return pending
      if (block.start_minute !== pending.sourceStart || block.end_minute !== pending.sourceEnd) {
        return null
      }
      return pending
    })
  }, [block.end_minute, block.start_minute])

  const displayStart = drag ? drag.start : pendingLayout ? pendingLayout.start : block.start_minute
  const displayEnd = drag ? drag.end : pendingLayout ? pendingLayout.end : block.end_minute
  const displayTop = ((displayStart - visibleStartMin) / SLOT_MINUTES) * slotHeightPx
  const displayHeight = ((displayEnd - displayStart) / SLOT_MINUTES) * slotHeightPx
  const heightPx = Math.max(displayHeight, slotHeightPx)

  const endDrag = useCallback(() => {
    const d = dragRef.current
    dragRef.current = null
    setDrag(null)
    if (!d) return
    try {
      const { start, end } = d
      if (end <= start) return
      if (
        lane === 'planned'
        && (end - start < SLOT_MINUTES || start % SLOT_MINUTES !== 0 || end % SLOT_MINUTES !== 0)
      ) return
      if (start === block.start_minute && end === block.end_minute) return
      setPendingLayout({
        start,
        end,
        sourceStart: block.start_minute,
        sourceEnd: block.end_minute,
      })
      void onPatch({ start_minute: start, end_minute: end }).catch(() => {
        setPendingLayout(null)
      })
    } finally {
      onDragSessionChange?.(false)
    }
  }, [block.end_minute, block.start_minute, lane, onDragSessionChange, onPatch])

  const cancelDrag = useCallback(() => {
    const wasDragging = dragRef.current != null
    dragRef.current = null
    setDrag(null)
    if (wasDragging) onDragSessionChange?.(false)
  }, [onDragSessionChange])

  const startResize = useCallback(
    (edge: 'start' | 'end', e: React.PointerEvent) => {
      e.stopPropagation()
      e.preventDefault()
      const el = e.currentTarget as HTMLElement
      const pointerId = e.pointerId
      const initial: DragState = {
        kind: 'resize',
        edge,
        start: block.start_minute,
        end: block.end_minute,
      }
      dragRef.current = initial
      setDrag(initial)
      onDragSessionChange?.(true)

      const onMove = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        setDrag((d) => {
          if (!d || d.kind !== 'resize') return d
          const m = getMinuteFromClientY(ev.clientY)
          let next: DragState
          if (d.edge === 'start') {
            const ns = Math.min(m, d.end - SLOT_MINUTES)
            next = {
              ...d,
              start: Math.max(resizeMinStartMinute, Math.max(0, ns)),
            }
          } else {
            const ne = Math.max(m, d.start + SLOT_MINUTES)
            next = { ...d, end: Math.min(resizeMaxEndMinute, Math.min(24 * 60, ne)) }
          }
          dragRef.current = next
          return next
        })
      }
      const cleanup = () => {
        window.removeEventListener('pointermove', onMove)
        window.removeEventListener('pointerup', onUp)
        window.removeEventListener('pointercancel', onCancel)
        el.removeEventListener('lostpointercapture', onLostPointerCapture)
      }
      const onUp = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        cleanup()
        try {
          el.releasePointerCapture(pointerId)
        } catch {
          /* ignore */
        }
        endDrag()
      }
      const onCancel = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        cleanup()
        cancelDrag()
      }
      const onLostPointerCapture = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        cleanup()
        cancelDrag()
      }
      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
      window.addEventListener('pointercancel', onCancel)
      el.addEventListener('lostpointercapture', onLostPointerCapture)
      el.setPointerCapture(pointerId)
    },
    [
      block.end_minute,
      block.start_minute,
      cancelDrag,
      endDrag,
      getMinuteFromClientY,
      onDragSessionChange,
      resizeMaxEndMinute,
      resizeMinStartMinute,
    ],
  )

  const onBodyPointerDown = useCallback(
    (e: React.PointerEvent<HTMLButtonElement>) => {
      if (e.button !== 0) return
      e.stopPropagation()
      if (onBlockClick) {
        if (onBlockClick() === false) return
        suppressNextClickSelectRef.current = true
      }
      if (timeEditingDisabled) return
      const el = e.currentTarget
      const pointerId = e.pointerId
      const pointerDownX = e.clientX
      const pointerDownY = e.clientY
      const originStart = block.start_minute
      const originEnd = block.end_minute
      const duration = originEnd - originStart
      let bodyGesture: 'none' | 'move' = 'none'
      let anchorMinute = 0

      const cleanupWindow = () => {
        window.removeEventListener('pointermove', onPointerMove)
        window.removeEventListener('pointerup', onPointerUp)
        window.removeEventListener('pointercancel', onPointerCancel)
        el.removeEventListener('lostpointercapture', onLostPointerCapture)
      }

      const onPointerMove = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        const dx = ev.clientX - pointerDownX
        const dy = ev.clientY - pointerDownY

        if (bodyGesture === 'none') {
          if (Math.abs(dx) < SWIPE_AXIS_DEAD_ZONE_PX && Math.abs(dy) < SWIPE_AXIS_DEAD_ZONE_PX) return
          bodyGesture = 'move'
          anchorMinute = getMinuteFromClientY(ev.clientY)
          prevBlockRef.current = originStart
          const initialMove: DragState = {
            kind: 'move',
            start: originStart,
            end: originEnd,
          }
          dragRef.current = initialMove
          setDrag(initialMove)
          onDragSessionChange?.(true)
          try {
            el.setPointerCapture(pointerId)
          } catch {
            /* ignore */
          }
        }

        const deltaMin = getMinuteFromClientY(ev.clientY) - anchorMinute
        const candidateRaw = originStart + deltaMin
        const maxStartInWindow = Math.max(visibleStartMin, visibleEndMin - duration)

        let blockStart = resolveSameLaneMovePreviewStart(
          sameLaneBlocks,
          block.id,
          duration,
          candidateRaw,
          prevBlockRef.current,
          MOVE_PREVIEW_BLOCK_HYSTERESIS_MINUTES,
        )
        blockStart = Math.min(Math.max(blockStart, visibleStartMin), maxStartInWindow)
        prevBlockRef.current = blockStart

        const next: DragState = {
          kind: 'move',
          start: blockStart,
          end: blockStart + duration,
        }
        dragRef.current = next
        setDrag(next)
      }

      const onPointerUp = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        cleanupWindow()
        if (bodyGesture !== 'none') {
          try {
            el.releasePointerCapture(ev.pointerId)
          } catch {
            /* ignore */
          }
          suppressClickRef.current = true
          endDrag()
        }
      }

      const onPointerCancel = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        cleanupWindow()
        if (bodyGesture !== 'none') {
          suppressClickRef.current = true
          cancelDrag()
        }
      }

      const onLostPointerCapture = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        cleanupWindow()
        if (bodyGesture !== 'none') {
          suppressClickRef.current = true
          cancelDrag()
        }
      }

      window.addEventListener('pointermove', onPointerMove)
      window.addEventListener('pointerup', onPointerUp)
      window.addEventListener('pointercancel', onPointerCancel)
      el.addEventListener('lostpointercapture', onLostPointerCapture)
    },
    [
      block.end_minute,
      block.id,
      block.start_minute,
      cancelDrag,
      endDrag,
      getMinuteFromClientY,
      onBlockClick,
      sameLaneBlocks,
      visibleEndMin,
      visibleStartMin,
      onDragSessionChange,
      timeEditingDisabled,
    ],
  )

  const displayLabel = blockPrimaryIdentity(block)
  const secondaryLabel = blockSecondaryIdentity(block)
  const timeRangeLabel = formatTimeRangeGcal12(displayStart, displayEnd)
  const durationMin = displayEnd - displayStart
  const compactContent = durationMin <= SLOT_MINUTES

  const isDragging = drag != null
  const dragKind =
    drag?.kind === 'move'
      ? 'move'
      : drag?.kind === 'resize' ? 'resize' : undefined

  const laneStripeColor =
    lane === 'planned' ? 'bg-planned' : 'bg-actual'

  const shellClassName = (() => {
    const base = 'absolute left-1 right-1 flex flex-col overflow-hidden rounded-md transition-[box-shadow,background-color,border-color] duration-150'
    if (dragKind === 'move') {
      return `${base} z-30 cursor-grabbing border-0 bg-paper-raised [box-shadow:var(--shadow-engrave-drag)] rotate-[-1.2deg]`
    }
    if (dragKind === 'resize') {
      return `${base} z-30 border-0 bg-paper-raised [box-shadow:var(--shadow-engrave-raise)]`
    }
    if (isSelected) {
      return `${base} z-20 border-0 bg-paper-raised [box-shadow:var(--shadow-engrave-raise)]`
    }
    return `${base} z-10 border-0 bg-paper-soft [box-shadow:var(--shadow-engrave-rest)]`
  })()

  return (
    <div
      data-block
      data-block-id={block.id}
      data-selected={isSelected ? 'true' : undefined}
      data-dragging={isDragging ? 'true' : undefined}
      data-drag-kind={dragKind}
      data-content-density={compactContent ? 'compact' : 'expanded'}
      className={shellClassName}
      style={{
        top: displayTop,
        height: heightPx,
      }}
    >
      {!readOnly && !timeEditingDisabled && dragKind !== 'move' && (
        <button
          type="button"
          aria-label="Resize block start"
          className={[
            'absolute inset-x-0 top-0 z-20 h-2 cursor-ns-resize border-0',
            'bg-paper-groove-bg hover:bg-paper-groove-bg-strong',
            '[box-shadow:var(--shadow-groove-inner)]',
            "before:content-[''] before:absolute before:left-1/2 before:-translate-x-1/2",
            'before:top-[2px] before:h-[1px] before:w-9',
            isSelected ? 'before:bg-paper-rule-ink' : 'before:bg-paper-rule',
            "after:content-[''] after:absolute after:left-1/2 after:-translate-x-1/2",
            'after:top-[4.5px] after:h-[1px] after:w-9',
            isSelected ? 'after:bg-paper-rule-ink' : 'after:bg-paper-rule',
          ].join(' ')}
          onPointerDown={(e) => startResize('start', e)}
        />
      )}
      {readOnly ? (
        <div className="relative flex min-h-0 min-w-0 flex-1 overflow-hidden text-left">
          <CardContent
            compact={compactContent}
            displayLabel={displayLabel}
            timeRangeLabel={timeRangeLabel}
            note={block.note}
            secondaryLabel={secondaryLabel}
            laneStripeColor={laneStripeColor}
            isSelected={isSelected}
          />
        </div>
      ) : (
        <button
          type="button"
          aria-label={`Edit ${lane} block`}
          className={`relative flex min-h-0 min-w-0 flex-1 touch-none overflow-hidden border-0 bg-transparent text-left select-none ${
            drag?.kind === 'move' ? 'cursor-grabbing' : 'cursor-grab'
          }`}
          onPointerDown={onBodyPointerDown}
          onClick={(e) => {
            e.stopPropagation()
            if (suppressClickRef.current) {
              e.preventDefault()
              suppressClickRef.current = false
              suppressNextClickSelectRef.current = false
              return
            }
            if (suppressNextClickSelectRef.current) {
              e.preventDefault()
              suppressNextClickSelectRef.current = false
              return
            }
            onBlockClick?.()
          }}
        >
          <CardContent
            compact={compactContent}
            displayLabel={displayLabel}
            timeRangeLabel={timeRangeLabel}
            note={block.note}
            secondaryLabel={secondaryLabel}
            laneStripeColor={laneStripeColor}
            isSelected={isSelected}
          />
        </button>
      )}
      {!readOnly && !timeEditingDisabled && dragKind !== 'move' && (
        <button
          type="button"
          aria-label="Resize block end"
          className={[
            'absolute inset-x-0 bottom-0 z-20 h-2 cursor-ns-resize border-0',
            'bg-paper-groove-bg hover:bg-paper-groove-bg-strong',
            '[box-shadow:var(--shadow-groove-inner)]',
            "before:content-[''] before:absolute before:left-1/2 before:-translate-x-1/2",
            'before:top-[2px] before:h-[1px] before:w-9',
            isSelected ? 'before:bg-paper-rule-ink' : 'before:bg-paper-rule',
            "after:content-[''] after:absolute after:left-1/2 after:-translate-x-1/2",
            'after:top-[4.5px] after:h-[1px] after:w-9',
            isSelected ? 'after:bg-paper-rule-ink' : 'after:bg-paper-rule',
          ].join(' ')}
          onPointerDown={(e) => startResize('end', e)}
        />
      )}
    </div>
  )
}

function CardContent({
  compact,
  displayLabel,
  timeRangeLabel,
  note,
  secondaryLabel,
  laneStripeColor,
  isSelected,
}: {
  compact: boolean
  displayLabel: string
  timeRangeLabel: string
  note: string | null
  secondaryLabel: string | null
  laneStripeColor: string
  isSelected: boolean
}) {
  const titleClassName = `min-w-0 shrink truncate font-body leading-tight text-on-surface ${
    isSelected ? 'text-[13.5px] font-semibold' : 'text-[12.5px] font-medium'
  }`
  const timeClassName = 'shrink-0 truncate text-[10.5px] font-mono leading-tight text-on-surface-variant'

  return (
    <>
      <span
        aria-hidden
        data-lane-stripe
        className={`absolute top-2 bottom-2 left-0 w-[3px] rounded-[2px] ${laneStripeColor}`}
      />
      <span
        data-block-content
        className={`flex min-h-0 min-w-0 flex-1 overflow-hidden px-3 py-0 ${
          compact
            ? 'flex-row items-center gap-1.5'
            : 'flex-col items-stretch justify-center gap-0.5'
        }`}
      >
        <span data-block-title className={titleClassName}>{displayLabel}</span>
        {compact ? <span aria-hidden className="shrink-0 text-on-surface-variant">·</span> : null}
        <span data-block-time className={timeClassName}>{timeRangeLabel}</span>
        {!compact && secondaryLabel ? (
          <span data-block-context className="min-h-0 truncate font-body text-[9px] leading-tight text-on-surface-variant">
            {secondaryLabel}
          </span>
        ) : null}
        {!compact && note ? (
          <span className="min-h-0 truncate font-body text-[9px] leading-tight text-outline-variant">
            {note}
          </span>
        ) : null}
      </span>
    </>
  )
}
