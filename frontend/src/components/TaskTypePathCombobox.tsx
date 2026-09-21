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
  const [activeIndex, setActiveIndex] = useState(-1)
  const [busy, setBusy] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

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
    setCreateError(null)
    markChosen()
    onSelectTaskTypeId(taskTypeId)
    setQuery(name)
    setOpen(false)
  }

  const optionCount = suggestions.rows.length + Number(showUnset) + Number(!!suggestions.createPath)
  const activeOptionId = open && activeIndex >= 0 && activeIndex < optionCount
    ? `${listId}-${activeIndex}` : undefined
  const optionProps = (index: number, selected: boolean) => ({
    id: `${listId}-${index}`,
    'aria-selected': selected,
    'data-active': index === activeIndex,
    onMouseMove: () => setActiveIndex(index),
  })

  useEffect(() => {
    if (activeOptionId) document.getElementById(activeOptionId)?.scrollIntoView?.({ block: 'nearest' })
  }, [activeOptionId])

  const create = async () => {
    if (busy || !suggestions.createPath) return
    setCreateError(null)
    markChosen()
    setBusy(true)
    try {
      const created = await onCreateTaskTypePath(suggestions.createPath)
      choose(created.id, created.name)
    } catch {
      setCreateError('Could not create Task Type. Try again.')
    } finally {
      setBusy(false)
    }
  }

  const commitActive = () => {
    if (showUnset && activeIndex === (unsetSelected ? 0 : suggestions.rows.length)) {
      choose(null, '')
      return
    }
    const row = suggestions.rows[activeIndex - Number(showUnset && unsetSelected)]
    if (row) choose(row.id, row.name)
    else void create()
  }

  return (
    <div
      ref={rootRef}
      className="relative"
      onBlur={(event) => {
        const next = event.relatedTarget
        if (next instanceof Node && rootRef.current?.contains(next)) return
        setOpen(false)
        setQuery(selected?.name ?? '')
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
        aria-controls={open ? listId : undefined}
        aria-activedescendant={activeOptionId}
        aria-autocomplete="list"
        className="w-full rounded-xl border border-outline-variant/15 bg-surface px-3 py-2.5 font-body text-sm text-on-surface outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/20 dark:border-dark-outline-variant dark:bg-dark-surface-container-lowest dark:text-dark-on-surface"
        value={query}
        placeholder={allowUnset ? 'Unset' : undefined}
        onFocus={() => { setOpen(true); setActiveIndex(-1) }}
        onChange={(e) => {
          setQuery(e.target.value)
          setActiveIndex(-1)
          setOpen(true)
        }}
        onKeyDown={(e) => {
          if (e.nativeEvent.isComposing) return
          if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
            e.preventDefault()
            setOpen(true)
            const direction = e.key === 'ArrowDown' ? 1 : -1
            setActiveIndex((index) => {
              if (!optionCount) return -1
              if (!open || index < 0 || index >= optionCount) return direction === 1 ? 0 : optionCount - 1
              return (index + direction + optionCount) % optionCount
            })
          } else if (e.key === 'Enter' && activeOptionId) {
            e.preventDefault()
            commitActive()
          } else if (e.key === 'Escape' && open) {
            e.preventDefault()
            e.stopPropagation()
            setOpen(false)
            setActiveIndex(-1)
          }
        }}
      />

      {createError && <p role="alert" className="mt-2 text-sm text-error">{createError}</p>}
      {recommendation && <div className="mt-2 flex items-center gap-2 rounded-xl border border-outline-variant/30 bg-surface-container-low p-2 text-sm dark:bg-dark-surface-container">
        <button type="button" className="flex-1 text-left" onClick={() => choose(recommendation.id, recommendation.name)}>Suggested: <strong>{recommendation.name}</strong><span className="ml-2 underline">Use</span></button>
        <button type="button" aria-label="Dismiss Task Type recommendation" className="min-h-10 min-w-10" onClick={dismiss}>×</button>
      </div>}
      {open && (
        <div className="absolute z-20 mt-2 w-full overflow-hidden rounded-xl bg-surface-container-lowest shadow-[0_0_40px_rgba(45,52,53,0.08)] dark:bg-dark-surface-container-lowest/95 dark:shadow-[0_0_40px_rgba(0,0,0,0.35)]">
        <ul
          id={listId}
          role="listbox"
          aria-label={`${label} options`}
          className="max-h-64 overflow-auto"
        >
          {showUnset && unsetSelected ? (
            <UnsetOption {...optionProps(unsetSelected ? 0 : suggestions.rows.length, unsetSelected)} onChoose={() => choose(null, '')} />
          ) : null}
          {suggestions.rows.map((row, index) => {
            const parts = formatTaskTypePathParts(row.name)
            return (
              <li key={row.id} role="presentation">
                <button
                  type="button"
                  role="option"
                  {...optionProps(index + Number(showUnset && unsetSelected), row.id === valueTaskTypeId)}
                  className="flex w-full items-start gap-2 px-3 py-2 text-left text-sm data-[active=true]:bg-surface-container-high hover:bg-surface-container-high dark:data-[active=true]:bg-dark-surface-container-high dark:hover:bg-dark-surface-container-high"
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
            <UnsetOption {...optionProps(unsetSelected ? 0 : suggestions.rows.length, unsetSelected)} onChoose={() => choose(null, '')} />
          ) : null}

          {suggestions.createPath ? (
            <li role="presentation">
              <button
                type="button"
                role="option"
                {...optionProps(suggestions.rows.length, false)}
                disabled={busy}
                aria-disabled={busy}
                className="w-full px-3 py-2 text-left text-sm text-primary data-[active=true]:bg-surface-container-high hover:bg-surface-container-high disabled:opacity-50 dark:data-[active=true]:bg-dark-surface-container-high dark:hover:bg-dark-surface-container-high"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => void create()}
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

function UnsetOption({ onChoose, ...props }: { onChoose: () => void; id: string; 'aria-selected': boolean; 'data-active': boolean; onMouseMove: () => void }) {
  return (
    <li role="presentation">
      <button
        type="button"
        role="option"
        {...props}
        className="flex w-full px-3 py-2 text-left text-sm text-on-surface-variant data-[active=true]:bg-surface-container-high hover:bg-surface-container-high dark:data-[active=true]:bg-dark-surface-container-high dark:hover:bg-dark-surface-container-high"
        onMouseDown={(e) => e.preventDefault()}
        onClick={onChoose}
      >
        Unset
      </button>
    </li>
  )
}
