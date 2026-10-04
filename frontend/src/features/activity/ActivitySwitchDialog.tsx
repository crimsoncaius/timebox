import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

export function ActivitySwitchDialog({ open, onClose, children, title = 'Switch activity' }: { open: boolean; onClose: () => void; children: ReactNode; title?: string }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const headingId = useId()
  useEffect(() => {
    if (open && dialog.current && !dialog.current.open) dialog.current.showModal()
  }, [open])
  if (!open) return children
  return createPortal(<dialog ref={dialog} aria-labelledby={headingId} onCancel={event => { event.preventDefault(); onClose() }}
    className="m-auto max-h-[90dvh] w-[min(480px,calc(100vw-24px))] overflow-y-auto rounded-3xl border border-outline-variant bg-surface-container-low p-6 text-sm text-on-surface backdrop:bg-black/50 dark:bg-dark-surface-container dark:text-dark-on-surface">
    <div className="mb-5 flex items-start justify-between gap-4"><div><p className="mb-2 text-xs uppercase tracking-widest">Activity tracking</p><h2 id={headingId} className="text-2xl font-semibold">{title}</h2></div>
      <button type="button" aria-label={`Close ${title.toLowerCase()}`} onClick={onClose} className="p-2">×</button></div>
    {children}
  </dialog>, document.body)
}
