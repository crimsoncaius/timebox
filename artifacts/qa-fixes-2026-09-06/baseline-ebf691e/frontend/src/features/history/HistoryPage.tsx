import { useCallback, useEffect, useMemo, useState } from 'react'
import { Layout } from '../../components/Layout'
import { api, type DayListItem } from '../../lib/api'
import { ChronicleMonthGrid } from './ChronicleMonthGrid'
import { daysByDate, shiftMonth } from './historyCalendar'

type CalendarMonth = { y: number; m: number }

function calendarMonthFromIso(value: string): CalendarMonth {
  const [y, m] = value.split('-').map(Number)
  return { y, m }
}

export function HistoryPage() {
  const [rows, setRows] = useState<DayListItem[]>([])
  const [applicationMonth, setApplicationMonth] = useState<CalendarMonth | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [data, health] = await Promise.all([api.listDays(500), api.health()])
      setRows(data)
      setApplicationMonth(calendarMonthFromIso(health.today))
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
      return calendarMonthFromIso(rows[0].date)
    }
    return applicationMonth
  }, [applicationMonth, rows])

  const [viewMonth, setViewMonth] = useState<CalendarMonth | null>(null)
  const visibleMonth = viewMonth ?? derivedDefaultMonth

  const byDate = useMemo(() => daysByDate(rows), [rows])

  const onPrevMonth = useCallback(() => {
    if (!visibleMonth) return
    const n = shiftMonth(visibleMonth.y, visibleMonth.m, -1)
    setViewMonth({ y: n.year, m: n.month })
  }, [visibleMonth])

  const onNextMonth = useCallback(() => {
    if (!visibleMonth) return
    const n = shiftMonth(visibleMonth.y, visibleMonth.m, 1)
    setViewMonth({ y: n.year, m: n.month })
  }, [visibleMonth])

  const onThisMonth = useCallback(() => {
    if (applicationMonth) setViewMonth(applicationMonth)
  }, [applicationMonth])

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

      {!loading && visibleMonth && (
        <ChronicleMonthGrid
          year={visibleMonth.y}
          month={visibleMonth.m}
          byDate={byDate}
          onPrevMonth={onPrevMonth}
          onNextMonth={onNextMonth}
          onThisMonth={onThisMonth}
        />
      )}
    </Layout>
  )
}
