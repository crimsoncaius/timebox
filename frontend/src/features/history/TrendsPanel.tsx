import { useEffect, useState } from 'react'
import { api } from '../../lib/api'
import { canAdvanceTrendRange, dayCountLabel, shiftTrendRange, showsCurrentTrendRange, trendDuration, trendRangeDayCount, trendRangeHeading, type TrendNode, type TrendRange, type TrendsReport } from './trends'
import { errorMessage } from '../../lib/errors'
import { CustomRangePicker } from './CustomRangePicker'

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

  const current = showsCurrentTrendRange(period, anchor, today)
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
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1 min-[480px]:flex-nowrap">
        {/* One width in both states so the arrows never move. Below 480px the heading takes its own line above them. */}
        {period === 'custom' ? <CustomRangePicker start={custom.start} end={custom.end} today={today}
          onChange={(start, end) => { setReport(null); setCustom({ start, end }) }} /> : <>
          <button type="button" onClick={() => { setAnchor(''); setReport(null); setRetry(x => x + 1) }} className={`inline-flex h-10 w-[10.5rem] shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-full px-4 text-sm ${current
            ? 'border border-planned-border bg-planned-surface text-on-surface dark:border-planned-dark-border dark:bg-planned-dark-surface dark:text-dark-on-surface'
            : 'bg-planned text-surface-container-lowest dark:bg-planned-dark'}`}>
            <span aria-hidden className="material-symbols-outlined text-[16px]">{current ? 'check' : 'calendar_today'}</span>
            {current ? currentLabel : `Go to ${currentLabel.toLowerCase()}`}
          </button>
          <button className={arrow} aria-label={`Previous ${period}`} disabled={!report} onClick={() => shift(-1)}>‹</button>
          <button className={arrow} aria-label={`Next ${period}`} disabled={!report || !canAdvanceTrendRange(report.start, period, report.today)} onClick={() => shift(1)}>›</button>
        </>}
        <p className={`order-first mb-1 min-w-0 basis-full truncate font-headline min-[480px]:order-none min-[480px]:mb-0 min-[480px]:basis-auto text-xl font-light tracking-tight tabular-nums ${period === 'custom' ? 'min-[480px]:ml-3' : 'min-[480px]:ml-1'}`}>
          {period === 'custom'
            ? <>{trendRangeHeading(period, custom.start, custom.end, today)}<span className="ml-2 font-body text-sm tracking-normal text-on-surface-variant dark:text-dark-on-surface-variant">· {dayCountLabel(trendRangeDayCount({ period, ...custom }))}</span></>
            : report ? trendRangeHeading(period, report.start, report.end, report.today) : '…'}
        </p>
      </div>
      <div role="group" aria-label="Time range" className="flex shrink-0 rounded-full bg-surface-container-low p-1 dark:bg-dark-surface-container md:ml-auto">
        {(['day', 'week', 'month', 'custom'] as const).map(option => <button key={option} type="button" aria-pressed={period === option} disabled={option === 'custom' && !report && !custom.start} onClick={() => select(option)}
          className={`flex-1 rounded-full px-2.5 py-1.5 text-sm disabled:opacity-40 ${period === option ? 'bg-surface-container-lowest text-on-surface shadow-sm dark:bg-dark-surface-container-highest dark:text-dark-on-surface' : 'text-on-surface-variant dark:text-dark-on-surface-variant'}`}>{option[0].toUpperCase() + option.slice(1)}</button>)}
      </div>
    </div>
    {customError && <p role="alert" className="text-sm text-on-error-container">{customError}</p>}
    {loading && <p role="status" className="text-sm text-on-surface-variant">Updating recorded time…</p>}
    {error && <div role="alert"><p>{error}</p><button className={control} onClick={() => setRetry(x => x + 1)}>Retry</button></div>}
    {report && <>
      <div><p className="font-headline text-5xl font-extralight tracking-tight" data-testid="trends-total">{trendDuration(report.duration_seconds)}</p><p className="mt-2 text-sm text-on-surface-variant">Recorded time · {report.timezone}</p></div>
      {report.types.length === 0 ? <p>No recorded time in this range.</p> : <>
        <div>
          <div className={`flex items-center pb-2 text-xs ${muted}`}>
            <h2 className="flex-1">Task Type</h2><span className={shareColumn}>Share</span><span className={`${timeColumn} text-right`}>Time</span><span className={`${percentColumn} text-right`}>%</span>
          </div>
          <div className="divide-y divide-surface-container-low border-y border-surface-container-low dark:divide-dark-surface-container dark:border-dark-surface-container">
            {report.types.map(node => <div key={node.path}><TypeNode node={node} total={report.duration_seconds} depth={0} onDrill={period === 'day' ? null : (name, days) => onDrill(name, days, { period, start: report.start, end: report.end })} /></div>)}
          </div>
        </div>
      </>}
    </>}
  </section>
}

