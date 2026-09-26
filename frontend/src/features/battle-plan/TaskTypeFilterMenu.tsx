import { useMemo, useState, type ReactNode } from 'react'
import type { BattleTask, TaskType } from '../../lib/api'
import { formatTaskTypePathParts, rankTaskTypes } from '../../lib/taskTypePaths'
import { coveringTaskType, isUnderTaskType, toggleTaskTypeFilter } from './taskTypeFilter'

const UNSPECIFIED = 'unspecified'

/**
 * The Battle Plan Task Type filter: chosen types as removable chips, then a search over
 * existing types with a bounded multi-select list. It never creates types.
 *
 * @param tasks the tasks whose counts each row shows, before any other filter applies.
 */
export function TaskTypeFilterMenu({ taskTypes, tasks, selected, onChange }: {
  taskTypes: TaskType[]
  tasks: BattleTask[]
  selected: string[]
  onChange: (next: string[]) => void
}) {
  const [query, setQuery] = useState('')
  const types = useMemo(() => taskTypes.filter((type) => type.name !== UNSPECIFIED), [taskTypes])
  const results = useMemo(() => rankTaskTypes(types, query), [types, query])
  const counts = useMemo(() => new Map(types.map((type) => [
    type.id,
    tasks.filter((task) => task.task_type != null && (task.task_type.id === type.id || isUnderTaskType(task.task_type, type))).length,
  ])), [types, tasks])
  const unsetCount = tasks.filter((task) => task.task_type_id == null).length
  const chosen = types.filter((type) => selected.includes(String(type.id))).sort((a, b) => a.name.localeCompare(b.name))
  const toggle = (key: string) => onChange(toggleTaskTypeFilter(selected, key, types))

  return (
    <details className="relative">
      <summary className="cursor-pointer list-none rounded-full bg-surface-container-lowest px-3 py-2 text-xs dark:bg-dark-surface-container">
        Task types{selected.length ? ` · ${selected.length}` : ''}
      </summary>
      <div className="absolute left-0 top-11 z-40 w-80 rounded-2xl bg-surface-container-lowest p-3 shadow-xl dark:bg-dark-surface-container-high">
        {chosen.length || selected.includes('unset') ? (
          <div className="mb-2 flex flex-wrap gap-1.5" aria-label="Chosen task types">
            {chosen.map((type) => {
              const parts = formatTaskTypePathParts(type.name)
              const subtypes = types.filter((row) => isUnderTaskType(row, type)).length
              return (
                <button
                  key={type.id}
                  type="button"
                  aria-label={`Remove ${type.name}${subtypes ? ` and ${subtypes} sub-types` : ''}`}
                  className="flex max-w-full items-center gap-1.5 rounded-full border border-outline-variant/40 bg-surface-container-high px-2.5 py-1 text-xs dark:bg-dark-surface-container-highest"
                  onClick={() => toggle(String(type.id))}
                >
                  <span className="min-w-0 truncate">
                    {parts.ancestorsLabel ? <span className="text-on-surface-variant">{parts.ancestorsLabel} / </span> : null}
                    <span>{parts.leafLabel}</span>
                    {/* The count says the chip reaches past the one type it names. */}
                    {subtypes ? <span className="text-on-surface-variant">{`  +${subtypes}`}</span> : null}
                  </span>
                  <span aria-hidden="true" className="text-on-surface-variant">×</span>
                </button>
              )
            })}
            {selected.includes('unset') ? (
              <button type="button" aria-label="Remove Unset" className="flex items-center gap-1.5 rounded-full border border-outline-variant/40 bg-surface-container-high px-2.5 py-1 text-xs dark:bg-dark-surface-container-highest" onClick={() => toggle('unset')}>
                Unset <span aria-hidden="true" className="text-on-surface-variant">×</span>
              </button>
            ) : null}
          </div>
        ) : null}
        <input
          type="search"
          aria-label="Search task types"
          placeholder="Search types"
          value={query}
          autoComplete="off"
          spellCheck={false}
          className="w-full rounded-xl border border-outline-variant/40 bg-surface-container-low px-3 py-2 text-sm dark:bg-dark-surface-container"
          onChange={(event) => setQuery(event.target.value)}
        />
        <ul aria-label="Task type options" className="mt-2 max-h-56 overflow-y-auto rounded-xl border border-outline-variant/20">
          {!query.trim() ? (
            <FilterRow name="Unset" label={<span>Unset</span>} count={unsetCount} checked={selected.includes('unset')} onToggle={() => toggle('unset')} />
          ) : null}
          {results.map((type) => {
            const key = String(type.id)
            const parts = formatTaskTypePathParts(type.name)
            const via = selected.includes(key) ? null : coveringTaskType(type, selected, types)
            return (
              <FilterRow
                key={type.id}
                name={type.name}
                label={(
                  <>
                    {parts.ancestorsLabel ? <span className="text-on-surface-variant">{parts.ancestorsLabel} / </span> : null}
                    <span className="font-medium">{parts.leafLabel}</span>
                  </>
                )}
                note={via ? `Included via ${via.name}` : undefined}
                count={counts.get(type.id) ?? 0}
                checked={selected.includes(key) || via != null}
                // A covered row is already matched by its ancestor; clear that ancestor to narrow.
                disabled={via != null}
                onToggle={() => toggle(key)}
              />
            )
          })}
          {query.trim() && results.length === 0 ? (
            <li className="px-3 py-3 text-xs text-on-surface-variant">No type matches that path.</li>
          ) : null}
        </ul>
      </div>
    </details>
  )
}

function FilterRow({ name, label, note, count, checked, disabled = false, onToggle }: {
  name: string
  label: ReactNode
  note?: string
  count: number
  checked: boolean
  disabled?: boolean
  onToggle: () => void
}) {
  return (
    <li>
      <label className={`flex items-center gap-2 px-3 py-2 text-sm ${disabled ? 'text-on-surface-variant' : 'cursor-pointer hover:bg-surface-container-low dark:hover:bg-dark-surface-container'}`}>
        <span className="min-w-0 flex-1">
          <span className="block truncate">{label}</span>
          {note ? <span className="block text-[11px] text-on-surface-variant">{note}</span> : null}
        </span>
        <span className="font-mono text-xs text-on-surface-variant">{count}</span>
        <input type="checkbox" aria-label={name} checked={checked} disabled={disabled} onChange={onToggle} />
      </label>
    </li>
  )
}
