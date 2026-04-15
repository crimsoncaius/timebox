import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { api, type TaskType } from '../../lib/api'

function saveStatusClass(saveState: 'idle' | 'saving' | 'saved' | 'error') {
  if (saveState === 'error') return 'text-error'
  if (saveState === 'saving' || saveState === 'saved') return 'text-tertiary'
  return 'text-on-surface-variant'
}

export function TaskTypesPage() {
  const [types, setTypes] = useState<TaskType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [newName, setNewName] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const rows = await api.listTaskTypes()
      setTypes(rows)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load task types')
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
      setError(e instanceof Error ? e.message : 'Failed to create task type')
    }
  }

  const rename = async (id: number, name: string) => {
    setSaveState('saving')
    setError(null)
    try {
      await api.patchTaskType(id, { name })
      await load()
      setSaveState('saved')
    } catch (e) {
      setSaveState('error')
      setError(e instanceof Error ? e.message : 'Failed to update task type')
    }
  }

  const remove = async (id: number) => {
    setSaveState('saving')
    setError(null)
    try {
      await api.deleteTaskType(id)
      await load()
      setSaveState('saved')
    } catch (e) {
      setSaveState('error')
      setError(e instanceof Error ? e.message : 'Failed to delete task type')
    }
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
        <section className="min-w-0 shrink-0 lg:max-w-md lg:pt-2">
          <h1 className="mb-3 font-headline text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
            Task types
          </h1>
          <p className="max-w-xl font-body text-lg font-light leading-relaxed text-on-surface-variant">
            Saved task type paths for time blocks (e.g. work, coding, coding/ai, exercise/cardio). Add a{' '}
            <Link
              to="/"
              className="text-primary underline decoration-primary/30 underline-offset-2 transition-colors hover:text-on-surface"
            >
              Today
            </Link>{' '}
            block only after at least one type exists.
          </p>
          <div
            className="mt-6 inline-flex items-center gap-2 rounded-full border border-outline-variant/15 bg-surface-container-low/90 px-3 py-1.5 text-xs font-medium backdrop-blur-sm dark:border-stone-600/40 dark:bg-stone-900/50"
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
            className="rounded-2xl bg-surface-container-lowest/85 p-5 shadow-[0_0_40px_rgba(45,52,53,0.04)] backdrop-blur-xl dark:bg-stone-950/85 dark:shadow-[0_0_40px_rgba(0,0,0,0.25)]"
            aria-labelledby="task-types-new-heading"
          >
            <h2
              id="task-types-new-heading"
              className="mb-3 font-headline text-sm font-normal tracking-tight text-on-surface-variant"
            >
              New task type
            </h2>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:gap-4">
              <label className="min-w-0 flex-1">
                <span className="sr-only">New task type name</span>
                <input
                  className="w-full rounded-xl bg-surface-container-low/80 px-4 py-3 font-body text-base font-light text-on-surface shadow-inner shadow-black/3 outline-none transition-[background-color,box-shadow] placeholder:text-on-surface-variant/70 focus-visible:bg-surface-container-high/90 focus-visible:ring-2 focus-visible:ring-primary/20 dark:bg-stone-900/60 dark:shadow-black/20 dark:focus-visible:bg-stone-800/80"
                  value={newName}
                  placeholder="e.g. work"
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

          {/* Saved list: gap rule, alternating tone, invisible row inputs */}
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
            ) : (
              <ul className="space-y-4">
                {types.map((t, index) => (
                  <li
                    key={t.id}
                    className={[
                      'flex flex-wrap items-center gap-3 rounded-xl px-3 py-2 transition-colors sm:gap-4',
                      index % 2 === 0 ? 'bg-transparent' : 'bg-surface-container-low/50 dark:bg-stone-900/35',
                    ].join(' ')}
                  >
                    <input
                      className="min-w-0 flex-1 rounded-md border-0 bg-transparent px-2 py-2 font-body text-base font-light text-on-surface shadow-none outline-none ring-0 transition-colors placeholder:text-on-surface-variant hover:bg-surface-container-highest/60 focus:bg-surface-container-highest/80 focus:text-primary focus-visible:ring-2 focus-visible:ring-primary/15 dark:hover:bg-stone-800/50 dark:focus:bg-stone-800/70"
                      defaultValue={t.name}
                      aria-label={`Task type name ${t.id}`}
                      key={`${t.id}-${t.updated_at}`}
                      onBlur={(e) => {
                        const v = e.target.value.trim()
                        if (v && v !== t.name) void rename(t.id, v)
                      }}
                    />
                    <button
                      type="button"
                      className="shrink-0 rounded-md border border-outline-variant/15 bg-transparent px-3 py-2 font-label text-xs uppercase tracking-wider text-error transition-colors hover:bg-error-container/15 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-error/30 dark:border-stone-600/40 dark:hover:bg-error-container/10"
                      onClick={() => void remove(t.id)}
                    >
                      Delete
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
    </Layout>
  )
}
