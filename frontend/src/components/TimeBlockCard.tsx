import { useCallback, useEffect, useRef, useState } from 'react'
import type { BlockLane, TimeBlock } from '../lib/api'
import {
  floorToSlotMinute,
  resolveSameLaneMoveStart,
  SLOT_MINUTES,
} from '../lib/time'
import type { TimeBlockLike } from '../lib/time'

const LONG_PRESS_MS = 500
const LONG_PRESS_CANCEL_MOVE_PX = 10

type DragState =
  | { kind: 'resize'; edge: 'start' | 'end'; start: number; end: number }
  | { kind: 'move'; start: number; end: number }

export function TimeBlockCard({
  block,
  lane,
  visibleStartMin,
  visibleEndMin: _visibleEndMin,
  slotHeightPx,
  readOnly,
  sameLaneBlocks,
  resizeMinStartMinute,
  resizeMaxEndMinute,
  getMinuteFromClientY,
  onPatch,
  onBlockClick,
}: {
  block: TimeBlock
  lane: BlockLane
  visibleStartMin: number
  /** Reserved for future clamping to the visible window; lane height already limits pointer Y. */
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
  onBlockClick?: () => void
}) {
  void _visibleEndMin

  const [drag, setDrag] = useState<DragState | null>(null)
  const dragRef = useRef<DragState | null>(drag)
  const prevResolvedRef = useRef(block.start_minute)
  const suppressClickRef = useRef(false)

  useEffect(() => {
    dragRef.current = drag
  }, [drag])

  const displayStart = drag ? drag.start : block.start_minute
  const displayEnd = drag ? drag.end : block.end_minute
  const displayTop = ((displayStart - visibleStartMin) / SLOT_MINUTES) * slotHeightPx
  const displayHeight = ((displayEnd - displayStart) / SLOT_MINUTES) * slotHeightPx
  const heightPx = Math.max(displayHeight, slotHeightPx)

  const endDrag = useCallback(() => {
    const d = dragRef.current
    dragRef.current = null
    setDrag(null)
    if (!d) return
    const { start, end } = d
    if (end - start < SLOT_MINUTES) return
    if (start % SLOT_MINUTES !== 0 || end % SLOT_MINUTES !== 0) return
    if (start === block.start_minute && end === block.end_minute) return
    void onPatch({ start_minute: start, end_minute: end })
  }, [block.end_minute, block.start_minute, onPatch])

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
      resizeMaxEndMinute,
      resizeMinStartMinute,
    ],
  )

  const onBodyPointerDown = useCallback(
    (e: React.PointerEvent<HTMLButtonElement>) => {
      if (e.button !== 0) return
      e.stopPropagation()
      const el = e.currentTarget
      const pointerId = e.pointerId
      const pointerDownY = e.clientY
      const originStart = block.start_minute
      const originEnd = block.end_minute
      const duration = originEnd - originStart
      const lastPointerYRef = { current: e.clientY }
      let cancelledByMove = false
      let moveStarted = false
      let timer: ReturnType<typeof setTimeout> | null = null
      let anchorMinute = 0

      const cleanupWindow = () => {
        window.removeEventListener('pointermove', onPointerMove)
        window.removeEventListener('pointerup', onPointerUp)
      }

      const onPointerMove = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        lastPointerYRef.current = ev.clientY
        if (!moveStarted) {
          if (Math.abs(ev.clientY - pointerDownY) > LONG_PRESS_CANCEL_MOVE_PX) {
            cancelledByMove = true
            if (timer) clearTimeout(timer)
          }
          return
        }
        const deltaMin = getMinuteFromClientY(ev.clientY) - anchorMinute
        const candidate = floorToSlotMinute(originStart + deltaMin)
        const resolved = resolveSameLaneMoveStart(
          sameLaneBlocks,
          block.id,
          duration,
          candidate,
          prevResolvedRef.current,
        )
        prevResolvedRef.current = resolved
        const next: DragState = { kind: 'move', start: resolved, end: resolved + duration }
        dragRef.current = next
        setDrag(next)
      }

      const onPointerUp = (ev: PointerEvent) => {
        if (ev.pointerId !== pointerId) return
        if (timer) clearTimeout(timer)
        cleanupWindow()
        if (moveStarted) {
          try {
            el.releasePointerCapture(ev.pointerId)
          } catch {
            /* ignore */
          }
          suppressClickRef.current = true
          endDrag()
          return
        }
      }

      timer = setTimeout(() => {
        if (cancelledByMove) return
        moveStarted = true
        anchorMinute = getMinuteFromClientY(lastPointerYRef.current)
        prevResolvedRef.current = originStart
        const initialMove: DragState = { kind: 'move', start: originStart, end: originEnd }
        dragRef.current = initialMove
        setDrag(initialMove)
        try {
          el.setPointerCapture(pointerId)
        } catch {
          /* ignore */
        }
      }, LONG_PRESS_MS)

      window.addEventListener('pointermove', onPointerMove)
      window.addEventListener('pointerup', onPointerUp)
    },
    [block.end_minute, block.id, block.start_minute, endDrag, getMinuteFromClientY, sameLaneBlocks],
  )

  const label = block.task_type?.name ?? '—'

  return (
    <div
      data-block
      data-block-id={block.id}
      className="absolute left-1 right-1 z-10 flex flex-col overflow-hidden rounded-lg border border-outline-variant/30 bg-primary-container/40 dark:bg-primary-container/25"
      style={{ top: displayTop, height: heightPx }}
    >
      {!readOnly && (
        <button
          type="button"
          aria-label="Resize block start"
          className="h-2 w-full shrink-0 cursor-ns-resize border-0 bg-on-surface/10 hover:bg-on-surface/20"
          onPointerDown={(e) => startResize('start', e)}
        />
      )}
      {readOnly ? (
        <div className="flex min-h-0 flex-1 flex-col justify-center overflow-hidden px-1.5 py-0.5">
          <p className="truncate font-body text-[10px] font-medium leading-tight text-on-surface">{label}</p>
          {block.note ? (
            <p className="truncate font-body text-[9px] leading-tight text-outline-variant">{block.note}</p>
          ) : null}
        </div>
      ) : (
        <button
          type="button"
          aria-label={`Edit ${lane} block`}
          className="touch-none flex min-h-0 min-w-0 flex-1 flex-col justify-center overflow-hidden border-0 bg-transparent px-1.5 py-0.5 text-left select-none"
          onPointerDown={onBodyPointerDown}
          onClick={(e) => {
            e.stopPropagation()
            if (suppressClickRef.current) {
              e.preventDefault()
              suppressClickRef.current = false
              return
            }
            onBlockClick?.()
          }}
        >
          <span className="truncate font-body text-[10px] font-medium leading-tight text-on-surface">{label}</span>
          {block.note ? (
            <span className="truncate font-body text-[9px] leading-tight text-outline-variant">{block.note}</span>
          ) : null}
        </button>
      )}
      {!readOnly && (
        <button
          type="button"
          aria-label="Resize block end"
          className="h-2 w-full shrink-0 cursor-ns-resize border-0 bg-on-surface/10 hover:bg-on-surface/20"
          onPointerDown={(e) => startResize('end', e)}
        />
      )}
    </div>
  )
}
