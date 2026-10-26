import { useId, useRef, useState, useLayoutEffect, type ReactNode } from 'react'

let dismissCurrent: (() => void) | undefined

/** Optional help uses the native top layer, including when its trigger lives in a dialog. */
export function ContextualHelp({ title, children }: { title: string; children: ReactNode }) {
  const id = useId()
  const trigger = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDialogElement>(null)
  const body = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  useLayoutEffect(() => {
    if (!open) return
    const dialog = panel.current!
    const button = trigger.current!
    const dismiss = () => { dialog.close(); setOpen(false) }
    dismissCurrent?.()
    dismissCurrent = dismiss
    dialog.showModal()
    const position = () => {
      const width = Math.min(352, window.innerWidth - 32)
      dialog.style.width = `${width}px`
      const rect = button.getBoundingClientRect()
      const height = dialog.scrollHeight
      const compact = body.current!.scrollHeight <= 120 && height < window.innerHeight - 32
      const below = window.innerHeight - rect.bottom - 16
      const above = rect.top - 16
      const anchored = compact && Math.max(below, above) >= height
      dialog.dataset.presentation = anchored ? 'popover' : 'dialog'
      dialog.style.left = `${anchored ? Math.max(16, Math.min(rect.left, window.innerWidth - width - 16)) : (window.innerWidth - width) / 2}px`
      dialog.style.top = `${anchored ? below >= height ? rect.bottom + 4 : rect.top - height - 4 : Math.max(16, (window.innerHeight - height) / 2)}px`
    }
    position()
    const observer = new ResizeObserver(position)
    observer.observe(body.current!)
    window.addEventListener('resize', position)
    window.addEventListener('scroll', position, true)
    dialog.querySelector('button')?.focus()
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', position)
      window.removeEventListener('scroll', position, true)
      dialog.close()
      if (dismissCurrent === dismiss) { dismissCurrent = undefined; button.focus() }
    }
  }, [open])
  return <>
    <button ref={trigger} type="button" aria-label={`Help: ${title}`} aria-haspopup="dialog" aria-expanded={open} aria-controls={open ? id : undefined}
      onClick={event => { event.stopPropagation(); setOpen(true) }}
      className="inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container focus-visible:outline-2 focus-visible:outline-planned dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container">
      <span aria-hidden className="material-symbols-outlined text-xl">info</span>
    </button>
    {open && <dialog ref={panel} id={id} role="dialog" aria-labelledby={`${id}-title`}
      onCancel={event => { event.preventDefault(); event.stopPropagation(); setOpen(false) }}
      onClick={event => event.stopPropagation()}
      onKeyDown={event => event.stopPropagation()}
      className="fixed m-0 max-h-[calc(100dvh-2rem)] max-w-none overflow-y-auto rounded-2xl bg-surface-container-lowest p-[20px] text-on-surface shadow-lg backdrop:bg-black/20 dark:bg-dark-surface-container dark:text-dark-on-surface">
      <div className="flex items-start justify-between gap-3">
        <h2 id={`${id}-title`} className="font-headline text-lg font-medium">{title}</h2>
        <button type="button" onClick={() => setOpen(false)} aria-label={`Close help: ${title}`} className="min-h-[44px] min-w-[44px] shrink-0 rounded-full focus-visible:outline-2 focus-visible:outline-planned">
          <span aria-hidden className="material-symbols-outlined">close</span>
        </button>
      </div>
      <div ref={body} className="space-y-2 text-sm leading-relaxed text-on-surface-variant dark:text-dark-on-surface-variant">{children}</div>
    </dialog>}
  </>
}
