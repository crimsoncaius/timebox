import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { DeleteTaskTypeResolutionModal } from '../../components/DeleteTaskTypeResolutionModal'
import { ApiHttpError, api, TASK_TYPE_STILL_IN_USE_DETAIL, type TaskType } from '../../lib/api'
import {
  filterTaskTypesByQuery,
  formatTaskTypePathParts,
  groupTaskTypesByRoot,
  pathDepth,
} from '../../lib/taskTypePaths'

function saveStatusClass(saveState: 'idle' | 'saving' | 'saved' | 'error') {
  if (saveState === 'error') return 'text-error'
  if (saveState === 'saving' || saveState === 'saved') return 'text-tertiary'
  return 'text-on-surface-variant'
}

function apiErrorMessage(e: unknown, fallback: string): string {
  if (e instanceof ApiHttpError) return e.detailMessage
  if (e instanceof Error) return e.message
  return fallback
}

export function TaskTypesPage() {
  const [types, setTypes] = useState<TaskType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [newName, setNewName] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editValue, setEditValue] = useState('')
  const [resolveDelete, setResolveDelete] = useState<{ id: number; name: string } | null>(null)
  const [resolveBusy, setResolveBusy] = useState(false)

  const visibleTypes = useMemo(() => filterTaskTypesByQuery(types, newName), [types, newName])

  const grouped = useMemo(() => groupTaskTypesByRoot(visibleTypes), [visibleTypes])

  const migrateTargets = useMemo(
    () => (resolveDelete ? types.filter((t) => t.id !== resolveDelete.id) : []),
    [types, resolveDelete],
  )

  const displayRows = useMemo(() => {
    let stripe = 0
    return grouped.map((g) => ({
      root: g.root,
      rows: g.items.map((t) => ({ t, stripeIndex: stripe++ })),
    }))
  }, [grouped])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const rows = await api.listTaskTypes()
      setTypes(rows)
    } catch (e) {
      setError(apiErrorMessage(e, 'Failed to load task types'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const addType = async () => {
    const name = newName.trim()
    if (!name) return
    setSaveState('saving')
    setError(null)
    try {
      await api.createTaskType({ name })
      await load()
      setNewName('')
      setSaveState('saved')
    } catch (e) {
      setSaveState('error')
      setError(apiErrorMessage(e, 'Failed to create task type'))
    }
  }

  const rename = async (id: number, name: string) => {
    setSaveState('saving')
    setError(null)
    try {
      await api.patchTaskType(id, { name })
      await load()
      setEditingId(null)
      setSaveState('saved')
    } catch (e) {
      setSaveState('error')
      setError(apiErrorMessage(e, 'Failed to update task type'))
    }
  }

  const attemptRemove = async (id: number) => {
    setEditingId((cur) => (cur === id ? null : cur))
    setSaveState('saving')
    setError(null)
    try {
      await api.deleteTaskType(id)
      await load()
      setSaveState('saved')
    } catch (e) {
      if (
        e instanceof ApiHttpError &&
        e.status === 409 &&
        e.detailMessage === TASK_TYPE_STILL_IN_USE_DETAIL
      ) {
        const row = types.find((t) => t.id === id)
        setResolveDelete({ id, name: row?.name ?? `Task type #${id}` })
        setSaveState('idle')
        setError(null)
        return
      }
      setSaveState('error')
      setError(apiErrorMessage(e, 'Failed to delete task type'))
    }
  }

  const confirmCascadeDelete = async () => {
    if (!resolveDelete) return
    setResolveBusy(true)
    setError(null)
    try {
      await api.deleteTaskType(resolveDelete.id, { cascadeBlocks: true })
      setResolveDelete(null)
      await load()
      setSaveState('saved')
    } catch (e) {
      setResolveDelete(null)
      setSaveState('error')
      setError(apiErrorMessage(e, 'Failed to delete task type'))
    } finally {
      setResolveBusy(false)
    }
  }

  const confirmMigrateDelete = async (targetId: number) => {
    if (!resolveDelete) return
    setResolveBusy(true)
    setError(null)
    try {
      await api.deleteTaskType(resolveDelete.id, { migrateBlocksTo: targetId })
      setResolveDelete(null)
      await load()
      setSaveState('saved')
    } catch (e) {
      setResolveDelete(null)
      setSaveState('error')
      setError(apiErrorMessage(e, 'Failed to delete task type'))
    } finally {
      setResolveBusy(false)
    }
  }

  const startEdit = (t: TaskType) => {
    setEditingId(t.id)
    setEditValue(t.name)
  }

  const commitEdit = (t: TaskType) => {
    const v = editValue.trim()
    if (!v) {
      setEditValue(t.name)
      setEditingId(null)
      return
    }
    if (v !== t.name) void rename(t.id, v)
    else setEditingId(null)
  }

  if (loading) {
    return (
      <Layout>
        <p className="font-body text-on-surface-variant">Loading…</p>
      </Layout>
    )
  }

  return (
    <Layout>
      <div className="flex flex-col gap-12 lg:flex-row lg:items-start lg:gap-16 xl:gap-24">
        {/* Left: editorial intro — intentional asymmetry */}
        <section className="min-w-0 shrink-0 lg:max-w-md lg:pt-4">
          <div className="space-y-5">
            <h1 className="font-headline text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
              Task types
            </h1>
            <p className="max-w-xl font-body text-lg font-light leading-relaxed text-on-surface-variant">
              Saved task type paths for time blocks (e.g. work, coding, coding/ai, exercise/cardio). Add a{' '}
              <Link
                to="/"
                className="text-primary underline decoration-primary/30 underline-offset-2 transition-colors hover:text-on-surface"
              >
                Day
              </Link>{' '}
              block only after at least one type exists.
            </p>
          </div>
          <div
            className="mt-10 inline-flex items-center gap-2 rounded-full border border-outline-variant/15 bg-surface-container-low/90 px-3 py-1.5 text-xs font-medium backdrop-blur-sm dark:border-dark-outline-variant dark:bg-dark-surface-container/50"
            aria-live="polite"
          >
            <span
              className={[
                'h-1.5 w-1.5 shrink-0 rounded-full',
                saveState === 'saving' && 'animate-pulse bg-tertiary',
                saveState === 'saved' && 'bg-tertiary',
                saveState === 'error' && 'bg-error',
                saveState === 'idle' && 'bg-outline-variant/50',
              ]
                .filter(Boolean)
                .join(' ')}
              aria-hidden
            />
            <span className={['font-label tracking-tight', saveStatusClass(saveState)].join(' ')}>
              {saveState === 'saving' && 'Saving…'}
              {saveState === 'saved' && 'Saved'}
              {saveState === 'error' && 'Save failed'}
              {saveState === 'idle' && 'Up to date'}
            </span>
          </div>
        </section>

        {/* Right: workspace — composer + list (tonal layering, no box borders) */}
        <div className="min-w-0 flex-1 space-y-10">
          {error && (
            <div
              className="rounded-2xl bg-error-container/25 px-4 py-3 text-sm text-on-error-container outline-1 outline-error/20 dark:bg-error-container/15 dark:outline-error/30"
              role="alert"
            >
              {error}
            </div>
          )}

          {/* Composer: glass lift + gradient CTA */}
          <section
            className="rounded-2xl bg-surface-container-lowest/85 p-5 shadow-[0_0_40px_rgba(45,52,53,0.04)] backdrop-blur-xl dark:bg-dark-surface-container-lowest/85 dark:shadow-[0_0_40px_rgba(0,0,0,0.25)]"
            aria-labelledby="task-types-composer-heading"
          >
            <h2
              id="task-types-composer-heading"
              className="mb-3 font-headline text-sm font-normal tracking-tight text-on-surface-variant"
            >
              Task type
            </h2>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:gap-4">
              <label className="min-w-0 flex-1">
                <span className="sr-only">Filter saved types or enter a new task type name</span>
                <input
                  className="w-full rounded-xl bg-surface-container-low/80 px-4 py-3 font-body text-base font-light text-on-surface shadow-inner shadow-black/3 outline-none transition-[background-color,box-shadow] placeholder:text-on-surface-variant focus-visible:bg-surface-container-high/90 focus-visible:ring-1 focus-visible:ring-primary/20 dark:bg-dark-surface-container/60 dark:text-dark-on-surface dark:shadow-black/20 dark:focus-visible:bg-dark-surface-container-high/80"
                  value={newName}
                  placeholder="Search or add (e.g. work)"
                  onChange={(e) => setNewName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') void addType()
                  }}
                />
              </label>
              <button
                type="button"
                className="shrink-0 rounded-md bg-linear-to-br from-primary to-primary-dim px-5 py-3 font-headline text-sm font-light tracking-tight text-on-primary shadow-none transition-opacity hover:opacity-95 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary/40 dark:from-primary/90 dark:to-primary-dim/90"
                onClick={() => void addType()}
              >
                Add
              </button>
            </div>
          </section>

          {/* Saved list: grouped by root segment, breadcrumb display, edit on demand */}
          <section aria-labelledby="task-types-saved-heading">
            <h2
              id="task-types-saved-heading"
              className="mb-4 font-headline text-sm font-normal tracking-tight text-on-surface-variant"
            >
              Saved types
            </h2>
            {types.length === 0 ? (
              <p className="font-body text-sm font-light leading-relaxed text-on-surface-variant">
                No task types yet. Add one above.
              </p>
            ) : visibleTypes.length === 0 ? (
              <p className="font-body text-sm font-light leading-relaxed text-on-surface-variant">
                No matching task types.
              </p>
            ) : (
              <div className="space-y-8">
                {displayRows.map(({ root, rows }) => (
                  <div key={root} className="space-y-2">
                    <h3 className="font-headline text-xs font-normal uppercase tracking-[0.2em] text-on-surface-variant">
                      {root}
                    </h3>
                    <ul className="space-y-2">
                      {rows.map(({ t, stripeIndex }) => {
                        const parts = formatTaskTypePathParts(t.name)
                        const depth = pathDepth(t.name)
                        const indentPx = 8 + depth * 14
                        const isEditing = editingId === t.id
                        return (
                          <li
                            key={t.id}
                            className={[
                              'flex flex-wrap items-center gap-2 rounded-xl px-2 py-1 transition-colors sm:gap-3',
                              stripeIndex % 2 === 0 ? 'bg-transparent' : 'bg-surface-container-low/50 dark:bg-dark-surface-container/35',
                            ].join(' ')}
                          >
                            {isEditing ? (
                              <input
                                className="min-w-0 flex-1 rounded-md border-0 bg-surface-container-highest/80 px-3 py-2 font-body text-base font-light text-on-surface shadow-none outline-none ring-0 transition-colors focus-visible:ring-1 focus-visible:ring-primary/15 dark:bg-dark-surface-container-high/70 dark:text-dark-on-surface"
                                value={editValue}
                                aria-label={`Task type name ${t.id}`}
                                autoFocus
                                onChange={(e) => setEditValue(e.target.value)}
                                onBlur={() => commitEdit(t)}
                                onKeyDown={(e) => {
                                  if (e.key === 'Escape') {
                                    e.preventDefault()
                                    setEditValue(t.name)
                                    setEditingId(null)
                                  }
                                  if (e.key === 'Enter') {
                                    e.preventDefault()
                                    ;(e.target as HTMLInputElement).blur()
                                  }
                                }}
                              />
                            ) : (
                              <div
                                className="min-w-0 flex-1 py-2 font-body text-base font-light"
                                style={{ paddingLeft: indentPx }}
                                aria-label={`Task type ${t.name}`}
                              >
                                <span className="text-on-surface">
                                  {parts.ancestorsLabel ? (
                                    <>
                                      <span className="text-on-surface-variant">{parts.ancestorsLabel} / </span>
                                      <span>{parts.leafLabel}</span>
                                    </>
                                  ) : (
                                    <span>{parts.leafLabel}</span>
                                  )}
                                </span>
                              </div>
                            )}
                            {!isEditing && (
                              <button
                                type="button"
                                className="shrink-0 rounded-md border border-outline-variant/15 bg-transparent px-3 py-2 font-label text-xs uppercase tracking-wider text-on-surface-variant transition-colors hover:bg-surface-container-high focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary/30 dark:border-dark-outline-variant dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container-high"
                                aria-label={`Edit ${t.name}`}
                                onClick={() => startEdit(t)}
                              >
                                Edit
                              </button>
                            )}
                            <button
                              type="button"
                              className="shrink-0 rounded-md border border-outline-variant/15 bg-transparent p-2 text-error transition-colors hover:bg-error-container/15 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-error/30 dark:border-dark-outline-variant dark:hover:bg-error-container/10"
                              aria-label={`Delete ${t.name}`}
                              title={`Delete ${t.name}`}
                              onClick={() => void attemptRemove(t.id)}
                            >
                              <span className="material-symbols-outlined text-[20px]" aria-hidden>
                                delete
                              </span>
                            </button>
                          </li>
                        )
                      })}
                    </ul>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
      <DeleteTaskTypeResolutionModal
        open={resolveDelete !== null}
        taskTypeName={resolveDelete?.name ?? ''}
        migrateTargets={migrateTargets}
        busy={resolveBusy}
        onClose={() => {
          if (!resolveBusy) setResolveDelete(null)
        }}
        onCascade={() => void confirmCascadeDelete()}
        onMigrate={(targetId) => void confirmMigrateDelete(targetId)}
      />
    </Layout>
  )
}
