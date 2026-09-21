import { useDroppable } from '@dnd-kit/react'
import { forwardRef, useCallback, useEffect, useRef, useState } from 'react'
import type { BlockDraftPlacement, BlockLane, DayRead, TimeBlock } from '../lib/api'
import { nowLineScrollDelta } from '../lib/dayView'
import {
  calendarIsoDateInTimeZone,
  formatHourLabelGcal12,
  formatTimeRangeGcal12,
  gapBoundsForDraft,
  MINUTES_PER_DAY,
  minuteFromPointerYInVisibleLane,
  minuteOfDayWithSecondsInTimeZone,
  sameLaneResizeBounds,
  SLOT_MINUTES,
  TIMELINE_SLOT_HEIGHT_PX,
  visibleMinuteRange,
} from '../lib/time'
import { actualPlacementEnd, nearestBlockStart, NO_NEARBY_BLOCK_SPACE, blockRangeAvailable } from '../lib/blockPlacement'
import { TimeBlockCard } from './TimeBlockCard'

export const READY_TASK_DRAG_TYPE = 'ready-to-plan-task'
export const PLANNED_LANE_DROP_ID = 'day-planned-lane'

const laneHeaderPlanned =
  'font-label text-xs font-semibold uppercase tracking-[0.08em] text-planned dark:text-planned-dark pb-1'
const laneHeaderActual =
  'font-label text-xs font-semibold uppercase tracking-[0.08em] text-actual dark:text-actual-dark pb-1'

function laneSurfaceClass(lane: BlockLane) {
  return lane === 'planned'
    ? 'border-planned-border/80 bg-planned-surface/95 dark:border-planned-dark-border dark:bg-planned-dark-surface'
    : 'border-actual-border/80 bg-actual-surface/95 dark:border-actual-dark-border dark:bg-actual-dark-surface'
}

export const DayTimeline = forwardRef<
  HTMLDivElement,
  {
    day: DayRead
    /** Same server-anchored clock used by Activity Tracking. */
    now: () => number
    showZoomControls?: boolean
    /** Controlled zoom, so menus outside the timeline can reset it; uncontrolled when omitted. */
    zoom?: number
    onZoomChange?: (zoom: number) => void
    readOnly: boolean
    draft: BlockDraftPlacement | null
    /** When set, the matching block shows selected affordance on the timeline. */
    selectedBlockId: number | null
    onLaneSlotClick: (lane: BlockLane, startMin: number, endMin: number) => void
    onDraftTimeChange?: (startMin: number, endMin: number) => void
    onPatchBlock: (
      blockId: number,
      patch: {
        task_type_id?: number
        note?: string | null
        start_minute?: number
        end_minute?: number
      }, lane: BlockLane,
    ) => Promise<void>
    onBlockClick?: (blockId: number, lane: BlockLane) => boolean | void
    /** While a block move/resize drag is active, parent may disable inspector hit-testing. */
    onBlockDragSessionChange?: (active: boolean) => void
    /** Position today's Now Line once, leaving more room below it for upcoming work. */
    autoScrollToNow?: boolean
    /** Increment to re-scroll while already viewing Today. */
    scrollToNowRequest?: number
    placementSelected?: boolean
    placementPreview?: { start: number; end: number } | null
    onPlacementError?: (message: string) => void
  }
