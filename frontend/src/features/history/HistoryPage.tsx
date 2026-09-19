import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { api, type DayListItem } from '../../lib/api'
import { ChronicleMonthGrid } from './ChronicleMonthGrid'
import { TrendsPanel } from './TrendsPanel'
import { daysByDate, shiftMonth } from './historyCalendar'

type CalendarMonth = { y: number; m: number }

function calendarMonthFromIso(value: string): CalendarMonth {
  const [y, m] = value.split('-').map(Number)
  return { y, m }
}

type ChronicleView = 'calendar' | 'trends'

export function HistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const view: ChronicleView = searchParams.get('view') === 'trends' ? 'trends' : 'calendar'
  const selectView = (next: ChronicleView) =>
    setSearchParams(next === 'trends' ? { view: 'trends' } : {}, { replace: true })
  const [highlight, setHighlight] = useState<{ name: string; days: Record<string, number> } | null>(null)
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
            {view === 'trends' ? 'Trends' : 'Chronicle of focus'}
          </h1>
          <p className="max-w-xl font-body text-lg font-light leading-relaxed text-on-surface-variant">
            {view === 'trends'
              ? 'Recorded time by Task Type.'
              : 'Browse by month. Days you have opened appear in the archive; any day opens in Day.'}
          </p>
        </div>
      </section>

      <div role="tablist" aria-label="Chronicle view" className="mb-10 flex border-b border-outline-variant/30 dark:border-dark-outline-variant">
        {(['calendar', 'trends'] as const).map((option) => (
          <button
            key={option}
            type="button"
            role="tab"
            aria-selected={view === option}
            onClick={() => selectView(option)}
            className={`-mb-px flex-1 border-b-2 py-3 font-headline text-base font-light tracking-tight transition-colors sm:flex-none sm:px-10 ${view === option ? 'border-on-surface text-on-surface dark:border-dark-on-surface dark:text-dark-on-surface' : 'border-transparent text-on-surface-variant hover:text-on-surface dark:text-dark-on-surface-variant'}`}
          >
            {option === 'calendar' ? 'Calendar' : 'Trends'}
          </button>
        ))}
      </div>

      <div hidden={view !== 'trends'}>
        <TrendsPanel active={view === 'trends'} onDrill={(name, days) => {
          const latest = Object.keys(days).sort().at(-1)
          if (!latest) return
          setHighlight({ name, days })
          setViewMonth(calendarMonthFromIso(latest))
          selectView('calendar')
        }} />
      </div>
      {view === 'calendar' && <>
      {highlight && <div className="mb-4 flex items-center gap-4"><p>{highlight.name} · {Object.keys(highlight.days).length} contributing days</p><button onClick={() => setHighlight(null)}>Clear</button><button onClick={() => selectView('trends')}>Back to Trends</button></div>}
      {error && (
        <div className="mb-6 rounded-xl border border-error-container bg-error-container/20 px-4 py-3 text-sm text-on-error-container">
          {error}
        </div>
      )}

      {loading && <p className="text-on-surface-variant">Loading…</p>}

      {!loading && rows.length === 0 && !highlight && (
        <p className="mb-10 text-on-surface-variant">No days yet. Open Day to create your first day.</p>
      )}

      {!loading && visibleMonth && (
        <ChronicleMonthGrid
          year={visibleMonth.y}
          month={visibleMonth.m}
          byDate={byDate}
          highlightedDays={highlight?.days}
          highlightedType={highlight?.name}
          onPrevMonth={onPrevMonth}
          onNextMonth={onNextMonth}
          onThisMonth={onThisMonth}
        />
      )}
      </>}
    </Layout>
  )
}
