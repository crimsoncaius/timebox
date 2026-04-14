import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DayTimeline } from '../../components/DayTimeline'
import { Layout } from '../../components/Layout'
import { api, type BlockLane, type DayRead } from '../../lib/api'

function formatDisplayDate(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  if (!y || !m || !d) return isoDate
  const dt = new Date(Date.UTC(y, m - 1, d))
  return dt.toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

export function TodayPage() {
  const { date } = useParams<{ date: string }>()
  const [day, setDay] = useState<DayRead | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')

  const load = useCallback(async () => {
    if (!date) return
    setLoading(true)
    setError(null)
    try {
      const d = await api.getDay(date)
      setDay(d)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load day')
    } finally {
      setLoading(false)
    }
  }, [date])

  useEffect(() => {
    void load()
  }, [load])

  const patchDaySettings = async (body: Partial<{ start_hour: number; end_hour: number; show_full_day: boolean }>) => {
    if (!date) return
    setSaveState('saving')
    setError(null)
    try {
      const next = await api.patchDay(date, body)
      setDay(next)
      setSaveState('saved')
    } catch (e) {
      setSaveState('error')
      setError(e instanceof Error ? e.message : 'Failed to save settings')
    }
  }

  const createBlock = useCallback(
    async (lane: BlockLane, startMin: number, endMin: number) => {
      if (!date) return
      setSaveState('saving')
      setError(null)
      try {
        const next = await api.createBlock(date, { lane, title: '', start_minute: startMin, end_minute: endMin })
        setDay(next)
        setSaveState('saved')
      } catch (e) {
        setSaveState('error')
        setError(e instanceof Error ? e.message : 'Failed to create block')
      }
    },
    [date],
  )

  const patchBlock = useCallback(
    async (blockId: number, patch: { title?: string; start_minute?: number; end_minute?: number }) => {
      if (!date) return
      setSaveState('saving')
      setError(null)
      try {
        const next = await api.patchBlock(date, blockId, patch)
        setDay(next)
        setSaveState('saved')
      } catch (e) {
        setSaveState('error')
        setError(e instanceof Error ? e.message : 'Failed to update block')
      }
    },
    [date],
  )

  const deleteBlock = useCallback(
    async (blockId: number) => {
      if (!date) return
      setSaveState('saving')
      setError(null)
      try {
        const next = await api.deleteBlock(date, blockId)
        setDay(next)
        setSaveState('saved')
      } catch (e) {
        setSaveState('error')
        setError(e instanceof Error ? e.message : 'Failed to delete block')
      }
    },
    [date],
  )

  if (!date) {
    return (
      <Layout>
        <p className="text-error">Missing date in URL.</p>
      </Layout>
    )
  }

  if (loading) {
    return (
      <Layout>
        <p className="text-on-surface-variant">Loading…</p>
      </Layout>
    )
  }

  if (!day) {
    return (
      <Layout>
        <p className="text-error">{error ?? 'Failed to load day.'}</p>
      </Layout>
    )
  }

  return (
    <Layout>
      <span data-testid="day-date" className="sr-only">
        {day.date}
      </span>
      <section className="mb-16">
        <div className="flex items-baseline justify-between gap-6">
          <div>
            <h1 className="mb-2 font-headline text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
              {formatDisplayDate(day.date)}
            </h1>
            <p className="max-w-xl font-body text-lg font-light leading-relaxed text-on-surface-variant">
              Timezone {day.meta.timezone}. Server today {day.meta.today}.
            </p>
            <p className="mt-2 text-xs text-outline">
              <span
                className={
                  saveState === 'error'
                    ? 'text-error'
                    : saveState === 'saving'
                      ? 'text-tertiary'
                      : saveState === 'saved'
                        ? 'text-tertiary'
                        : 'text-outline'
                }
              >
                {saveState === 'saving' && 'Saving…'}
                {saveState === 'saved' && 'Saved'}
                {saveState === 'error' && 'Save failed'}
                {saveState === 'idle' && '\u00a0'}
              </span>
            </p>
          </div>
        </div>
      </section>

      {error && (
        <div className="mb-6 rounded-xl border border-error-container bg-error-container/20 px-4 py-3 text-sm text-on-error-container">
          {error}
        </div>
      )}

      <details className="mb-10 rounded-xl border border-outline-variant/20 bg-surface-container-low/60 p-4">
        <summary className="cursor-pointer font-headline text-sm font-medium text-on-surface-variant">
          Day window
        </summary>
        <div className="mt-4 flex flex-wrap items-end gap-4">
          <label className="flex flex-col text-sm text-on-surface-variant">
            <span className="mb-1">Start hour (0–23)</span>
            <input
              type="number"
              min={0}
              max={23}
              className="w-24 rounded-lg border border-outline-variant/40 bg-surface-container-lowest px-2 py-1.5 text-on-surface focus:ring-1 focus:ring-primary/30"
              defaultValue={day.start_hour}
              key={`${day.date}-start-${day.updated_at}`}
              onBlur={(e) => {
                const v = Number(e.target.value)
                if (Number.isFinite(v)) void patchDaySettings({ start_hour: v })
              }}
            />
          </label>
          <label className="flex flex-col text-sm text-on-surface-variant">
            <span className="mb-1">End hour (exclusive, 1–24)</span>
            <input
              type="number"
              min={1}
              max={24}
              className="w-24 rounded-lg border border-outline-variant/40 bg-surface-container-lowest px-2 py-1.5 text-on-surface focus:ring-1 focus:ring-primary/30"
              defaultValue={day.end_hour}
              key={`${day.date}-end-${day.updated_at}`}
              onBlur={(e) => {
                const v = Number(e.target.value)
                if (Number.isFinite(v)) void patchDaySettings({ end_hour: v })
              }}
            />
          </label>
          <label className="flex items-center gap-2 text-sm text-on-surface-variant">
            <input
              type="checkbox"
              checked={day.show_full_day}
              onChange={(e) => void patchDaySettings({ show_full_day: e.target.checked })}
            />
            Show full 24 hours
          </label>
        </div>
      </details>

      <section className="overflow-x-auto pb-24">
        <DayTimeline
          day={day}
          readOnly={false}
          onCreateBlock={createBlock}
          onPatchBlock={patchBlock}
          onDeleteBlock={deleteBlock}
        />
      </section>

      <Link
        to={`/review/${day.date}`}
        className="fixed bottom-12 right-12 z-[70] flex h-16 w-16 items-center justify-center rounded-full bg-primary text-on-primary shadow-2xl shadow-primary/20 transition-all hover:scale-105 active:scale-95"
        aria-label="Open review for this day"
      >
        <span className="material-symbols-outlined text-3xl">edit_note</span>
      </Link>
    </Layout>
  )
}