const muted = 'text-on-surface-variant dark:text-dark-on-surface-variant'
const shareColumn = 'w-14 shrink-0 pr-2'
const timeColumn = 'w-20 shrink-0'
const percentColumn = 'w-16 shrink-0'

// One ledger row: tree guides and a chevron slot for depth, then fixed Share, Time and % columns.
// Every bar shares the range-total scale; top-level rows are taller and heavier than their children.
function TypeNode({ node, total, depth, onDrill }: { node: TrendNode; total: number; depth: number; onDrill: NodeDrill }) {
  const [expanded, setExpanded] = useState(false)
  const direct = node.path.endsWith('/')
  const children = [...node.children]
  if (node.direct_seconds > 0 && node.children.length) children.push({ ...node, path: `${node.path}/`, name: `Directly under ${node.name}`, duration_seconds: node.direct_seconds, days: node.direct_days, children: [] })
  children.sort((a, b) => b.duration_seconds - a.duration_seconds || a.path.localeCompare(b.path))
  const percentage = node.duration_seconds / total * 100
  const top = depth === 0
  const label = <>
    {Array.from({ length: Math.min(depth, 4) }, (_, i) => <span key={i} aria-hidden className="flex w-[18px] shrink-0 justify-center self-stretch"><span className="w-px bg-outline-variant dark:bg-dark-outline-variant" /></span>)}
    <span aria-hidden className={`material-symbols-outlined w-[22px] shrink-0 text-[18px] ${muted}`}>{node.children.length ? expanded ? 'expand_more' : 'chevron_right' : ''}</span>
    <span className={`min-w-0 flex-1 truncate ${top ? 'text-[15px] font-medium' : 'text-sm'} ${direct ? `italic ${muted}` : ''}`}>{direct ? '(direct)' : node.name}</span>
    <span aria-hidden className={shareColumn}><span className={`block ${top ? 'h-2' : 'h-[5px]'} overflow-hidden rounded-full bg-surface-container-low dark:bg-dark-surface-container`}><span className={`block h-full ${top ? 'bg-on-surface dark:bg-dark-on-surface' : 'bg-on-surface-variant dark:bg-dark-on-surface-variant'}`} style={{ width: `${percentage}%` }} /></span></span>
  </>
  const totals = <>
    <span className={`${timeColumn} text-right ${top ? 'text-sm font-medium' : 'text-[13px]'}`}>{trendDuration(node.duration_seconds)}</span>
    <span className={`${percentColumn} text-right text-xs ${muted}`}>{percentage.toFixed(1)}%</span>
  </>
  return <>
    <div className={`flex items-stretch ${top ? 'min-h-[3.25rem]' : 'min-h-10'}`}>
      {node.children.length || onDrill ? <button className="flex min-w-0 flex-1 items-center text-left" aria-label={direct ? node.name : undefined} aria-expanded={node.children.length ? expanded : undefined} onClick={() => node.children.length ? setExpanded(!expanded) : onDrill?.(node.name, node.days)}>
        {label}
      </button> : <div className="flex min-w-0 flex-1 items-center">{label}</div>}
      {onDrill ? <button className="flex shrink-0 items-center tabular-nums" aria-label={`Show contributing days for ${node.name}`} onClick={() => onDrill(direct ? node.name : node.path, node.days)}>
        {totals}
      </button> : <div className="flex shrink-0 items-center tabular-nums">{totals}</div>}
    </div>
    {expanded && children.map(child => <TypeNode key={child.path} node={child} total={total} depth={depth + 1} onDrill={onDrill} />)}
  </>
}
