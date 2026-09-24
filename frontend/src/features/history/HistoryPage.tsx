import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { api, type ChronicleDay } from '../../lib/api'
import { ChronicleMonthGrid } from './ChronicleMonthGrid'
import { TrendsPanel } from './TrendsPanel'
import { daysByDate, shiftMonth } from './historyCalendar'
import { errorMessage } from '../../lib/errors'

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
  const [rows, setRows] = useState<ChronicleDay[]>([])
  const [applicationMonth, setApplicationMonth] = useState<CalendarMonth | null>(null)
  const [viewMonth, setViewMonth] = useState<CalendarMonth | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    const month = viewMonth ? `${viewMonth.y}-${String(viewMonth.m).padStart(2, '0')}` : undefined
    void api.chronicleMonth(month).then((data) => {
      if (!active) return
      setRows(data.days)
      setApplicationMonth(calendarMonthFromIso(data.today))
      setError(null)
      setLoading(false)
    }).catch((cause) => {
      if (!active) return
      setError(errorMessage(cause, 'Failed to load Chronicle'))
      setLoading(false)
    })
    return () => { active = false }
  }, [viewMonth])

  const visibleMonth = viewMonth ?? applicationMonth

  const byDate = useMemo(() => daysByDate(rows), [rows])

  const showMonth = useCallback((month: CalendarMonth | null) => {
    setLoading(true)
    setError(null)
    setViewMonth(month)
  }, [])

  const onPrevMonth = useCallback(() => {
    if (!visibleMonth) return
    const n = shiftMonth(visibleMonth.y, visibleMonth.m, -1)
    showMonth({ y: n.year, m: n.month })
  }, [visibleMonth, showMonth])

  const onNextMonth = useCallback(() => {
    if (!visibleMonth || !applicationMonth || visibleMonth.y > applicationMonth.y || (visibleMonth.y === applicationMonth.y && visibleMonth.m >= applicationMonth.m)) return
    const n = shiftMonth(visibleMonth.y, visibleMonth.m, 1)
    showMonth({ y: n.year, m: n.month })
  }, [visibleMonth, applicationMonth, showMonth])

  const onThisMonth = useCallback(() => {
    if (viewMonth) showMonth(null)
  }, [viewMonth, showMonth])

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
              : 'Browse planned, recorded, and completed days through today. Any date opens in Day.'}
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
          showMonth(calendarMonthFromIso(latest))
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
        <p className="mb-10 text-on-surface-variant">No planned, recorded, or completed days this month.</p>
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
          canNextMonth={applicationMonth != null && (visibleMonth.y < applicationMonth.y || (visibleMonth.y === applicationMonth.y && visibleMonth.m < applicationMonth.m))}
          onThisMonth={onThisMonth}
        />
      )}
      </>}
    </Layout>
  )
}
