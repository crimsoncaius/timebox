import { useCallback, useEffect, useRef, useState } from 'react'
import type { BlockLane, TimeBlock } from '../lib/api'
import {
  formatTimeRangeGcal12,
  MOVE_PREVIEW_BLOCK_HYSTERESIS_MINUTES,
  resolveSameLaneMovePreviewStart,
  SLOT_MINUTES,
} from '../lib/time'
import type { TimeBlockLike } from '../lib/time'

/** Drag far enough right before release counts as complete-as-planned. */
const SWIPE_COMPLETE_COMMIT_PX = 72
/** Show the complete affordance copy once the user has pulled this far. */
const SWIPE_COMPLETE_HINT_PX = 28
/** Ignore tiny jitter before choosing move vs swipe-complete. */
const SWIPE_AXIS_DEAD_ZONE_PX = 8
/** Horizontal pull must beat vertical by this ratio to arm swipe-complete. */
const SWIPE_COMPLETE_DOMINANCE = 1.15

/** Resize handle row height (`h-2`) in px; two handles when editable. */
const RESIZE_HANDLE_ROWS_PX = 16
/** Min height for the body (below handles) before showing the time row. */
const MIN_INNER_PX_FOR_TIME = 34
const MIN_INNER_PX_FOR_TIME_WITH_NOTE = 48
/** Inner height needed to show title and time on two lines in side-text mode. */
const MIN_INNER_PX_SIDE_TEXT_TWO_LINES = 30

type DragState =
  | { kind: 'resize'; edge: 'start' | 'end'; start: number; end: number }
  | { kind: 'move'; start: number; end: number }
  | { kind: 'complete'; start: number; end: number; pullPx: number }

