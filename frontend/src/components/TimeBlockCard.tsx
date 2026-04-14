import { useCallback, useEffect, useRef, useState } from 'react'
import type { BlockLane, TimeBlock } from '../lib/api'
import { SLOT_MINUTES } from '../lib/time'

export function TimeBlockCard({
  block,
  lane,
  visibleStartMin,
  slotHeightPx,
  readOnly,
  getMinuteFromClientY,
  onPatch,
  onDelete,
}: {
  block: TimeBlock
  lane: BlockLane
  visibleStartMin: number
  slotHeightPx: number
  readOnly: boolean
  getMinuteFromClientY: (clientY: number) => number
  onPatch: (patch: { title?: string; start_minute?: number; end_minute?: number }) => Promise<void>
  onDelete?: () => Promise<void>
}) {
  const [title, setTitle] = useState(block.title)
  const [drag, setDrag] = useState<null | { edge: 'start' | 'end'; start: number; end: number }>(null)
  const dragRef = useRef(drag)

  useEffect(() => {
    dragRef.current = drag
  }, [drag])

  // Resync when server snapshot changes (e.g. reload or another tab).
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional snapshot resync
    setTitle(block.title)
  }, [block.title, block.id])

  const displayStart = drag ? drag.start : block.start_minute
  const displayEnd = drag ? drag.end : block.end_minute
  const displayTop = ((displayStart - visibleStartMin) / SLOT_MINUTES) * slotHeightPx
  const displayHeight = ((displayEnd - displayStart) / SLOT_MINUTES) * slotHeightPx

  const endDrag = useCallback(() => {
    const d = dragRef.current
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
      setDrag({ edge, start: block.start_minute, end: block.end_minute })

      const onMove = (ev: PointerEvent) => {
        setDrag((d) => {
          if (!d) return null
          const m = getMinuteFromClientY(ev.clientY)
          if (d.edge === 'start') {
            const ns = Math.min(m, d.end - SLOT_MINUTES)
            return { ...d, start: Math.max(0, ns) }
          }
          const ne = Math.max(m, d.start + SLOT_MINUTES)
          return { ...d, end: Math.min(24 * 60, ne) }
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
    [block.end_minute, block.start_minute, endDrag, getMinuteFromClientY],
  )

  return (
    <div
      data-block
      className="absolute left-1 right-1 z-10 overflow-hidden rounded-lg border border-outline-variant/30 bg-primary-container/40 dark:bg-primary-container/25"
      style={{ top: displayTop, height: Math.max(displayHeight, 24) }}
    >
      {!readOnly && (
        <button
          type="button"
          aria-label="Resize block start"
          className="absolute left-0 right-0 top-0 z-20 h-2 cursor-ns-resize bg-on-surface/10 hover:bg-on-surface/20"
          onPointerDown={(e) => startResize('start', e)}
        />
      )}
      <div className="px-2 py-1 pt-2">
        {readOnly ? (
          <p className="truncate font-body text-xs text-on-surface">{title || '—'}</p>
        ) : (
          <input
            className="w-full border-none bg-transparent font-body text-xs text-on-surface placeholder:text-outline-variant focus:ring-0"
            value={title}
            placeholder="Task"
            aria-label={`Task ${lane}`}
            onChange={(e) => setTitle(e.target.value)}
            onBlur={() => {
              const t = title.trim()
              if (t !== (block.title ?? '')) void onPatch({ title: t })
            }}
          />
        )}
      </div>
      {!readOnly && onDelete && (
        <button
          type="button"
          className="absolute bottom-6 right-1 rounded p-0.5 text-[10px] text-outline hover:bg-on-surface/10"
          aria-label="Delete block"
          onClick={(e) => {
            e.stopPropagation()
            void onDelete()
          }}
        >
          ✕
        </button>
      )}
      {!readOnly && (
        <button
          type="button"
          aria-label="Resize block end"
          className="absolute bottom-0 left-0 right-0 z-20 h-2 cursor-ns-resize bg-on-surface/10 hover:bg-on-surface/20"
          onPointerDown={(e) => startResize('end', e)}
        />
      )}
    </div>
  )
}
