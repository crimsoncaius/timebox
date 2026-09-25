import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

export function ActivitySwitchDialog({ open, onClose, children }: { open: boolean; onClose: () => void; children: ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    if (open && dialog.current && !dialog.current.open) dialog.current.showModal()
  }, [open])
  if (!open) return children
  return createPortal(<dialog ref={dialog} aria-labelledby="switch-activity-heading" onCancel={onClose}
    className="m-auto max-h-[90dvh] w-[min(480px,calc(100vw-24px))] overflow-y-auto rounded-3xl border border-outline-variant bg-surface-container-low p-6 text-sm text-on-surface backdrop:bg-black/50 dark:bg-dark-surface-container dark:text-dark-on-surface">
    <div className="mb-5 flex items-start justify-between gap-4"><div><p className="mb-2 text-xs uppercase tracking-widest">Activity tracking</p><h2 id="switch-activity-heading" className="text-2xl font-semibold">Switch activity</h2></div>
      <button type="button" aria-label="Close switch activity" onClick={onClose} className="p-2">×</button></div>
    {children}
  </dialog>, document.body)
}
