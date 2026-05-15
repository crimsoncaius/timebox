import { useCallback, useEffect, useMemo, useState } from 'react'
import { Layout } from '../../components/Layout'
import { api, type DayListItem } from '../../lib/api'
import { ChronicleMonthGrid } from './ChronicleMonthGrid'
import { daysByDate, shiftMonth } from './historyCalendar'

export function HistoryPage() {
  const [rows, setRows] = useState<DayListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.listDays(500)
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

  const derivedDefaultMonth = useMemo(() => {
    if (rows.length > 0) {
      const [y, m] = rows[0].date.split('-').map(Number)
      return { y, m }
    }
    const t = new Date()
    return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1 }
  }, [rows])

  const [viewMonth, setViewMonth] = useState<{ y: number; m: number } | null>(null)
  const year = viewMonth?.y ?? derivedDefaultMonth.y
  const month = viewMonth?.m ?? derivedDefaultMonth.m

  const byDate = useMemo(() => daysByDate(rows), [rows])

  const onPrevMonth = useCallback(() => {
    const n = shiftMonth(year, month, -1)
    setViewMonth({ y: n.year, m: n.month })
  }, [year, month])

  const onNextMonth = useCallback(() => {
    const n = shiftMonth(year, month, 1)
    setViewMonth({ y: n.year, m: n.month })
  }, [year, month])

  const onThisMonth = useCallback(() => {
    const t = new Date()
    setViewMonth({ y: t.getUTCFullYear(), m: t.getUTCMonth() + 1 })
  }, [])

  return (
    <Layout>
      <section className="mb-16 flex items-end justify-between gap-8">
        <div className="max-w-2xl">
          <h1 className="mb-2 font-headline text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
            Chronicle of focus
          </h1>
          <p className="max-w-xl font-body text-lg font-light leading-relaxed text-on-surface-variant">
            Browse by month. Days you have opened appear in the archive; any day opens in Day.
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
        <p className="mb-10 text-on-surface-variant">No days yet. Open Day to create your first day.</p>
      )}

      {!loading && (
        <ChronicleMonthGrid
          year={year}
          month={month}
          byDate={byDate}
          onPrevMonth={onPrevMonth}
          onNextMonth={onNextMonth}
          onThisMonth={onThisMonth}
        />
      )}
    </Layout>
  )
}