>(function DayTimeline(
  {
    day,
    now,
    showZoomControls = true,
    zoom: controlledZoom,
    onZoomChange,
    readOnly,
    draft,
    selectedBlockId,
    onLaneSlotClick,
    onDraftTimeChange,
    onPatchBlock,
    onBlockClick,
    onBlockDragSessionChange,
    autoScrollToNow = false,
    scrollToNowRequest = 0,
    placementSelected = false,
    placementPreview = null,
    onPlacementError,
  },
  ref,
) {
  const { start: visibleStartMin, end: visibleEndMin } = visibleMinuteRange(day)
  const slotCount = (visibleEndMin - visibleStartMin) / SLOT_MINUTES
  const [localZoom, setLocalZoom] = useState(1)
  const zoom = controlledZoom ?? localZoom
  const slotHeightPx = TIMELINE_SLOT_HEIGHT_PX * zoom
  const timelineRef = useRef<HTMLDivElement>(null)
  const zoomRef = useRef(zoom)
  const onZoomChangeRef = useRef(onZoomChange)
  useEffect(() => {
    zoomRef.current = zoom
    onZoomChangeRef.current = onZoomChange
  })
  const setZoom = (next: number) => {
    zoomRef.current = next
    setLocalZoom(next)
    onZoomChangeRef.current?.(next)
  }
  const totalHeight = slotCount * slotHeightPx

  const plannedRef = useRef<HTMLDivElement>(null)
  const actualRef = useRef<HTMLDivElement>(null)
  const nowLineRef = useRef<HTMLDivElement>(null)
  const autoScrollDateRef = useRef<string | null>(null)
  const autoScrollRequestRef = useRef(0)
  const autoScrollCompletedRef = useRef(false)

  useEffect(() => {
    const node = timelineRef.current
    if (!node) return
    let distance: number | null = null
    const touchPointers = new Set<number>()
    const pointerDown = (event: PointerEvent) => {
      if (event.pointerType !== 'touch') return
      touchPointers.add(event.pointerId)
      if (touchPointers.size < 2) return
      for (const pointerId of touchPointers) {
        window.dispatchEvent(new PointerEvent('pointercancel', { pointerId }))
      }
      event.stopPropagation()
    }
    const pointerEnd = (event: PointerEvent) => { touchPointers.delete(event.pointerId) }
    const changeZoom = (factor: number, clientY: number) => {
      const old = zoomRef.current
      const next = Math.min(12, Math.max(0.5, old * factor))
      const laneTop = plannedRef.current?.getBoundingClientRect().top ?? node.getBoundingClientRect().top
      const anchor = clientY - laneTop
      setZoom(next)
      requestAnimationFrame(() => window.scrollBy(0, anchor * (next / old - 1)))
    }
    const touchStart = (event: TouchEvent) => {
      if (event.touches.length === 2) {
        distance = Math.hypot(event.touches[0].clientX - event.touches[1].clientX, event.touches[0].clientY - event.touches[1].clientY)
        event.preventDefault()
      }
    }
    const touchMove = (event: TouchEvent) => {
      if (event.touches.length !== 2 || distance === null) return
      event.preventDefault()
      const next = Math.hypot(event.touches[0].clientX - event.touches[1].clientX, event.touches[0].clientY - event.touches[1].clientY)
      if (distance > 0) changeZoom(next / distance, (event.touches[0].clientY + event.touches[1].clientY) / 2)
      distance = next
    }
    const touchEnd = () => { distance = null }
    const wheel = (event: WheelEvent) => {
      if (!event.ctrlKey && !event.metaKey) return
      event.preventDefault()
      changeZoom(Math.exp(-event.deltaY * 0.01), event.clientY)
    }
    node.addEventListener('pointerdown', pointerDown, true)
    window.addEventListener('pointerup', pointerEnd)
    node.addEventListener('pointercancel', pointerEnd)
    node.addEventListener('touchstart', touchStart, { passive: false })
    node.addEventListener('touchmove', touchMove, { passive: false })
    node.addEventListener('touchend', touchEnd)
    node.addEventListener('touchcancel', touchEnd)
    node.addEventListener('wheel', wheel, { passive: false })
    return () => {
      node.removeEventListener('pointerdown', pointerDown, true)
      window.removeEventListener('pointerup', pointerEnd)
      node.removeEventListener('pointercancel', pointerEnd)
      node.removeEventListener('touchstart', touchStart)
      node.removeEventListener('touchmove', touchMove)
      node.removeEventListener('touchend', touchEnd)
      node.removeEventListener('touchcancel', touchEnd)
      node.removeEventListener('wheel', wheel)
    }
  }, [])

  const [, setNowTick] = useState(0)
  const currentInstant = new Date(now())
  const isTodayInTz = calendarIsoDateInTimeZone(currentInstant, day.meta.timezone) === day.date
  useEffect(() => {
    const id = window.setInterval(() => setNowTick((n) => n + 1), 30_000)
    return () => window.clearInterval(id)
  }, [])

  const nowMinuteOfDay = minuteOfDayWithSecondsInTimeZone(currentInstant, day.meta.timezone)

  const onLaneClick = (lane: BlockLane, e: React.MouseEvent<HTMLDivElement>) => {
    if (readOnly) return
    if (!(lane === 'planned' && placementSelected) && (e.target as HTMLElement).closest('[data-block], [data-draft-block]')) return
    const el = e.currentTarget
    const top = el.getBoundingClientRect().top
    const y = e.clientY - top
    const start = minuteFromPointerYInVisibleLane(
      y,
      visibleStartMin,
      visibleEndMin,
      slotHeightPx,
    )
    if (start >= visibleEndMin) return
    const end = Math.min(start + SLOT_MINUTES, visibleEndMin)
    onLaneSlotClick(lane, start, end)
  }

  const blocksFor = (lane: BlockLane) => {
    if (lane === 'planned') return day.time_blocks.filter((block) => block.lane === 'planned').sort((a, b) => a.start_minute - b.start_minute)
    return day.actual_blocks.map(({ actual_block: actual, start_minute, end_minute }) => ({
      id: actual.id,
      lane: 'actual' as const,
      task_type_id: actual.task_type_id,
      task_type: actual.task_type,
      task_id: actual.task_id,
      task: actual.task,
      name: actual.name,
      note: actual.note,
      planned_block_id: actual.planned_block_id,
      start_minute,
      end_minute: actual.end_at == null && isTodayInTz ? Math.max(start_minute, Math.min(visibleEndMin, nowMinuteOfDay)) : end_minute,
      start_at: actual.start_at,
      end_at: actual.end_at,
      created_at: actual.created_at,
      updated_at: actual.updated_at,
    })).sort((a, b) => a.start_minute - b.start_minute)
  }

  const visibleRange = visibleEndMin - visibleStartMin
  const showNowLine =
    isTodayInTz &&
    visibleRange > 0 &&
    nowMinuteOfDay >= visibleStartMin &&
    nowMinuteOfDay < visibleEndMin
  const nowLineTopPx = showNowLine
    ? ((nowMinuteOfDay - visibleStartMin) / visibleRange) * totalHeight
    : 0

  useEffect(() => {
    if (
      autoScrollDateRef.current !== day.date
      || autoScrollRequestRef.current !== scrollToNowRequest
    ) {
      autoScrollDateRef.current = day.date
      autoScrollRequestRef.current = scrollToNowRequest
      autoScrollCompletedRef.current = false
    }
    if (!autoScrollToNow || autoScrollCompletedRef.current) return
    if (!showNowLine) {
      if (isTodayInTz) autoScrollCompletedRef.current = true
      return
    }
    const line = nowLineRef.current
    const timeline = timelineRef.current
    if (!line || !timeline) return
    autoScrollCompletedRef.current = true
    scrollCurrentTimeIntoView(line, timeline)
  }, [autoScrollToNow, day.date, isTodayInTz, scrollToNowRequest, showNowLine])

  return (
    <>
    {showZoomControls && (<div className="flex justify-end pb-1">
        <div className="inline-flex min-h-11 items-center rounded-full border border-outline-variant/40 bg-surface-container-low/60 pl-4 pr-1 text-xs text-on-surface-variant dark:border-dark-outline-variant dark:bg-dark-surface-container dark:text-dark-on-surface-variant">
        <span className="inline-flex items-center gap-2 rounded-md outline-none focus-visible:ring-2 focus-visible:ring-primary" tabIndex={0} aria-label={`Timeline zoom ${zoom.toFixed(1)} times. Use arrow keys to adjust.`}
          onKeyDown={(e) => {
            if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
              e.preventDefault()
              const next = Math.min(12, Math.max(0.5, zoom * (e.key === 'ArrowUp' ? 1.2 : 1 / 1.2)))
              setZoom(next)
            }
          }}>Zoom <span className="min-w-[3.5ch] font-mono font-medium tabular-nums text-on-surface dark:text-dark-on-surface">{zoom.toFixed(1)}×</span></span>
        <span aria-hidden className="mx-3 h-4 w-px bg-outline-variant/50 dark:bg-dark-outline-variant" />
        <button type="button" className="inline-flex min-h-11 items-center gap-1.5 rounded-full px-3 font-medium text-planned transition-colors hover:bg-planned/10 focus-visible:outline-2 focus-visible:outline-planned" onClick={() => setZoom(1)}>
          <span className="material-symbols-outlined text-[16px]" aria-hidden>restart_alt</span>
          Reset
        </button>
        </div>
      </div>)}
    <div
      ref={(node) => {
        timelineRef.current = node
        if (typeof ref === 'function') ref(node)
        else if (ref) ref.current = node
      }}
      className="grid grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)] gap-x-1 gap-y-1.5 sm:gap-x-2"
      data-testid="day-timeline"
    >
      <div className="w-12 shrink-0 sm:w-14" aria-hidden />
      <h3 className={laneHeaderPlanned}>Planned</h3>
      <h3 className={laneHeaderActual}>Actual</h3>

      <div className="col-start-1 row-start-2 w-12 shrink-0 select-none border-r border-outline-variant/25 pr-1.5 text-right font-body text-[11px] text-timeline-label sm:w-14 dark:border-dark-outline-variant dark:text-dark-on-surface-variant">
        <div style={{ height: totalHeight }} className="relative">
          {Array.from({ length: slotCount }, (_, i) => {
            const m = visibleStartMin + i * SLOT_MINUTES
            const showLabel = m % 60 === 0 || i === 0
            return (
              <div
                key={m}
                data-hour={m}
                className={
                  m % 60 === 0
                    ? 'absolute w-full border-t border-timeline-grid-strong pt-0.5 dark:border-dark-outline-variant'
                    : 'absolute w-full border-t border-timeline-grid-soft dark:border-dark-surface-container'
                }
                style={{ top: i * slotHeightPx, height: slotHeightPx }}
              >
                {showLabel ? formatHourLabelGcal12(m) : ''}
              </div>
            )
          })}
        </div>
      </div>

      <Lane
        laneRef={plannedRef}
        lane="planned"
        placementSelected={placementSelected}
        placementPreview={placementPreview}
        onPlacementError={onPlacementError}
        slotHeightPx={slotHeightPx}
        totalHeight={totalHeight}
        slotCount={slotCount}
        visibleStartMin={visibleStartMin}
        visibleEndMin={visibleEndMin}
        blocks={blocksFor('planned')}
        draft={draft?.lane === 'planned' ? draft : null}
        readOnly={readOnly}
        onLaneClick={(e) => onLaneClick('planned', e)}
        onPatchBlock={onPatchBlock}
        onBlockClick={onBlockClick}
        onDraftTimeChange={onDraftTimeChange}
        selectedBlockId={selectedBlockId}
        onBlockDragSessionChange={onBlockDragSessionChange}
      />
      <Lane
        laneRef={actualRef}
        lane="actual"
        runningBlockIds={day.actual_blocks.filter(p => p.actual_block.end_at == null).map(p => p.actual_block.id)}
        slotHeightPx={slotHeightPx}
        totalHeight={totalHeight}
        slotCount={slotCount}
        visibleStartMin={visibleStartMin}
        visibleEndMin={visibleEndMin}
        placementEndMin={actualPlacementEnd(day)}
        blocks={blocksFor('actual')}
        onPlacementError={onPlacementError}
        draft={draft?.lane === 'actual' ? draft : null}
        readOnly={readOnly}
        onLaneClick={(e) => onLaneClick('actual', e)}
        onPatchBlock={onPatchBlock}
        onBlockClick={onBlockClick}
        onDraftTimeChange={onDraftTimeChange}
        selectedBlockId={selectedBlockId}
        onBlockDragSessionChange={onBlockDragSessionChange}
      />

      {showNowLine && (
        <div
          className="pointer-events-none relative z-18 col-start-2 col-span-2 row-start-2"
          style={{ height: totalHeight }}
          data-testid="day-now-line"
          aria-hidden
        >
          <div
            ref={nowLineRef}
            className="absolute left-0 right-0 border-t-2 border-now-line"
            style={{ top: nowLineTopPx, transform: 'translateY(-1px)' }}
          />
        </div>
      )}
    </div>
    </>
  )
})

