import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { api, type DayListItem } from '../../lib/api'

function formatShortDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return iso
  const dt = new Date(Date.UTC(y, m - 1, d))
  return dt.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
}

function formatTitle(iso: string, index: number): string {
  if (index === 0) return 'Most recent'
  if (index === 1) return 'Previous'
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return iso
  const dt = new Date(Date.UTC(y, m - 1, d))
  return dt.toLocaleDateString(undefined, { weekday: 'long', timeZone: 'UTC' })
}

export function HistoryPage() {
  const [rows, setRows] = useState<DayListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.listDays(60)
      setRows(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load history')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <Layout>
      <section className="mb-16 flex items-end justify-between gap-8">
        <div className="max-w-2xl">
          <h1 className="mb-4 font-headline text-[2.75rem] font-light leading-none tracking-tighter text-on-surface">
            Chronicle of focus
          </h1>
          <p className="max-w-lg font-body text-lg leading-relaxed text-on-surface-variant">
            Recent days, newest first. Open a day to plan or review blocks.
          </p>
        </div>
      </section>

      {error && (
        <div className="mb-6 rounded-xl border border-error-container bg-error-container/20 px-4 py-3 text-sm text-on-error-container">
          {error}
        </div>
      )}

      {loading && <p className="text-on-surface-variant">Loading…</p>}

      {!loading && rows.length === 0 && (
        <p className="text-on-surface-variant">No days yet. Open Today to create your first day.</p>
      )}

      <div className="grid grid-cols-1 gap-x-12 gap-y-16 md:grid-cols-2 xl:grid-cols-3">
        {rows.map((r, i) => (
          <Link key={r.id} to={`/day/${r.date}`} className="group block cursor-pointer">
            <div className="mb-4 flex items-baseline justify-between">
              <span className="font-headline text-2xl font-light tracking-tight text-on-surface">
                {formatTitle(r.date, i)}
              </span>
              <span className="text-[10px] font-label uppercase tracking-widest text-on-surface-variant">
                {formatShortDate(r.date)}
              </span>
            </div>
            <div className="relative overflow-hidden rounded-xl bg-surface-container-low p-6 transition-all group-hover:bg-surface-container-high">
              <p className="text-xs font-label uppercase tracking-widest text-on-surface-variant/80">
                Window {r.start_hour}:00–{r.end_hour === 24 ? '24' : `${r.end_hour}:00`}
                {r.show_full_day ? ' · full day' : ''}
              </p>
              <div className="mt-4 flex items-center justify-end">
                <span className="material-symbols-outlined text-sm text-on-surface-variant transition-transform group-hover:translate-x-1">
                  arrow_forward
                </span>
              </div>
              <div className="absolute right-0 top-0 h-full w-1 bg-tertiary/15" />
            </div>
          </Link>
        ))}
      </div>
    </Layout>
  )
}
