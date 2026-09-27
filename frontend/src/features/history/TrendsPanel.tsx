import { useEffect, useState } from 'react'
import { api } from '../../lib/api'
import { canAdvanceTrendRange, shiftTrendRange, trendDuration, trendRangeHeading, type TrendNode, type TrendRange, type TrendsReport } from './trends'
import { errorMessage } from '../../lib/errors'
import { CalendarDateField } from '../../components/CalendarDateField'

type Period = 'day' | 'week' | 'month' | 'custom'
type Drill = (name: string, days: Record<string, number>, range: TrendRange) => void
// A Day range always has exactly one contributing day, so it offers no drill-through.
type NodeDrill = ((name: string, days: Record<string, number>) => void) | null
const arrow = 'grid size-9 shrink-0 place-items-center rounded-full text-xl text-on-surface hover:bg-surface-container disabled:opacity-30 dark:text-dark-on-surface dark:hover:bg-dark-surface-container'
const control = 'rounded-full bg-surface-container-low px-4 py-2 text-sm text-on-surface dark:bg-dark-surface-container dark:text-dark-on-surface'

export function TrendsPanel({ active, onDrill }: { active: boolean; onDrill: Drill }) {
  const [period, setPeriod] = useState<Period>('week')
  const [anchor, setAnchor] = useState('')
  const [custom, setCustom] = useState({ start: '', end: '' })
  const [report, setReport] = useState<TrendsReport | null>(null)
  const [today, setToday] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [retry, setRetry] = useState(0)
  const customError = period !== 'custom' ? ''
    : !custom.start || !custom.end ? 'Choose a start and end date.'
    : custom.start > custom.end ? 'Start date must be on or before end date.'
    : today && (custom.start > today || custom.end > today) ? 'Choose dates on or before Today in the Reporting Time Zone.'
    : ''
  useEffect(() => {
    if (!active) return
    if (period === 'custom' && customError) { setLoading(false); setError(''); return }
    const controller = new AbortController()
    let current = true
    async function load() {
      setLoading(true)
      setError('')
      try {
        const query = new URLSearchParams({ period })
        if (anchor) query.set('anchor', anchor)
        if (period === 'custom') { query.set('start', custom.start); query.set('end', custom.end) }
        const value = await api.trends(query, controller.signal)
        if (current) { setReport(value); setToday(value.today) }
      } catch (e) {
        if (current) setError(errorMessage(e, 'Unable to load recorded time'))
      } finally { if (current) setLoading(false) }
    }
    void load()
    const timer = setInterval(() => { if (document.visibilityState !== 'hidden') void load() }, 60_000)
    const focus = () => { void load() }
    window.addEventListener('focus', focus)
    return () => { current = false; controller.abort(); clearInterval(timer); window.removeEventListener('focus', focus) }
  }, [active, period, anchor, custom.start, custom.end, customError, retry])

  // Before the report arrives, an unset anchor means the current range.
  const current = report ? report.start <= report.today && report.today <= report.end : !anchor
  const currentLabel = period === 'day' ? 'Today' : `This ${period}`

  function select(next: Period) {
    if (next === period) return
    if (next === 'custom' && (!custom.start || !custom.end)) {
      if (!report) return
      setCustom({ start: report.start, end: report.end > report.today ? report.today : report.end })
    }
    setReport(null)
    setPeriod(next)
  }
  function shift(step: number) {
    if (!report) return
    if (step > 0 && !canAdvanceTrendRange(report.start, period, report.today)) return
    setAnchor(shiftTrendRange(report.start, period, step))
    setReport(null)
  }
  return <section className="max-w-2xl space-y-6 pb-10" aria-label="Recorded time by Task Type">
    <div className="flex flex-col gap-3 border-b border-outline-variant/30 pb-4 dark:border-dark-outline-variant md:flex-row md:items-center">
      {period !== 'custom' && <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1 min-[480px]:flex-nowrap">
        {/* One width in both states so the arrows never move. Below 480px the heading takes its own line above them. */}
        <button type="button" onClick={() => { setAnchor(''); setReport(null); setRetry(x => x + 1) }} className={`inline-flex h-10 w-[10.5rem] shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-full px-4 text-sm ${current
          ? 'border border-planned-border bg-planned-surface text-on-surface dark:border-planned-dark-border dark:bg-planned-dark-surface dark:text-dark-on-surface'
          : 'bg-planned text-surface-container-lowest dark:bg-planned-dark'}`}>
          <span aria-hidden className="material-symbols-outlined text-[16px]">{current ? 'check' : 'calendar_today'}</span>
          {current ? currentLabel : `Go to ${currentLabel.toLowerCase()}`}
        </button>
        <button className={arrow} aria-label={`Previous ${period}`} disabled={!report} onClick={() => shift(-1)}>‹</button>
        <button className={arrow} aria-label={`Next ${period}`} disabled={!report || !canAdvanceTrendRange(report.start, period, report.today)} onClick={() => shift(1)}>›</button>
        <p className="order-first mb-1 min-w-0 basis-full truncate font-headline min-[480px]:order-none min-[480px]:mb-0 min-[480px]:ml-1 min-[480px]:basis-auto text-xl font-light tracking-tight tabular-nums">{report ? trendRangeHeading(period, report.start, report.end, report.today) : '…'}</p>
      </div>}
      <div role="group" aria-label="Time range" className="flex shrink-0 rounded-full bg-surface-container-low p-1 dark:bg-dark-surface-container md:ml-auto">
        {(['day', 'week', 'month', 'custom'] as const).map(option => <button key={option} type="button" aria-pressed={period === option} disabled={option === 'custom' && !report && !custom.start} onClick={() => select(option)}
          className={`flex-1 rounded-full px-2.5 py-1.5 text-sm disabled:opacity-40 ${period === option ? 'bg-surface-container-lowest text-on-surface shadow-sm dark:bg-dark-surface-container-highest dark:text-dark-on-surface' : 'text-on-surface-variant dark:text-dark-on-surface-variant'}`}>{option[0].toUpperCase() + option.slice(1)}</button>)}
      </div>
    </div>
    {period === 'custom' && <div className="flex flex-wrap gap-4">
      <label className="text-sm">From <CalendarDateField label="Range start" value={custom.start} maxIso={today || undefined} className={control} onChange={start => { setReport(null); setCustom(value => ({ ...value, start })) }} /></label>
      <label className="text-sm">To (inclusive) <CalendarDateField label="Range end" value={custom.end} maxIso={today || undefined} className={control} onChange={end => { setReport(null); setCustom(value => ({ ...value, end })) }} /></label>
    </div>}
    {customError && <p role="alert" className="text-sm text-on-error-container">{customError}</p>}
    {loading && <p role="status" className="text-sm text-on-surface-variant">Updating recorded time…</p>}
    {error && <div role="alert"><p>{error}</p><button className={control} onClick={() => setRetry(x => x + 1)}>Retry</button></div>}
    {report && <>
      <div><p className="font-headline text-5xl font-extralight tracking-tight" data-testid="trends-total">{trendDuration(report.duration_seconds)}</p><p className="mt-2 text-sm text-on-surface-variant">Recorded time · {report.timezone}</p></div>
      {report.types.length === 0 ? <p>No recorded time in this range.</p> : <>
        <h2 className="text-xs uppercase tracking-widest text-on-surface-variant">By Task Type</h2>
        <div className="space-y-5">{report.types.map(node => <TypeNode key={node.path} node={node} total={report.duration_seconds} depth={0} onDrill={period === 'day' ? null : (name, days) => onDrill(name, days, { period, start: report.start, end: report.end })} />)}</div>
      </>}
    </>}
  </section>
}

