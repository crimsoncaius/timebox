import { useRef } from 'react'
import type { BlockLane, DayRead, TimeBlock } from '../lib/api'
import {
  formatMinuteLabel24,
  sameLaneResizeBounds,
  SLOT_MINUTES,
  visibleMinuteRange,
} from '../lib/time'
import { TimeBlockCard } from './TimeBlockCard'

const SLOT_HEIGHT_PX = 28

export function DayTimeline({
  day,
  readOnly,
  onCreateBlock,
  onPatchBlock,
  onBlockClick,
}: {
  day: DayRead
  readOnly: boolean
  onCreateBlock: (lane: BlockLane, startMin: number, endMin: number) => Promise<void>
  onPatchBlock: (
    blockId: number,
    patch: {
      task_type_id?: number
      note?: string | null
      start_minute?: number
      end_minute?: number
    },
  ) => Promise<void>
  onBlockClick?: (blockId: number, lane: BlockLane) => void
}) {
  const { start: visibleStartMin, end: visibleEndMin } = visibleMinuteRange(day)
  const slotCount = (visibleEndMin - visibleStartMin) / SLOT_MINUTES
  const totalHeight = slotCount * SLOT_HEIGHT_PX

  const plannedRef = useRef<HTMLDivElement>(null)
  const actualRef = useRef<HTMLDivElement>(null)

  const onLaneClick = (lane: BlockLane, e: React.MouseEvent<HTMLDivElement>) => {
    if (readOnly) return
    if ((e.target as HTMLElement).closest('[data-block]')) return
    const el = e.currentTarget
    const top = el.getBoundingClientRect().top
    const y = e.clientY - top
    const idx = Math.floor(Math.max(0, y) / SLOT_HEIGHT_PX)
    const start = visibleStartMin + idx * SLOT_MINUTES
    if (start >= visibleEndMin) return
    const end = Math.min(start + SLOT_MINUTES, visibleEndMin)
    void onCreateBlock(lane, start, end)
  }

  const blocksFor = (lane: BlockLane) =>
    day.time_blocks.filter((b) => b.lane === lane).sort((a, b) => a.start_minute - b.start_minute)

  return (
    <div className="flex gap-3" data-testid="day-timeline">
      {/* Time labels */}
      <div className="w-14 shrink-0 select-none text-right font-headline text-[10px] text-outline">
        <div style={{ height: totalHeight }} className="relative">
          {Array.from({ length: slotCount }, (_, i) => {
            const m = visibleStartMin + i * SLOT_MINUTES
            const showLabel = m % 60 === 0 || i === 0
            return (
              <div
                key={m}
                className="absolute w-full border-t border-outline-variant/10 pr-1 pt-0.5"
                style={{ top: i * SLOT_HEIGHT_PX, height: SLOT_HEIGHT_PX }}
              >
                {showLabel ? formatMinuteLabel24(m) : ''}
              </div>
            )
          })}
        </div>
      </div>

      <div className="grid min-w-0 flex-1 grid-cols-2 gap-3">
        <div>
          <h3 className="mb-2 font-headline text-xs uppercase tracking-[0.2em] text-on-surface-variant">
            Planned
          </h3>
          <Lane
            laneRef={plannedRef}
            lane="planned"
            totalHeight={totalHeight}
            slotCount={slotCount}
            visibleStartMin={visibleStartMin}
            visibleEndMin={visibleEndMin}
            blocks={blocksFor('planned')}
            readOnly={readOnly}
            onLaneClick={(e) => onLaneClick('planned', e)}
            onPatchBlock={onPatchBlock}
            onBlockClick={onBlockClick}
          />
        </div>
        <div>
          <h3 className="mb-2 font-headline text-xs uppercase tracking-[0.2em] text-on-surface-variant">
            Actual
          </h3>
          <Lane
            laneRef={actualRef}
            lane="actual"
            totalHeight={totalHeight}
            slotCount={slotCount}
            visibleStartMin={visibleStartMin}
            visibleEndMin={visibleEndMin}
            blocks={blocksFor('actual')}
            readOnly={readOnly}
            onLaneClick={(e) => onLaneClick('actual', e)}
            onPatchBlock={onPatchBlock}
            onBlockClick={onBlockClick}
          />
        </div>
      </div>
    </div>
  )
}

function Lane({
  laneRef,
  lane,
  totalHeight,
  slotCount,
  visibleStartMin,
  visibleEndMin,
  blocks,
  readOnly,
  onLaneClick,
  onPatchBlock,
  onBlockClick,
}: {
  laneRef: React.RefObject<HTMLDivElement | null>
  lane: BlockLane
  totalHeight: number
  slotCount: number
  visibleStartMin: number
  visibleEndMin: number
  blocks: TimeBlock[]
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
  onBlockClick?: (blockId: number, lane: BlockLane) => void
}) {
  return (
    <div
      ref={laneRef}
      role="presentation"
      className={`relative rounded-xl border border-outline-variant/20 bg-surface-container-low/80 ${readOnly ? '' : 'cursor-crosshair'}`}
      style={{ height: totalHeight }}
      onClick={onLaneClick}
    >
      {Array.from({ length: slotCount }, (_, i) => (
        <div
          key={i}
          className="absolute left-0 right-0 border-t border-outline-variant/10"
          style={{ top: i * SLOT_HEIGHT_PX, height: SLOT_HEIGHT_PX }}
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
            slotHeightPx={SLOT_HEIGHT_PX}
            readOnly={readOnly}
            sameLaneBlocks={blocks}
            resizeMinStartMinute={minStartMinute}
            resizeMaxEndMinute={maxEndMinute}
            getMinuteFromClientY={(cy) => {
              const el = laneRef.current
              if (!el) return visibleStartMin
              const top = el.getBoundingClientRect().top
              const y = cy - top
              const idx = Math.floor(Math.max(0, y) / SLOT_HEIGHT_PX)
              const m = visibleStartMin + idx * SLOT_MINUTES
              return Math.min(Math.max(m, 0), 24 * 60 - SLOT_MINUTES)
            }}
            onPatch={(patch) => onPatchBlock(b.id, patch)}
            onBlockClick={
              readOnly || !onBlockClick ? undefined : () => onBlockClick(b.id, lane)
            }
          />
        )
      })}
    </div>
  )
}
