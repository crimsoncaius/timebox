import { useEffect, type RefObject } from 'react'

/** Elements whose own handlers own the click, so it must not close the inspector. */
const KEEPS_SELECTION = [
  '[role="dialog"]',
  '[data-inspector]',
  '[data-ready-task-id]',
]

/**
 * Close the Day inspector on Escape, or on a pointer press outside it.
 *
 * Presses inside the timeline are ignored: selecting another block is the
 * timeline's job, and it decides whether the current selection may be replaced.
 */
export function useInspectorDismiss(
  open: boolean,
  timelineRef: RefObject<HTMLElement | null>,
  onDismiss: () => void,
) {
  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      const node = event.target
      if (!(node instanceof Node)) return
      if (timelineRef.current?.contains(node)) return
      const element = node instanceof Element ? node : node.parentElement
      if (KEEPS_SELECTION.some((selector) => element?.closest(selector))) return
      onDismiss()
    }
    document.addEventListener('pointerdown', onPointerDown, true)
    return () => document.removeEventListener('pointerdown', onPointerDown, true)
  }, [open, timelineRef, onDismiss])

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onDismiss()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onDismiss])
}
