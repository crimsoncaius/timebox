import { useEffect, useRef, type RefObject } from 'react'

/** Keyboard containment, focus return, and inert background for overlay editors. */
export function useModalFocus(ref: RefObject<HTMLElement | null>, onClose: () => void, open = true) {
  const close = useRef(onClose)
  const previousFocus = useRef(document.activeElement as HTMLElement | null)
  useEffect(() => { close.current = onClose }, [onClose])
  useEffect(() => {
    const dialog = ref.current
    if (!open || !dialog) return
    const previous = dialog.contains(document.activeElement) ? previousFocus.current : document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const siblings: { element: HTMLElement; inert: boolean }[] = []
    let parent: HTMLElement | null = dialog
    while (parent?.parentElement && parent !== document.body) {
      for (const node of parent.parentElement.children) {
        if (node !== parent && node instanceof HTMLElement) {
          siblings.push({ element: node, inert: node.hasAttribute('inert') })
          node.setAttribute('inert', '')
        }
      }
      parent = parent.parentElement
    }
    const focusable = () => Array.from(dialog.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), a[href], [tabindex]:not([tabindex="-1"])',
    )).filter(node => !node.closest('[hidden], [inert]') && node.tabIndex >= 0)
    if (!dialog.contains(document.activeElement)) (focusable()[0] ?? dialog).focus()
    const key = (event: KeyboardEvent) => {
      if (event.defaultPrevented) return
      if (event.target instanceof Element && !dialog.contains(event.target) && event.target.closest('[role="dialog"]')) return
      if (event.key === 'Escape') { event.preventDefault(); close.current(); return }
      if (event.key !== 'Tab') return
      const items = focusable(), first = items[0], last = items.at(-1)
      if (!first) { event.preventDefault(); dialog.focus(); return }
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', key)
    return () => {
      document.removeEventListener('keydown', key)
      siblings.forEach(({ element, inert }) => { element.toggleAttribute('inert', inert) })
      document.body.style.overflow = previousOverflow
      if (previous?.isConnected) previous.focus()
    }
  }, [ref, open])
}