function scrollCurrentTimeIntoView(line: HTMLElement, timeline: HTMLElement, viewportHeight = window.innerHeight) {
  window.scrollBy({
    top: nowLineScrollDelta(line.getBoundingClientRect().top, timeline.getBoundingClientRect().top, viewportHeight),
    behavior: 'auto',
  })
}

function DraftBlockOverlay({
  draft,
  blocks,
  visibleStartMin,
  visibleEndMin,
  slotHeightPx,
  laneRef,
  lane,
  readOnly,
  onDraftTimeChange,
  onPlacementError,
}: {
  draft: BlockDraftPlacement
  blocks: TimeBlock[]
  visibleStartMin: number
  visibleEndMin: number
  slotHeightPx: number
  laneRef: React.RefObject<HTMLDivElement | null>
  lane: BlockLane
  readOnly: boolean
  onDraftTimeChange?: (startMin: number, endMin: number) => void
  onPlacementError?: (message: string) => void
}) {
  const [drag, setDrag] = useState<{
    kind: 'resize' | 'move'
    edge: 'start' | 'end' | 'move'
    invalid?: boolean
    start: number
    end: number
  } | null>(null)
  const dragRef = useRef(drag)
  const latestDraft = useRef({ blocks })
  useEffect(() => { latestDraft.current = { blocks } }, [blocks])

  useEffect(() => {
    dragRef.current = drag
  }, [drag])

  const displayStart = drag ? drag.start : draft.start_minute
  const displayEnd = drag ? drag.end : draft.end_minute

  const getMinuteFromClientY = useCallback(
    (cy: number) => {
      const el = laneRef.current
      if (!el) return visibleStartMin
      const top = el.getBoundingClientRect().top
      const y = cy - top
      return Math.min(visibleEndMin, Math.max(visibleStartMin, visibleStartMin + Math.round(y / slotHeightPx * SLOT_MINUTES)))
    },
    [laneRef, visibleStartMin, visibleEndMin, slotHeightPx],
  )

  const endDrag = useCallback(() => {
    const d = dragRef.current
    dragRef.current = null
    setDrag(null)
    if (!d) return
    const { start, end } = d
    if (end - start < SLOT_MINUTES) return
    if (d.invalid) { onPlacementError?.(NO_NEARBY_BLOCK_SPACE); return }
    if (!blockRangeAvailable(latestDraft.current.blocks, start, end, visibleStartMin, visibleEndMin)) {
      onPlacementError?.('That time is no longer available'); return
    }
    if (start === draft.start_minute && end === draft.end_minute) return
    onDraftTimeChange?.(start, end)
  }, [draft.end_minute, draft.start_minute, onDraftTimeChange, onPlacementError, visibleStartMin, visibleEndMin])

  const cancelDrag = useCallback(() => {
    dragRef.current = null
    setDrag(null)
  }, [])

  const resizeBoundsRef = useRef({ minStartMinute: 0, maxEndMinute: MINUTES_PER_DAY })

  const startDraftDrag = useCallback(
    (edge: 'start' | 'end' | 'move', e: React.PointerEvent) => {
      if (readOnly || !onDraftTimeChange) return
      e.stopPropagation()
      e.preventDefault()
      const el = e.currentTarget as HTMLElement
      const pointerId = e.pointerId
      const anchor = getMinuteFromClientY(e.clientY)
      resizeBoundsRef.current = gapBoundsForDraft(blocks, draft.start_minute, draft.end_minute)
      const initial = {
        kind: edge === 'move' ? 'move' as const : 'resize' as const,
        edge,
        start: draft.start_minute,
        end: draft.end_minute,
      }
      dragRef.current = initial
      setDrag(initial)

      const onMove = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        setDrag((cur) => {
          if (!cur) return cur
          const { minStartMinute, maxEndMinute } = resizeBoundsRef.current
          const m = getMinuteFromClientY(ev.clientY)
          let next: typeof cur
          if (cur.kind === 'move') {
            const duration = draft.end_minute - draft.start_minute
            const candidate = draft.start_minute + m - anchor
            const start = nearestBlockStart(latestDraft.current.blocks, candidate, duration, visibleStartMin, visibleEndMin)
            next = { ...cur, start: start ?? candidate, end: (start ?? candidate) + duration, invalid: start === null }
          } else if (cur.edge === 'start') {
            const ns = Math.min(m, cur.end - SLOT_MINUTES)
            next = {
              ...cur,
              start: Math.max(minStartMinute, Math.max(visibleStartMin, ns)),
            }
          } else {
            const ne = Math.max(m, cur.start + SLOT_MINUTES)
            next = { ...cur, end: Math.min(maxEndMinute, Math.min(visibleEndMin, ne)) }
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
      blocks,
      cancelDrag,
      draft.end_minute,
      draft.start_minute,
      endDrag,
      getMinuteFromClientY,
      onDraftTimeChange,
      readOnly,
      visibleStartMin,
      visibleEndMin,
    ],
  )

  const draftTop =
    ((displayStart - visibleStartMin) / SLOT_MINUTES) * slotHeightPx
  const draftHeight = Math.max(
    ((displayEnd - displayStart) / SLOT_MINUTES) * slotHeightPx,
    slotHeightPx,
  )

  const laneLabel = lane === 'planned' ? 'Planned' : 'Actual'

  const isDragging = drag != null

  return (
    <div
      data-draft-block
      data-testid="draft-block"
      data-dragging={isDragging ? 'true' : undefined}
      data-drag-kind={drag?.kind}
      className={`absolute left-1 right-1 flex flex-col overflow-hidden rounded-md border border-dashed border-primary/40 transition-[box-shadow,background-color] duration-150 dark:border-dark-outline dark:bg-primary-container/10 ${
        isDragging
          ? 'z-30 bg-primary-container/35 shadow-[0_0_40px_rgba(45,52,53,0.1)] ring-1 ring-inset ring-primary/25 dark:bg-dark-surface-container-high/45 dark:shadow-[0_0_40px_rgba(0,0,0,0.3)]'
          : 'z-20 bg-primary-container/15'
      }`}
      style={{ top: draftTop, height: draftHeight }}
      onPointerDown={(e) => e.stopPropagation()}
      onClick={(e) => e.stopPropagation()}
    >
      {!readOnly && onDraftTimeChange && (
        <button
          type="button"
          aria-label={`Resize draft block start (${laneLabel})`}
          className="h-2 w-full shrink-0 cursor-ns-resize border-0 bg-on-surface/10 hover:bg-on-surface/20 dark:bg-dark-on-surface/10 dark:hover:bg-dark-on-surface/20"
          onPointerDown={(e) => startDraftDrag('start', e)}
        />
      )}
      {<button type="button" className="min-h-0 flex-1 text-xs touch-none"
        aria-label={`Move draft ${lane} block`} disabled={readOnly}
        onPointerDown={e => startDraftDrag('move', e)}>
        {drag?.invalid ? NO_NEARBY_BLOCK_SPACE : formatTimeRangeGcal12(displayStart, displayEnd)}
      </button>}
      {!readOnly && onDraftTimeChange && (
        <button
          type="button"
          aria-label={`Resize draft block end (${laneLabel})`}
          className="h-2 w-full shrink-0 cursor-ns-resize border-0 bg-on-surface/10 hover:bg-on-surface/20 dark:bg-dark-on-surface/10 dark:hover:bg-dark-on-surface/20"
          onPointerDown={(e) => startDraftDrag('end', e)}
        />
      )}
    </div>
  )
}

function Lane({
  laneRef,
  lane,
  runningBlockIds = [],
  slotHeightPx,
  totalHeight,
  slotCount,
  visibleStartMin,
  visibleEndMin,
  blocks,
  draft,
  readOnly,
  onLaneClick,
  onPatchBlock,
  onBlockClick,
  onDraftTimeChange,
  selectedBlockId,
  onBlockDragSessionChange,
  placementSelected = false,
  placementPreview = null,
  placementEndMin = visibleEndMin,
  onPlacementError,
}: {
  laneRef: React.RefObject<HTMLDivElement | null>
  lane: BlockLane
  runningBlockIds?: number[]
  slotHeightPx: number
  totalHeight: number
  slotCount: number
  visibleStartMin: number
  visibleEndMin: number
  blocks: (TimeBlock & { end_at?: string | null })[]
  draft: BlockDraftPlacement | null
  readOnly: boolean
  onLaneClick: (e: React.MouseEvent<HTMLDivElement>) => void
  onPatchBlock: (
    blockId: number,
    patch: {
      task_type_id?: number
      note?: string | null
      start_minute?: number
      end_minute?: number
    }, lane: BlockLane,
  ) => Promise<void>
  onBlockClick?: (blockId: number, lane: BlockLane) => boolean | void
  onDraftTimeChange?: (startMin: number, endMin: number) => void
  onPlacementError?: (message: string) => void
  selectedBlockId: number | null
  onBlockDragSessionChange?: (active: boolean) => void
  placementSelected?: boolean
  placementPreview?: { start: number; end: number } | null
  placementEndMin?: number
}) {
  const { ref: dropRef, isDropTarget } = useDroppable({
    id: lane === 'planned' ? PLANNED_LANE_DROP_ID : 'day-actual-lane',
    accept: lane === 'planned' ? READY_TASK_DRAG_TYPE : 'no-ready-task-drops',
    disabled: readOnly || lane !== 'planned',
  })
  const setLaneRef = useCallback(
    (node: HTMLDivElement | null) => {
      laneRef.current = node
      dropRef(node)
    },
    [dropRef, laneRef],
  )
  /**
   * Explicit grid placement so the `[data-testid="day-now-line"]` overlay's
   * `col-start-2 col-span-2 row-start-2` (definite position) cannot evict the
   * auto-placed lanes — or the hour gutter — into an implicit row.
   */
  const gridPlacement = lane === 'planned' ? 'col-start-2 row-start-2' : 'col-start-3 row-start-2'
  const collisionBlocks = draft ? [...blocks, { id: -1, ...draft }] : blocks
  return (
    <div
      ref={setLaneRef}
      data-day-lane={lane}
      data-slot-height={slotHeightPx}
      role="presentation"
      className={`relative min-w-0 border ${gridPlacement} ${laneSurfaceClass(lane)} ${readOnly ? '' : 'cursor-crosshair'} ${isDropTarget ? 'z-10 ring-2 ring-inset ring-primary/45' : ''}`}
      style={{ height: totalHeight }}
      onClick={onLaneClick}
    >
      {Array.from({ length: slotCount }, (_, i) => {
        const m = visibleStartMin + i * SLOT_MINUTES
        return (
        <div
          key={i}
          className={
            m % 60 === 0
              ? 'absolute left-0 right-0 border-t border-timeline-grid-strong dark:border-dark-outline-variant'
              : 'absolute left-0 right-0 border-t border-timeline-grid-soft dark:border-dark-surface-container'
          }
          style={{ top: i * slotHeightPx, height: slotHeightPx }}
        />
        )
      })}
      {blocks.map((b) => {
        const { minStartMinute, maxEndMinute } = sameLaneResizeBounds(collisionBlocks, b.id)
        return (
          <TimeBlockCard
            key={b.id}
            block={b}
            lane={lane}
            visibleStartMin={visibleStartMin}
            visibleEndMin={placementEndMin}
            slotHeightPx={slotHeightPx}
            readOnly={readOnly}
            timeEditingDisabled={runningBlockIds.includes(b.id)}
            sameLaneBlocks={collisionBlocks}
            onPlacementError={onPlacementError}
            resizeMinStartMinute={minStartMinute}
            resizeMaxEndMinute={maxEndMinute}
            getMinuteFromClientY={(cy) => {
              const el = laneRef.current
              if (!el) return visibleStartMin
              const top = el.getBoundingClientRect().top
              const y = cy - top
              return Math.min(visibleEndMin, Math.max(visibleStartMin, visibleStartMin + Math.round(y / slotHeightPx * SLOT_MINUTES)))
            }}
            onPatch={(patch) => onPatchBlock(b.id, patch, lane)}
            onBlockClick={
              readOnly || !onBlockClick
                ? undefined
                : () => {
                    return onBlockClick(b.id, lane) !== false
                  }
            }
            isSelected={selectedBlockId === b.id}
            onDragSessionChange={onBlockDragSessionChange}
          />
        )
      })}
      {draft != null && (
        <DraftBlockOverlay
          draft={draft}
          blocks={blocks}
          visibleStartMin={visibleStartMin}
          visibleEndMin={placementEndMin}
          slotHeightPx={slotHeightPx}
          laneRef={laneRef}
          lane={lane}
          readOnly={readOnly}
          onDraftTimeChange={onDraftTimeChange}
          onPlacementError={onPlacementError}
        />
      )}
      {placementPreview && (
        <div data-testid="planned-placement-preview" className="pointer-events-none absolute inset-x-1 z-40 border border-planned bg-planned-surface/80 p-1 text-xs"
          style={{ top: (placementPreview.start - visibleStartMin) / SLOT_MINUTES * slotHeightPx,
            height: (placementPreview.end - placementPreview.start) / SLOT_MINUTES * slotHeightPx }}>
          {formatTimeRangeGcal12(placementPreview.start, placementPreview.end)}
        </div>
      )}
      {placementSelected && (
        <div data-testid="planned-placement-target" className="absolute inset-0 z-50 cursor-crosshair" />
      )}
    </div>
  )
}
