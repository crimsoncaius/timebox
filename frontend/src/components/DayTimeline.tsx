import { forwardRef, useCallback, useEffect, useRef, useState } from 'react'
import type { BlockDraftPlacement, BlockLane, DayRead, TimeBlock } from '../lib/api'
import {
  formatMinuteLabel24,
  gapBoundsForDraft,
  MINUTES_PER_DAY,
  minuteFromPointerYInVisibleLane,
  sameLaneResizeBounds,
  SLOT_MINUTES,
  TIMELINE_SLOT_HEIGHT_PX,
  visibleMinuteRange,
} from '../lib/time'
import { TimeBlockCard } from './TimeBlockCard'

const headerClass =
  'font-headline text-xs uppercase tracking-[0.2em] text-on-surface-variant'

export const DayTimeline = forwardRef<
  HTMLDivElement,
  {
    day: DayRead
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
      },
    ) => Promise<void>
    onBlockClick?: (blockId: number, lane: BlockLane) => boolean | void
    /** While a block move/resize drag is active, parent may disable inspector hit-testing. */
    onBlockDragSessionChange?: (active: boolean) => void
  }
>(function DayTimeline(
  {
    day,
    readOnly,
    draft,
    selectedBlockId,
    onLaneSlotClick,
    onDraftTimeChange,
    onPatchBlock,
    onBlockClick,
    onBlockDragSessionChange,
  },
  ref,
) {
  const { start: visibleStartMin, end: visibleEndMin } = visibleMinuteRange(day)
  const slotCount = (visibleEndMin - visibleStartMin) / SLOT_MINUTES
  const totalHeight = slotCount * TIMELINE_SLOT_HEIGHT_PX

  const plannedRef = useRef<HTMLDivElement>(null)
  const actualRef = useRef<HTMLDivElement>(null)

  const onLaneClick = (lane: BlockLane, e: React.MouseEvent<HTMLDivElement>) => {
    if (readOnly) return
    if ((e.target as HTMLElement).closest('[data-block]')) return
    if ((e.target as HTMLElement).closest('[data-draft-block]')) return
    const el = e.currentTarget
    const top = el.getBoundingClientRect().top
    const y = e.clientY - top
    const start = minuteFromPointerYInVisibleLane(
      y,
      visibleStartMin,
      visibleEndMin,
      TIMELINE_SLOT_HEIGHT_PX,
    )
    if (start >= visibleEndMin) return
    const end = Math.min(start + SLOT_MINUTES, visibleEndMin)
    onLaneSlotClick(lane, start, end)
  }

  const blocksFor = (lane: BlockLane) =>
    day.time_blocks.filter((b) => b.lane === lane).sort((a, b) => a.start_minute - b.start_minute)

  return (
    <div
      ref={ref}
      className="grid grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)] gap-x-3 gap-y-2"
      data-testid="day-timeline"
    >
      <div className="w-14 shrink-0" aria-hidden />
      <h3 className={headerClass}>Planned</h3>
      <h3 className={headerClass}>Actual</h3>

      <div className="w-14 shrink-0 select-none text-right font-headline text-[10px] text-outline">
        <div style={{ height: totalHeight }} className="relative">
          {Array.from({ length: slotCount }, (_, i) => {
            const m = visibleStartMin + i * SLOT_MINUTES
            const showLabel = m % 60 === 0 || i === 0
            return (
              <div
                key={m}
                className="absolute w-full border-t border-outline-variant/10 pr-1 pt-0.5"
                style={{ top: i * TIMELINE_SLOT_HEIGHT_PX, height: TIMELINE_SLOT_HEIGHT_PX }}
              >
                {showLabel ? formatMinuteLabel24(m) : ''}
              </div>
            )
          })}
        </div>
      </div>

      <Lane
        laneRef={plannedRef}
        lane="planned"
        slotHeightPx={TIMELINE_SLOT_HEIGHT_PX}
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
        slotHeightPx={TIMELINE_SLOT_HEIGHT_PX}
        totalHeight={totalHeight}
        slotCount={slotCount}
        visibleStartMin={visibleStartMin}
        visibleEndMin={visibleEndMin}
        blocks={blocksFor('actual')}
        draft={draft?.lane === 'actual' ? draft : null}
        readOnly={readOnly}
        onLaneClick={(e) => onLaneClick('actual', e)}
        onPatchBlock={onPatchBlock}
        onBlockClick={onBlockClick}
        onDraftTimeChange={onDraftTimeChange}
        selectedBlockId={selectedBlockId}
        onBlockDragSessionChange={onBlockDragSessionChange}
      />
    </div>
  )
})

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
}) {
  const [drag, setDrag] = useState<{
    kind: 'resize'
    edge: 'start' | 'end'
    start: number
    end: number
  } | null>(null)
  const dragRef = useRef(drag)

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
      return minuteFromPointerYInVisibleLane(y, visibleStartMin, visibleEndMin, slotHeightPx)
    },
    [laneRef, visibleStartMin, visibleEndMin, slotHeightPx],
  )

  const endDrag = useCallback(() => {
    const d = dragRef.current
    dragRef.current = null
    setDrag(null)
    if (!d || d.kind !== 'resize') return
    const { start, end } = d
    if (end - start < SLOT_MINUTES) return
    if (start % SLOT_MINUTES !== 0 || end % SLOT_MINUTES !== 0) return
    if (start === draft.start_minute && end === draft.end_minute) return
    onDraftTimeChange?.(start, end)
  }, [draft.end_minute, draft.start_minute, onDraftTimeChange])

  const resizeBoundsRef = useRef({ minStartMinute: 0, maxEndMinute: MINUTES_PER_DAY })

  const startResize = useCallback(
    (edge: 'start' | 'end', e: React.PointerEvent) => {
      if (readOnly || !onDraftTimeChange) return
      e.stopPropagation()
      e.preventDefault()
      resizeBoundsRef.current = gapBoundsForDraft(blocks, draft.start_minute, draft.end_minute)
      const initial = {
        kind: 'resize' as const,
        edge,
        start: draft.start_minute,
        end: draft.end_minute,
      }
      dragRef.current = initial
      setDrag(initial)

      const onMove = (ev: PointerEvent) => {
        setDrag((cur) => {
          if (!cur || cur.kind !== 'resize') return cur
          const { minStartMinute, maxEndMinute } = resizeBoundsRef.current
          const m = getMinuteFromClientY(ev.clientY)
          let next: typeof cur
          if (cur.edge === 'start') {
            const ns = Math.min(m, cur.end - SLOT_MINUTES)
            next = {
              ...cur,
              start: Math.max(minStartMinute, Math.max(0, ns)),
            }
          } else {
            const ne = Math.max(m, cur.start + SLOT_MINUTES)
            next = { ...cur, end: Math.min(maxEndMinute, Math.min(24 * 60, ne)) }
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
      blocks,
      draft.end_minute,
      draft.start_minute,
      endDrag,
      getMinuteFromClientY,
      onDraftTimeChange,
      readOnly,
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
      data-drag-kind={isDragging ? 'resize' : undefined}
      className={`absolute left-1 right-1 flex flex-col overflow-hidden rounded-lg border border-dashed border-primary/50 transition-[box-shadow,background-color] duration-150 ${
        isDragging
          ? 'z-30 bg-primary-container/35 shadow-[0_0_40px_rgba(45,52,53,0.1)] ring-1 ring-inset ring-primary/25 dark:bg-primary-container/25 dark:shadow-[0_0_40px_rgba(0,0,0,0.3)]'
          : 'z-20 bg-primary-container/15 dark:bg-primary-container/10'
      }`}
      style={{ top: draftTop, height: draftHeight }}
      onPointerDown={(e) => e.stopPropagation()}
      onClick={(e) => e.stopPropagation()}
    >
      {!readOnly && onDraftTimeChange && (
        <button
          type="button"
          aria-label={`Resize draft block start (${laneLabel})`}
          className="h-2 w-full shrink-0 cursor-ns-resize border-0 bg-on-surface/10 hover:bg-on-surface/20"
          onPointerDown={(e) => startResize('start', e)}
        />
      )}
      <div className="min-h-0 flex-1" aria-hidden />
      {!readOnly && onDraftTimeChange && (
        <button
          type="button"
          aria-label={`Resize draft block end (${laneLabel})`}
          className="h-2 w-full shrink-0 cursor-ns-resize border-0 bg-on-surface/10 hover:bg-on-surface/20"
          onPointerDown={(e) => startResize('end', e)}
        />
      )}
    </div>
  )
}

function Lane({
  laneRef,
  lane,
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
}: {
  laneRef: React.RefObject<HTMLDivElement | null>
  lane: BlockLane
  slotHeightPx: number
  totalHeight: number
  slotCount: number
  visibleStartMin: number
  visibleEndMin: number
  blocks: TimeBlock[]
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
    },
  ) => Promise<void>
  onBlockClick?: (blockId: number, lane: BlockLane) => boolean | void
  onDraftTimeChange?: (startMin: number, endMin: number) => void
  selectedBlockId: number | null
  onBlockDragSessionChange?: (active: boolean) => void
}) {
  return (
    <div
      ref={laneRef}
      role="presentation"
      className={`relative min-w-0 rounded-xl border border-outline-variant/20 bg-surface-container-low/80 ${readOnly ? '' : 'cursor-crosshair'}`}
      style={{ height: totalHeight }}
      onClick={onLaneClick}
    >
      {Array.from({ length: slotCount }, (_, i) => (
        <div
          key={i}
          className="absolute left-0 right-0 border-t border-outline-variant/10"
          style={{ top: i * slotHeightPx, height: slotHeightPx }}
        />
      ))}
      {blocks.map((b) => {
        const { minStartMinute, maxEndMinute } = sameLaneResizeBounds(blocks, b.id)
        return (
          <TimeBlockCard
            key={b.id}
            block={b}
            lane={lane}
            visibleStartMin={visibleStartMin}
            visibleEndMin={visibleEndMin}
            slotHeightPx={slotHeightPx}
            readOnly={readOnly}
            sameLaneBlocks={blocks}
            resizeMinStartMinute={minStartMinute}
            resizeMaxEndMinute={maxEndMinute}
            getMinuteFromClientY={(cy) => {
              const el = laneRef.current
              if (!el) return visibleStartMin
              const top = el.getBoundingClientRect().top
              const y = cy - top
              return minuteFromPointerYInVisibleLane(y, visibleStartMin, visibleEndMin, slotHeightPx)
            }}
            onPatch={(patch) => onPatchBlock(b.id, patch)}
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
          visibleEndMin={visibleEndMin}
          slotHeightPx={slotHeightPx}
          laneRef={laneRef}
          lane={lane}
          readOnly={readOnly}
          onDraftTimeChange={onDraftTimeChange}
        />
      )}
    </div>
  )
}