export function TimeBlockCard({
  block,
  lane,
  visibleStartMin,
  visibleEndMin,
  slotHeightPx,
  readOnly,
  sameLaneBlocks,
  resizeMinStartMinute,
  resizeMaxEndMinute,
  getMinuteFromClientY,
  onPatch,
  onBlockClick,
  onDragSessionChange,
  onSwipeComplete,
  isSelected = false,
}: {
  block: TimeBlock
  lane: BlockLane
  visibleStartMin: number
  /** End of the visible lane (exclusive); used to clamp moves to the rendered window. */
  visibleEndMin: number
  slotHeightPx: number
  readOnly: boolean
  sameLaneBlocks: TimeBlockLike[]
  resizeMinStartMinute: number
  resizeMaxEndMinute: number
  getMinuteFromClientY: (clientY: number) => number
  onPatch: (patch: {
    task_type_id?: number
    note?: string | null
    start_minute?: number
    end_minute?: number
  }) => Promise<void>
  /** Return false to abort (e.g. user cancelled discard). */
  onBlockClick?: () => boolean | void
  /** Fires when a move or resize drag session begins/ends (for global UI such as disabling inspector hit-testing). */
  onDragSessionChange?: (active: boolean) => void
  /** Planned blocks only: swipe right to complete (same as inspector Complete). */
  onSwipeComplete?: () => Promise<void>
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

  const swipeNudgePx =
    drag?.kind === 'complete' ? Math.min(drag.pullPx * 0.2, 18) : 0

  const endDrag = useCallback(() => {
    const d = dragRef.current
    dragRef.current = null
    setDrag(null)
    if (!d) return
    try {
      if (d.kind === 'complete') {
        if (d.pullPx >= SWIPE_COMPLETE_COMMIT_PX) {
          void onSwipeComplete?.()
        }
        return
      }
      const { start, end } = d
      if (end - start < SLOT_MINUTES) return
      if (start % SLOT_MINUTES !== 0 || end % SLOT_MINUTES !== 0) return
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
  }, [block.end_minute, block.start_minute, onDragSessionChange, onPatch, onSwipeComplete])

  const startResize = useCallback(
    (edge: 'start' | 'end', e: React.PointerEvent) => {
      e.stopPropagation()
      e.preventDefault()
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
      const onUp = (ev: PointerEvent) => {
        window.removeEventListener('pointermove', onMove)
        window.removeEventListener('pointerup', onUp)
        try {
          ;(e.target as HTMLElement).releasePointerCapture(ev.pointerId)
        } catch {
          /* ignore */
        }
        endDrag()
      }
      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
      ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
    },
    [
      block.end_minute,
      block.start_minute,
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
      const el = e.currentTarget
      const pointerId = e.pointerId
      const pointerDownX = e.clientX
      const pointerDownY = e.clientY
      const originStart = block.start_minute
      const originEnd = block.end_minute
      const duration = originEnd - originStart
      /** none: finger still in dead zone; move: vertical reposition; complete: swipe-right to complete */
      let bodyGesture: 'none' | 'move' | 'complete' = 'none'
      let anchorMinute = 0

      const cleanupWindow = () => {
        window.removeEventListener('pointermove', onPointerMove)
        window.removeEventListener('pointerup', onPointerUp)
      }

      const onPointerMove = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        const dx = ev.clientX - pointerDownX
        const dy = ev.clientY - pointerDownY

        if (bodyGesture === 'none') {
          if (Math.abs(dx) < SWIPE_AXIS_DEAD_ZONE_PX && Math.abs(dy) < SWIPE_AXIS_DEAD_ZONE_PX) return
          const favorSwipeComplete =
            !!onSwipeComplete &&
            dx > 0 &&
            dx > Math.abs(dy) * SWIPE_COMPLETE_DOMINANCE &&
            dx >= SWIPE_AXIS_DEAD_ZONE_PX
          if (favorSwipeComplete) {
            bodyGesture = 'complete'
            const initial: DragState = {
              kind: 'complete',
              start: originStart,
              end: originEnd,
              pullPx: Math.max(0, dx),
            }
            dragRef.current = initial
            setDrag(initial)
            onDragSessionChange?.(true)
            try {
              el.setPointerCapture(pointerId)
            } catch {
              /* ignore */
            }
            return
          }
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

        if (bodyGesture === 'complete') {
          const pullPx = Math.max(0, ev.clientX - pointerDownX)
          const next: DragState = {
            kind: 'complete',
            start: originStart,
            end: originEnd,
            pullPx,
          }
          dragRef.current = next
          setDrag(next)
          return
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

      window.addEventListener('pointermove', onPointerMove)
      window.addEventListener('pointerup', onPointerUp)
    },
    [
      block.end_minute,
      block.id,
      block.start_minute,
      endDrag,
      getMinuteFromClientY,
      onBlockClick,
      sameLaneBlocks,
      visibleEndMin,
      visibleStartMin,
      onDragSessionChange,
      onSwipeComplete,
    ],
  )

  const label = block.task?.title?.trim() || block.task_type?.name?.trim() || '(No title)'
  const timeRangeLabel = formatTimeRangeGcal12(displayStart, displayEnd)
  const innerContentPx = heightPx - (readOnly ? 0 : RESIZE_HANDLE_ROWS_PX)
  const innerTextThreshold = block.note ? MIN_INNER_PX_FOR_TIME_WITH_NOTE : MIN_INNER_PX_FOR_TIME
  const useSideTextLayout = drag == null && innerContentPx < innerTextThreshold
  const sideTextTwoLines = innerContentPx >= MIN_INNER_PX_SIDE_TEXT_TWO_LINES
  const showTime =
    !useSideTextLayout &&
    innerContentPx >= (block.note ? MIN_INNER_PX_FOR_TIME_WITH_NOTE : MIN_INNER_PX_FOR_TIME)

  const isDragging = drag != null
  const dragKind =
    drag?.kind === 'move'
      ? 'move'
      : drag?.kind === 'resize'
        ? 'resize'
        : drag?.kind === 'complete'
          ? 'complete'
          : undefined

  const laneStripeColor =
    lane === 'planned' ? 'bg-planned' : 'bg-actual'
  const laneBarClassName = `w-3 shrink-0 rounded-md border border-[rgba(80,70,50,0.25)] dark:border-[rgba(255,250,240,0.18)] bg-paper-soft ${
    isSelected ? `border-l-4 ${lane === 'planned' ? 'border-l-planned' : 'border-l-actual'} pl-0` : ''
  }`

  const showLaneStripe = isSelected || dragKind === 'move'
  const durationMin = displayEnd - displayStart
  const showSelectedMeta = isSelected && durationMin >= 60 && !useSideTextLayout

  const shellClassName = (() => {
    const clip =
      useSideTextLayout && dragKind == null ? 'overflow-visible' : 'overflow-hidden'
    const base = `absolute left-1 right-1 flex flex-col ${clip} rounded-md transition-[box-shadow,background-color,border-color] duration-150`
    if (dragKind === 'move') {
      return `${base} z-30 cursor-grabbing border-0 bg-paper-raised [box-shadow:var(--shadow-engrave-drag)] rotate-[-1.2deg]`
    }
    if (dragKind === 'complete') {
      const armed =
        drag &&
        drag.kind === 'complete' &&
        drag.pullPx >= SWIPE_COMPLETE_COMMIT_PX
      return `${base} z-30 cursor-grabbing ${
        armed
          ? 'border-0 bg-tertiary-container/55 [box-shadow:var(--shadow-engrave-drag)]'
          : 'border border-solid border-[rgba(80,70,50,0.25)] dark:border-[rgba(255,250,240,0.18)] bg-paper-raised'
      }`
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
      className={shellClassName}
      style={{
        top: displayTop,
        height: heightPx,
        transform: swipeNudgePx ? `translateX(${swipeNudgePx}px)` : undefined,
      }}
    >
      {!readOnly && dragKind !== 'move' && (
        <button
          type="button"
          aria-label="Resize block start"
          className={[
            useSideTextLayout ? 'h-[5px]' : 'h-2',
            'w-full shrink-0 cursor-ns-resize border-0 relative',
            'bg-paper-groove-bg hover:bg-paper-groove-bg-strong',
            '[box-shadow:var(--shadow-groove-inner)]',
            ...(!useSideTextLayout ? [
              "before:content-[''] before:absolute before:left-1/2 before:-translate-x-1/2",
              'before:top-[2px] before:h-[1px] before:w-9',
              isSelected ? 'before:bg-paper-rule-ink' : 'before:bg-paper-rule',
              "after:content-[''] after:absolute after:left-1/2 after:-translate-x-1/2",
              'after:top-[4.5px] after:h-[1px] after:w-9',
              isSelected ? 'after:bg-paper-rule-ink' : 'after:bg-paper-rule',
            ] : []),
          ].join(' ')}
          onPointerDown={(e) => startResize('start', e)}
        />
      )}
      {readOnly ? (
        useSideTextLayout ? (
          <div className="flex min-h-0 flex-1 flex-row items-stretch gap-2 overflow-hidden px-1.5 py-0.5">
            <div aria-hidden className={`self-stretch ${laneBarClassName}`} />
            <div className="flex min-h-0 min-w-0 flex-1 flex-col justify-center gap-0.5 overflow-hidden">
              {sideTextTwoLines ? (
                <>
                  <p className="shrink-0 truncate font-body text-[12px] font-medium leading-tight text-on-surface">
                    {label}
                  </p>
                  <p className="shrink-0 truncate font-body text-[10.5px] font-mono leading-tight text-on-surface-variant">
                    {timeRangeLabel}
                  </p>
                </>
              ) : (
                <p className="shrink-0 truncate font-body text-[12px] font-medium leading-tight text-on-surface">
                  {label}
                  <span className="font-normal text-on-surface-variant">
                    {' '}
                    · {timeRangeLabel}
                  </span>
                </p>
              )}
              {block.note && sideTextTwoLines ? (
                <p className="min-h-0 truncate font-body text-[9px] leading-tight text-outline-variant">{block.note}</p>
              ) : null}
            </div>
          </div>
        ) : (
          <div className="flex min-h-0 flex-1 flex-col items-stretch justify-center gap-0.5 overflow-hidden px-3 py-0">
            <p className="shrink-0 truncate font-body text-[12.5px] font-medium leading-tight text-on-surface">
              {label}
            </p>
            {showTime ? (
              <p className="shrink-0 truncate text-[10.5px] font-mono leading-tight text-on-surface-variant">
                {timeRangeLabel}
              </p>
            ) : null}
            {block.note ? (
              <p className="min-h-0 truncate font-body text-[9px] leading-tight text-outline-variant">{block.note}</p>
            ) : null}
          </div>
        )
      ) : (
        <button
          type="button"
          aria-label={`Edit ${lane} block`}
          className={`touch-none flex min-h-0 min-w-0 flex-1 border-0 bg-transparent text-left select-none relative ${
            useSideTextLayout
              ? 'flex-row items-stretch gap-2 overflow-hidden px-1.5 py-0.5'
              : 'flex-col items-stretch justify-center gap-0.5 overflow-hidden px-3 py-0'
          } ${
            drag?.kind === 'move' || drag?.kind === 'complete' ? 'cursor-grabbing' : 'cursor-grab'
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
          {showLaneStripe && !useSideTextLayout && (
            <span
              aria-hidden
              className={`absolute top-2 bottom-2 left-0 w-[2px] rounded-[2px] ${laneStripeColor}`}
            />
          )}
          {showSelectedMeta && (
            <span className="absolute top-1.5 right-2 font-mono text-[9.5px] uppercase tracking-[0.1em] text-on-surface-variant">
              · selected
            </span>
          )}
          {useSideTextLayout ? (
            <>
              <span aria-hidden className={`self-stretch ${laneBarClassName}`} />
              <span className="flex min-h-0 min-w-0 flex-1 flex-col justify-center gap-0.5 overflow-hidden">
                {sideTextTwoLines ? (
                  <>
                    <span className="shrink-0 truncate font-body text-[12px] font-medium leading-tight text-on-surface">
                      {label}
                    </span>
                    <span className="shrink-0 truncate font-body text-[10.5px] font-mono leading-tight text-on-surface-variant">
                      {timeRangeLabel}
                    </span>
                  </>
                ) : (
                  <span className="shrink-0 truncate font-body text-[12px] font-medium leading-tight text-on-surface">
                    {label}
                    <span className="font-normal text-on-surface-variant">
                      {' '}
                      · {timeRangeLabel}
                    </span>
                  </span>
                )}
                {block.note && sideTextTwoLines ? (
                  <span className="min-h-0 truncate font-body text-[9px] leading-tight text-outline-variant">
                    {block.note}
                  </span>
                ) : null}
              </span>
            </>
          ) : (
            <>
              <span className={`shrink-0 truncate font-body leading-tight text-on-surface ${isSelected ? 'text-[13.5px] font-semibold' : 'text-[12.5px] font-medium'}`}>
                {label}
              </span>
              {showTime ? (
                <span className="shrink-0 truncate text-[10.5px] font-mono leading-tight text-on-surface-variant">
                  {timeRangeLabel}
                </span>
              ) : null}
              {block.note ? (
                <span className="min-h-0 truncate font-body text-[9px] leading-tight text-outline-variant">
                  {block.note}
                </span>
              ) : null}
            </>
          )}
        </button>
      )}
      {drag?.kind === 'complete' && drag.pullPx >= SWIPE_COMPLETE_HINT_PX ? (
        <div
          className="pointer-events-none absolute inset-0 z-1 flex items-center justify-center rounded-md bg-surface/75 px-1 dark:bg-dark-background/70"
          aria-hidden
        >
          <span className="text-center font-headline text-[10px] font-medium uppercase tracking-wide text-on-surface">
            {drag.pullPx >= SWIPE_COMPLETE_COMMIT_PX ? 'Release to complete' : 'Complete'}
          </span>
        </div>
      ) : null}
      {!readOnly && dragKind !== 'move' && (
        <button
          type="button"
          aria-label="Resize block end"
          className={[
            useSideTextLayout ? 'h-[5px]' : 'h-2',
            'w-full shrink-0 cursor-ns-resize border-0 relative',
            'bg-paper-groove-bg hover:bg-paper-groove-bg-strong',
            '[box-shadow:var(--shadow-groove-inner)]',
            ...(!useSideTextLayout ? [
              "before:content-[''] before:absolute before:left-1/2 before:-translate-x-1/2",
              'before:top-[2px] before:h-[1px] before:w-9',
              isSelected ? 'before:bg-paper-rule-ink' : 'before:bg-paper-rule',
              "after:content-[''] after:absolute after:left-1/2 after:-translate-x-1/2",
              'after:top-[4.5px] after:h-[1px] after:w-9',
              isSelected ? 'after:bg-paper-rule-ink' : 'after:bg-paper-rule',
            ] : []),
          ].join(' ')}
          onPointerDown={(e) => startResize('end', e)}
        />
      )}
    </div>
  )
}
