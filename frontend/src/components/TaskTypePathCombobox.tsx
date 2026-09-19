import { useEffect, useId, useMemo, useRef, useState } from 'react'
import type { TaskType } from '../lib/api'
import { useTaskTypeRecommendation } from './useTaskTypeRecommendation'
import {
  buildTaskTypeSuggestions,
  createAncestorHint,
  formatTaskTypePathParts,
} from '../lib/taskTypePaths'

const UNSPECIFIED = 'unspecified'

export function TaskTypePathCombobox({
  label,
  taskTypes,
  valueTaskTypeId,
  onSelectTaskTypeId,
  onCreateTaskTypePath,
  allowUnset = false,
  recommendationName,
  recommendationEnabled = true,
}: {
  label: string
  taskTypes: TaskType[]
  valueTaskTypeId: number | null
  onSelectTaskTypeId: (taskTypeId: number | null) => void
  onCreateTaskTypePath: (path: string) => Promise<TaskType>
  allowUnset?: boolean
  recommendationName?: string
  recommendationEnabled?: boolean
}) {
  const { recommendation, markChosen, dismiss } = useTaskTypeRecommendation(recommendationName, taskTypes, valueTaskTypeId, recommendationEnabled)
  const listId = useId()
  const inputId = useId()
  const rootRef = useRef<HTMLDivElement>(null)
  const pickerTypes = useMemo(
    () => (allowUnset ? taskTypes.filter((row) => row.name !== UNSPECIFIED) : taskTypes),
    [allowUnset, taskTypes],
  )
  const selected = pickerTypes.find((row) => row.id === valueTaskTypeId) ?? null
  const unsetSelected = allowUnset && (valueTaskTypeId == null || taskTypes.find((row) => row.id === valueTaskTypeId)?.name === UNSPECIFIED)
  const [query, setQuery] = useState(selected?.name ?? '')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setQuery(selected?.name ?? '')
  }, [selected?.id, selected?.name])

  const suggestions = useMemo(
    () => buildTaskTypeSuggestions(pickerTypes, query, unsetSelected ? null : valueTaskTypeId),
    [pickerTypes, query, unsetSelected, valueTaskTypeId],
  )
  const hint = useMemo(
    () => (suggestions.createPath ? createAncestorHint(pickerTypes, suggestions.createPath) : null),
    [pickerTypes, suggestions.createPath],
  )
  const showUnset = allowUnset && suggestions.createPath == null && query.trim() === ''

  const choose = (taskTypeId: number | null, name: string) => {
    markChosen()
    onSelectTaskTypeId(taskTypeId)
    setQuery(name)
    setOpen(false)
  }

  return (
    <div
      ref={rootRef}
      className="relative"
      onBlur={(event) => {
        const next = event.relatedTarget
        if (next instanceof Node && rootRef.current?.contains(next)) return
        setOpen(false)
      }}
    >
      <label htmlFor={inputId} className="mb-0.5 block font-body text-xs text-on-surface-variant">
        {label}
      </label>
      <input
        id={inputId}
        name="timebox-task-type-search"
        type="text"
        autoComplete="off"
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        className="w-full rounded-xl border border-outline-variant/15 bg-surface px-3 py-2.5 font-body text-sm text-on-surface outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/20 dark:border-dark-outline-variant dark:bg-dark-surface-container-lowest dark:text-dark-on-surface"
        value={query}
        placeholder={allowUnset ? 'Unset' : undefined}
        onFocus={() => setOpen(true)}
        onChange={(e) => {
          setQuery(e.target.value)
          setOpen(true)
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') setOpen(false)
        }}
      />

      {recommendation && <div className="mt-2 flex items-center gap-2 rounded-xl border border-outline-variant/30 bg-surface-container-low p-2 text-sm dark:bg-dark-surface-container">
        <button type="button" className="flex-1 text-left" onClick={() => choose(recommendation.id, recommendation.name)}>Suggested: <strong>{recommendation.name}</strong><span className="ml-2 underline">Use</span></button>
        <button type="button" aria-label="Dismiss Task Type recommendation" className="min-h-10 min-w-10" onClick={dismiss}>×</button>
      </div>}
      {open && (
        <div className="absolute z-20 mt-2 w-full overflow-hidden rounded-xl bg-surface-container-lowest shadow-[0_0_40px_rgba(45,52,53,0.08)] dark:bg-dark-surface-container-lowest/95 dark:shadow-[0_0_40px_rgba(0,0,0,0.35)]">
        <ul
          id={listId}
          role="listbox"
          className="max-h-64 overflow-auto"
        >
          {showUnset && unsetSelected ? (
            <UnsetOption onChoose={() => choose(null, '')} />
          ) : null}
          {suggestions.rows.map((row) => {
            const parts = formatTaskTypePathParts(row.name)
            return (
              <li key={row.id}>
                <button
                  type="button"
                  role="option"
                  className="flex w-full items-start gap-2 px-3 py-2 text-left text-sm hover:bg-surface-container-high dark:hover:bg-dark-surface-container-high"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => choose(row.id, row.name)}
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
          {showUnset && !unsetSelected ? (
            <UnsetOption onChoose={() => choose(null, '')} />
          ) : null}

          {suggestions.createPath ? (
            <li>
              <button
                type="button"
                role="option"
                disabled={busy}
                className="w-full px-3 py-2 text-left text-sm text-primary hover:bg-surface-container-high disabled:opacity-50 dark:hover:bg-dark-surface-container-high"
                onMouseDown={(e) => e.preventDefault()}
                onClick={async () => {
                  markChosen()
                  setBusy(true)
                  try {
                    const created = await onCreateTaskTypePath(suggestions.createPath!)
                    choose(created.id, created.name)
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
        {hint ? (
          <p className="border-t border-outline-variant/15 px-3 py-2 font-body text-[11px] leading-4 text-on-surface-variant">
            {hint.lead}
            <span className="font-mono">{hint.path}</span>
            {hint.tail ?? '.'}
          </p>
        ) : null}
        </div>
      )}
    </div>
  )
}

function UnsetOption({ onChoose }: { onChoose: () => void }) {
  return (
    <li>
      <button
        type="button"
        role="option"
        className="flex w-full px-3 py-2 text-left text-sm text-on-surface-variant hover:bg-surface-container-high dark:hover:bg-dark-surface-container-high"
        onMouseDown={(e) => e.preventDefault()}
        onClick={onChoose}
      >
        Unset
      </button>
    </li>
  )
}
