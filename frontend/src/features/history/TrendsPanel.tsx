import { useEffect, useState } from 'react'
import { api } from '../../lib/api'
import { shiftTrendRange, trendDuration, type TrendNode, type TrendsReport } from './trends'
import { errorMessage } from '../../lib/errors'

type Period = 'day' | 'week' | 'month' | 'custom'
type Drill = (name: string, days: Record<string, number>) => void
const control = 'rounded-full bg-surface-container-low px-4 py-2 text-sm text-on-surface dark:bg-dark-surface-container dark:text-dark-on-surface'

export function TrendsPanel({ active, onDrill }: { active: boolean; onDrill: Drill }) {
  const [period, setPeriod] = useState<Period>('week')
  const [anchor, setAnchor] = useState('')
  const [custom, setCustom] = useState({ start: '', end: '' })
  const [report, setReport] = useState<TrendsReport | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    if (!active) return
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
        if (current) setReport(value)
      } catch (e) {
        if (current) setError(errorMessage(e, 'Unable to load recorded time'))
      } finally { if (current) setLoading(false) }
    }
    void load()
    const timer = setInterval(() => { if (document.visibilityState !== 'hidden') void load() }, 60_000)
    const focus = () => { void load() }
    window.addEventListener('focus', focus)
    return () => { current = false; controller.abort(); clearInterval(timer); window.removeEventListener('focus', focus) }
  }, [active, period, anchor, custom.start, custom.end, retry])

  function select(next: Period) {
    if (next === period) return
    if (next === 'custom' && (!custom.start || !custom.end)) {
      if (!report) return
      setCustom({ start: report.start, end: report.end })
    }
    setReport(null)
    setPeriod(next)
  }
  function shift(step: number) {
    if (!report) return
    setAnchor(shiftTrendRange(report.start, period, step))
    setReport(null)
  }
  return <section className="max-w-2xl space-y-6 pb-10" aria-label="Recorded time by Task Type">
    <div className="flex flex-wrap gap-2" aria-label="Time range">
      {(['day', 'week', 'month', 'custom'] as const).map(option => <button key={option} type="button" aria-pressed={period === option} disabled={option === 'custom' && !report && !custom.start} onClick={() => select(option)} className={`${control} ${period === option ? 'ring-2 ring-current' : ''}`}>{option[0].toUpperCase() + option.slice(1)}</button>)}
    </div>
    {period === 'custom' ? <div className="flex flex-wrap gap-4">
      <label className="text-sm">From <input aria-label="Range start" type="date" value={custom.start} className={control} onChange={e => { const start = e.target.value; if (start) { setReport(null); setCustom({ start, end: custom.end < start ? start : custom.end }) } }} /></label>
      <label className="text-sm">To (inclusive) <input aria-label="Range end" type="date" value={custom.end} className={control} onChange={e => { const end = e.target.value; if (end) { setReport(null); setCustom({ end, start: custom.start > end ? end : custom.start }) } }} /></label>
    </div> : <div className="flex items-center justify-between gap-3">
      <button className={control} aria-label={`Previous ${period}`} disabled={!report} onClick={() => shift(-1)}>‹</button>
      <div className="text-center"><p className="text-sm tabular-nums">{report ? report.start === report.end ? report.start : `${report.start} – ${report.end}` : '…'}</p><button className="mt-1 text-sm text-on-surface-variant" onClick={() => { setAnchor(''); setReport(null); setRetry(x => x + 1) }}>{period === 'day' ? 'Today' : `This ${period}`}</button></div>
      <button className={control} aria-label={`Next ${period}`} disabled={!report} onClick={() => shift(1)}>›</button>
    </div>}
    {loading && <p role="status" className="text-sm text-on-surface-variant">Updating recorded time…</p>}
    {error && <div role="alert"><p>{error}</p><button className={control} onClick={() => setRetry(x => x + 1)}>Retry</button></div>}
    {report && <>
      <div><p className="font-headline text-5xl font-extralight tracking-tight" data-testid="trends-total">{trendDuration(report.duration_seconds)}</p><p className="mt-2 text-sm text-on-surface-variant">Recorded time · {report.timezone}</p></div>
      {report.types.length === 0 ? <p>No recorded time in this range.</p> : <>
        <h2 className="text-xs uppercase tracking-widest text-on-surface-variant">By Task Type</h2>
        <div className="space-y-5">{report.types.map(node => <TypeNode key={node.path} node={node} total={report.duration_seconds} depth={0} onDrill={onDrill} />)}</div>
      </>}
    </>}
  </section>
}

function TypeNode({ node, total, depth, onDrill }: { node: TrendNode; total: number; depth: number; onDrill: Drill }) {
  const [expanded, setExpanded] = useState(false)
  const children = [...node.children]
  if (node.direct_seconds > 0 && node.children.length) children.push({ ...node, path: `${node.path}/`, name: `Directly under ${node.name}`, duration_seconds: node.direct_seconds, days: node.direct_days, children: [] })
  children.sort((a, b) => b.duration_seconds - a.duration_seconds || a.path.localeCompare(b.path))
  const percentage = node.duration_seconds / total * 100
  return <div className="space-y-4">
    <div>
      <div className="flex items-center justify-between gap-3 py-2">
        <button style={{ paddingLeft: `${Math.min(depth, 4) * 18}px` }} className="min-w-0 flex-1 py-2 text-left" aria-expanded={node.children.length ? expanded : undefined} onClick={() => node.children.length ? setExpanded(!expanded) : onDrill(node.name, node.days)}>
          {node.children.length > 0 && <span aria-hidden>{expanded ? '▾' : '▸'} </span>}{node.name}
        </button>
        <button className="shrink-0 py-2 text-right tabular-nums" aria-label={`Show contributing days for ${node.name}`} onClick={() => onDrill(node.path.endsWith('/') ? node.name : node.path, node.days)}>
          <span className="block text-sm">{trendDuration(node.duration_seconds)}</span><span className="block text-xs text-on-surface-variant">{percentage.toFixed(1)}%</span>
        </button>
      </div>
      <div className={`${depth ? 'h-1' : 'h-1.5'} overflow-hidden rounded-full bg-surface-container-low dark:bg-dark-surface-container`}><div className={`h-full ${depth ? 'bg-outline' : 'bg-on-surface-variant'}`} style={{ width: `${percentage}%` }} /></div>
    </div>
    {expanded && children.map(child => <TypeNode key={child.path} node={child} total={total} depth={depth + 1} onDrill={onDrill} />)}
  </div>
}
