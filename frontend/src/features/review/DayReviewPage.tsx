import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DayTimeline } from '../../components/DayTimeline'
import { Layout } from '../../components/Layout'
import { api, type DayRead } from '../../lib/api'

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

export function DayReviewPage() {
  const { date } = useParams<{ date: string }>()
  const [day, setDay] = useState<DayRead | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

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

  if (!date) {
    return (
      <Layout mainClassName="bg-[#f7f6f2] dark:bg-stone-900">
        <p className="text-error">Missing date.</p>
      </Layout>
    )
  }

  if (loading) {
    return (
      <Layout mainClassName="bg-[#f7f6f2] dark:bg-stone-900">
        <p className="text-on-surface-variant">Loading…</p>
      </Layout>
    )
  }

  if (!day) {
    return (
      <Layout mainClassName="bg-[#f7f6f2] dark:bg-stone-900">
        <p className="text-error">{error ?? 'Failed to load day.'}</p>
      </Layout>
    )
  }

  return (
    <Layout mainClassName="bg-[#f7f6f2] dark:bg-stone-900">
      <span data-testid="review-day-date" className="sr-only">
        {day.date}
      </span>
      <div className="mx-auto max-w-6xl space-y-12">
        <div className="flex flex-wrap items-end justify-between gap-6">
          <div>
            <p className="mb-2 text-[10px] uppercase tracking-[0.2em] text-outline">Review</p>
            <h1 className="font-headline text-5xl font-light tracking-tight text-on-surface">
              Daily review · {formatDisplayDate(day.date)}
            </h1>
            <p className="mt-4 max-w-lg font-body text-lg leading-relaxed text-on-surface-variant">
              Read-only snapshot of planned vs actual blocks. Edit from Today.
            </p>
          </div>
          <Link
            to={`/day/${day.date}`}
            className="rounded-lg bg-gradient-to-br from-primary to-primary-dim px-5 py-2.5 font-headline text-sm font-semibold uppercase tracking-[0.15em] text-on-primary shadow-sm transition-transform active:scale-95"
          >
            Edit day
          </Link>
        </div>

        {error && (
          <div className="rounded-xl border border-error-container bg-error-container/20 px-4 py-3 text-sm text-on-error-container">
            {error}
          </div>
        )}

        <section className="overflow-x-auto pb-8">
          <DayTimeline
            day={day}
            readOnly
            onCreateBlock={async () => {}}
            onPatchBlock={async () => {}}
          />
        </section>
      </div>
    </Layout>
  )
}
