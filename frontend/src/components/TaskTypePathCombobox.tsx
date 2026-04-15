import { useEffect, useId, useMemo, useRef, useState } from 'react'
import type { TaskType } from '../lib/api'
import { buildTaskTypeSuggestions, formatTaskTypePathParts } from '../lib/taskTypePaths'

export function TaskTypePathCombobox({
  label,
  taskTypes,
  valueTaskTypeId,
  onSelectTaskTypeId,
  onCreateTaskTypePath,
}: {
  label: string
  taskTypes: TaskType[]
  valueTaskTypeId: number
  onSelectTaskTypeId: (taskTypeId: number) => void
  onCreateTaskTypePath: (path: string) => Promise<TaskType>
}) {
  const listId = useId()
  const rootRef = useRef<HTMLDivElement>(null)
  const selected = taskTypes.find((row) => row.id === valueTaskTypeId) ?? null
  const [query, setQuery] = useState(selected?.name ?? '')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setQuery(selected?.name ?? '')
  }, [selected?.id, selected?.name])

  const suggestions = useMemo(() => buildTaskTypeSuggestions(taskTypes, query), [taskTypes, query])

  useEffect(() => {
    if (!open) return
    const onPointerDown = (e: PointerEvent) => {
      const el = rootRef.current
      if (!el) return
      if (e.target instanceof Node && !el.contains(e.target)) setOpen(false)
    }
    window.addEventListener('pointerdown', onPointerDown)
    return () => window.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  const inputId = 'block-task-type'

  return (
    <div ref={rootRef} className="relative">
      <label htmlFor={inputId} className="mb-0.5 block font-body text-xs text-on-surface-variant">
        {label}
      </label>
      <input
        id={inputId}
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        className="w-full rounded-xl border border-outline-variant/15 bg-surface px-3 py-2.5 font-body text-sm text-on-surface outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/20"
        value={query}
        onFocus={() => setOpen(true)}
        onChange={(e) => {
          setQuery(e.target.value)
          setOpen(true)
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') setOpen(false)
        }}
      />

      {open && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-20 mt-2 max-h-64 w-full overflow-auto rounded-xl bg-surface-container-lowest shadow-[0_0_40px_rgba(45,52,53,0.08)] dark:bg-stone-950/95 dark:shadow-[0_0_40px_rgba(0,0,0,0.35)]"
        >
          {suggestions.rows.map((row) => {
            const parts = formatTaskTypePathParts(row.name)
            return (
              <li key={row.id}>
                <button
                  type="button"
                  role="option"
                  className="flex w-full items-start gap-2 px-3 py-2 text-left text-sm hover:bg-surface-container-high"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => {
                    onSelectTaskTypeId(row.id)
                    setQuery(row.name)
                    setOpen(false)
                  }}
                >
                  <span className="min-w-0">
                    {parts.ancestorsLabel ? (
                      <span className="text-on-surface-variant">{parts.ancestorsLabel} / </span>
                    ) : null}
                    <span className="text-on-surface">{parts.leafLabel}</span>
                  </span>
                </button>
              </li>
            )
          })}

          {suggestions.createPath ? (
            <li>
              <button
                type="button"
                role="option"
                disabled={busy}
                className="w-full px-3 py-2 text-left text-sm text-primary hover:bg-surface-container-high disabled:opacity-50"
                onMouseDown={(e) => e.preventDefault()}
                onClick={async () => {
                  setBusy(true)
                  try {
                    const created = await onCreateTaskTypePath(suggestions.createPath!)
                    onSelectTaskTypeId(created.id)
                    setQuery(created.name)
                    setOpen(false)
                  } finally {
                    setBusy(false)
                  }
                }}
              >
                {busy ? 'Creating…' : `Create "${suggestions.createPath}"`}
              </button>
            </li>
          ) : null}
        </ul>
      )}
    </div>
  )
}
