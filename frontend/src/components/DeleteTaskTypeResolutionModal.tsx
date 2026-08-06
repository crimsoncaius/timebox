import { useEffect, useRef, useState } from 'react'
import type { TaskType } from '../lib/api'

export function DeleteTaskTypeResolutionModal({
  open,
  taskTypeName,
  migrateTargets,
  busy,
  onClose,
  onCascade,
  onMigrate,
}: {
  open: boolean
  taskTypeName: string
  migrateTargets: TaskType[]
  busy: boolean
  onClose: () => void
  onCascade: () => void
  onMigrate: (targetId: number) => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [migrateToId, setMigrateToId] = useState<number | null>(null)

  useEffect(() => {
    const el = dialogRef.current
    if (!el) return
    if (open) {
      if (!el.open) el.showModal()
    } else if (el.open) {
      el.close()
    }
  }, [open])

  const selectedMigrateToId = migrateTargets.some((target) => target.id === migrateToId)
    ? migrateToId
    : (migrateTargets[0]?.id ?? null)
  const canMigrate = selectedMigrateToId != null

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby="delete-task-type-resolve-title"
      className="max-w-lg rounded-2xl border border-outline-variant/20 bg-surface-container-lowest p-0 text-on-surface shadow-xl backdrop:bg-black/40 dark:border-dark-outline-variant dark:bg-dark-surface-container-lowest dark:text-dark-on-surface"
      onCancel={(e) => {
        if (busy) e.preventDefault()
        else onClose()
      }}
    >
      <div className="border-b border-outline-variant/15 px-5 py-4 dark:border-dark-outline-variant/25">
        <h2 id="delete-task-type-resolve-title" className="font-headline text-lg font-light tracking-tight">
          Remove task type?
        </h2>
        <p className="mt-2 font-body text-sm font-light leading-relaxed text-on-surface-variant dark:text-dark-on-surface-variant">
          <span className="text-on-surface dark:text-dark-on-surface">{taskTypeName}</span> is used by existing time
          blocks. Choose how to proceed.
        </p>
      </div>
      <div className="flex flex-col gap-3 px-5 py-4">
        <button
          type="button"
          disabled={busy}
          className="rounded-xl border border-error/25 bg-error-container/20 px-4 py-3 text-left font-body text-sm font-light text-on-error-container transition-opacity hover:bg-error-container/30 disabled:opacity-50 dark:border-error/30 dark:bg-error-container/15 dark:text-dark-on-error-container"
          onClick={() => onCascade()}
        >
          <span className="font-headline text-sm font-normal text-error dark:text-error">Delete type and all time blocks</span>
          <span className="mt-1 block text-xs text-on-surface-variant dark:text-dark-on-surface-variant">
            Permanently removes this task type and every planned or actual block that uses it.
          </span>
        </button>

        <div
          className={[
            'rounded-xl border px-4 py-3',
            canMigrate
              ? 'border-outline-variant/20 bg-surface-container-low/50 dark:border-dark-outline-variant dark:bg-dark-surface-container/40'
              : 'border-outline-variant/10 bg-surface-container-low/25 opacity-70 dark:border-dark-outline-variant/50 dark:bg-dark-surface-container/20',
          ].join(' ')}
        >
          <span className="font-headline text-sm font-normal text-on-surface dark:text-dark-on-surface">
            Move blocks to another type, then delete
          </span>
          <p className="mt-1 font-body text-xs font-light text-on-surface-variant dark:text-dark-on-surface-variant">
            All time blocks that use this type will point to the type you pick below.
          </p>
          {canMigrate ? (
            <label className="mt-3 block font-body text-xs font-medium text-on-surface-variant dark:text-dark-on-surface-variant">
              <span className="sr-only">Target task type</span>
              <select
                className="mt-1 w-full rounded-lg border-0 bg-surface-container-highest/90 px-3 py-2.5 font-body text-sm font-light text-on-surface outline-none ring-1 ring-outline-variant/20 focus-visible:ring-primary/25 dark:bg-dark-surface-container-high/80 dark:text-dark-on-surface dark:ring-dark-outline-variant/40"
                value={selectedMigrateToId ?? ''}
                disabled={busy}
                onChange={(ev) => setMigrateToId(Number(ev.target.value))}
              >
                {migrateTargets.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <p className="mt-3 font-body text-xs font-light text-on-surface-variant dark:text-dark-on-surface-variant">
              Add another saved task type first if you want to move blocks instead of deleting them.
            </p>
          )}
          <button
            type="button"
            disabled={busy || !canMigrate}
            className="mt-3 w-full rounded-lg bg-linear-to-br from-primary to-primary-dim px-4 py-2.5 font-headline text-sm font-light text-on-primary transition-opacity hover:opacity-95 disabled:cursor-not-allowed disabled:opacity-40 dark:from-primary/90 dark:to-primary-dim/90"
            onClick={() => {
              if (selectedMigrateToId != null) onMigrate(selectedMigrateToId)
            }}
          >
            Move blocks and delete type
          </button>
        </div>
      </div>
      <div className="flex justify-end gap-2 border-t border-outline-variant/15 px-5 py-3 dark:border-dark-outline-variant/25">
        <button
          type="button"
          disabled={busy}
          className="rounded-lg px-4 py-2 font-label text-xs uppercase tracking-wider text-on-surface-variant transition-colors hover:bg-surface-container-high disabled:opacity-50 dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container-high"
          onClick={onClose}
        >
          Cancel
        </button>
      </div>
    </dialog>
  )
}