function TypeNode({ node, total, depth, onDrill }: { node: TrendNode; total: number; depth: number; onDrill: NodeDrill }) {
  const [expanded, setExpanded] = useState(false)
  const children = [...node.children]
  if (node.direct_seconds > 0 && node.children.length) children.push({ ...node, path: `${node.path}/`, name: `Directly under ${node.name}`, duration_seconds: node.direct_seconds, days: node.direct_days, children: [] })
  children.sort((a, b) => b.duration_seconds - a.duration_seconds || a.path.localeCompare(b.path))
  const percentage = node.duration_seconds / total * 100
  return <div className="space-y-4">
    <div>
      <div className="flex items-center justify-between gap-3 py-2">
        {node.children.length || onDrill ? <button style={{ paddingLeft: `${Math.min(depth, 4) * 18}px` }} className="min-w-0 flex-1 py-2 text-left" aria-expanded={node.children.length ? expanded : undefined} onClick={() => node.children.length ? setExpanded(!expanded) : onDrill?.(node.name, node.days)}>
          {node.children.length > 0 && <span aria-hidden>{expanded ? '▾' : '▸'} </span>}{node.name}
        </button> : <span style={{ paddingLeft: `${Math.min(depth, 4) * 18}px` }} className="min-w-0 flex-1 py-2">{node.name}</span>}
        {onDrill ? <button className="shrink-0 py-2 text-right tabular-nums" aria-label={`Show contributing days for ${node.name}`} onClick={() => onDrill(node.path.endsWith('/') ? node.name : node.path, node.days)}>
          <TypeTotal seconds={node.duration_seconds} percentage={percentage} />
        </button> : <div className="shrink-0 py-2 text-right tabular-nums"><TypeTotal seconds={node.duration_seconds} percentage={percentage} /></div>}
      </div>
      <div className={`${depth ? 'h-1' : 'h-1.5'} overflow-hidden rounded-full bg-surface-container-low dark:bg-dark-surface-container`}><div className={`h-full ${depth ? 'bg-outline' : 'bg-on-surface-variant'}`} style={{ width: `${percentage}%` }} /></div>
    </div>
    {expanded && children.map(child => <TypeNode key={child.path} node={child} total={total} depth={depth + 1} onDrill={onDrill} />)}
  </div>
}

function TypeTotal({ seconds, percentage }: { seconds: number; percentage: number }) {
  return <><span className="block text-sm">{trendDuration(seconds)}</span><span className="block text-xs text-on-surface-variant">{percentage.toFixed(1)}%</span></>
}
