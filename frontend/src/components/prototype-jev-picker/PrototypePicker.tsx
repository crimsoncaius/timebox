import { useEffect, useId, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { TaskType } from '../../lib/api'
import { buildTaskTypeSuggestions, formatTaskTypePathParts } from '../../lib/taskTypePaths'

type Props = {
  label: string; taskTypes: TaskType[]; valueTaskTypeId: number | null
  onSelectTaskTypeId: (id: number | null) => void
  onCreateTaskTypePath: (path: string) => Promise<TaskType>
}
type Prediction = { query: string; path: string | null; status: string }

function simulate(query: string): Pick<Prediction, 'path' | 'status'> {
  if (/offline|timeout/i.test(query)) return { path: null, status: 'Service unavailable · silently omitted' }
  if (/maybe/i.test(query)) return { path: null, status: 'Confidence 0.62 · below 0.80 threshold' }
  const path = /piano|music|guitar|scales/i.test(query) ? 'learning/music'
    : /read|book|chapter/i.test(query) ? 'learning/reading'
    : /run|gym|exercise|jog/i.test(query) ? 'health/exercise'
    : /budget|tax|expense/i.test(query) ? 'admin/finances'
    : /plan|schedule/i.test(query) ? 'work/planning' : null
  return { path, status: path ? 'Confidence 0.96 · eligible' : 'No confident match' }
}

export function PrototypePicker({ label, taskTypes, valueTaskTypeId, onSelectTaskTypeId, onCreateTaskTypePath }: Props) {
  const [params] = useSearchParams()
  const variant = params.get('variant') ?? 'A'
  const selected = taskTypes.find(type => type.id === valueTaskTypeId)
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(true)
  const [edited, setEdited] = useState(false)
  const [prediction, setPrediction] = useState<Prediction | null>(null)
  const [dismissed, setDismissed] = useState<string | null>(null)
  const [tab, setTab] = useState('path')
  const id = useId()
  useEffect(() => {
    if (!edited || !open || !query.trim()) return
    const timer = setTimeout(() => setPrediction({ query, ...simulate(query) }), 500)
    return () => clearTimeout(timer)
  }, [query, edited, open])
  const result = edited && open && prediction?.query === query && dismissed !== query ? taskTypes.find(type => type.name === prediction.path && type.id !== valueTaskTypeId) : null
  const { rows, createPath } = buildTaskTypeSuggestions(taskTypes, query, valueTaskTypeId)
  const status = !edited || !query.trim() ? 'Idle · enter text to predict' : prediction?.query === query ? prediction.status : 'Waiting for 500 ms pause'
  const edit = (value: string) => { setQuery(value); setEdited(true); setOpen(true); setPrediction(null); setDismissed(null) }
  const choose = (type: TaskType | null) => { onSelectTaskTypeId(type?.id ?? null); setOpen(false); setEdited(false); setQuery(''); setPrediction(null) }
  const dismiss = () => setDismissed(query)
  const path = (type: TaskType) => {
    const parts = formatTaskTypePathParts(type.name)
    return <span>{parts.ancestorsLabel && <span className="proto-ancestor">{parts.ancestorsLabel} / </span>}<strong>{parts.leafLabel}</strong></span>
  }
  const row = (type: TaskType, semantic = false) => <button key={type.id} className="proto-row" onClick={() => choose(type)}>
    {path(type)}{semantic ? <span className="proto-badge">✦ By meaning</span> : type.id === valueTaskTypeId ? <span className="proto-muted">Current</span> : null}
  </button>
  const manual = <>
    {rows.map(type => row(type))}
    {!rows.length && <p className="proto-empty">No matching paths</p>}
  </>
  const create = <div className="proto-create">
    {createPath ? <button onClick={async () => choose(await onCreateTaskTypePath(createPath))}>＋ Create “{createPath}”</button>
      : !query.trim() ? <button onClick={() => choose(null)}>Unset</button> : null}
  </div>
  const suggestion = result ? <div className="proto-suggestion">
    <div className="proto-eyebrow">✦ Suggested Task Type <button aria-label="Dismiss suggestion" onClick={dismiss}>×</button></div>
    <div className="proto-suggestion-body">{path(result)}<button className="proto-use" onClick={() => choose(result)}>Use</button></div>
  </div> : null

  return <div className="proto-picker" onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); setOpen(false); setPrediction(null); setEdited(false) } }}>
    <label htmlFor={id}>{label}</label>
    <button className="proto-current" onClick={() => { setOpen(!open); setEdited(false); setPrediction(null); setQuery('') }} aria-expanded={open}>
      <span>{selected ? path(selected) : 'Unset'}</span><span>⌄</span>
    </button>
    {open && <section className="proto-popover" aria-label="Choose Task Type">
      <input id={id} aria-label="Search Task Types" placeholder="Search paths or describe an activity" value={query} onChange={event => edit(event.target.value)} autoComplete="off" />
      {variant === 'A' && <>
        {suggestion}
        <div className="proto-eyebrow proto-section-label">{query ? 'Matching paths' : 'Your Task Types'}</div>
        <div className="proto-results">{manual}</div>{create}
      </>}
      {variant === 'B' && <>
        <div className="proto-results">
          {result && row(result, true)}
          {rows.filter(type => type.id !== result?.id).map(type => row(type))}
          {!rows.length && !result && <p className="proto-empty">No matching Task Types</p>}
        </div>
        {result && <button className="proto-dismiss" onClick={dismiss}>Hide prediction</button>}{create}
      </>}
      {variant === 'C' && <>
        <div className="proto-tabs" role="tablist" aria-label="Match method">
          <button role="tab" aria-selected={tab === 'path'} onClick={() => setTab('path')}>By path <span>{rows.length}</span></button>
          <button role="tab" aria-selected={tab === 'meaning'} onClick={() => setTab('meaning')}>✦ By meaning {result && <span className="proto-dot" />}</button>
        </div>
        <div className="proto-results" role="tabpanel">
          {tab === 'path' ? manual : result ? <div className="proto-meaning"><span className="proto-meaning-icon">✦</span><p>For “{query}”</p>{path(result)}<button className="proto-use" onClick={() => choose(result)}>Use this Task Type</button><button className="proto-dismiss" onClick={dismiss}>Dismiss</button></div>
            : <p className="proto-empty">{query.trim() ? 'No suggestion to show. You can still choose by path.' : 'Describe an activity to find a Task Type.'}</p>}
        </div>{create}
      </>}
    </section>}
    <details className="proto-lab" open>
      <summary>Prototype controls & state</summary>
      <div className="proto-examples">{['practice piano', 'music', 'run', 'maybe', 'offline'].map(example => <button key={example} onClick={() => edit(example)}>{example}</button>)}</div>
      <p><b>Variant:</b> {variant} · <b>Current:</b> {selected?.name ?? 'Unset'}</p>
      <p><b>Query:</b> {query || '(empty)'} · <b>Picker:</b> {open ? 'open' : 'closed'}</p>
      <p><b>Prediction:</b> {dismissed === query ? 'Dismissed until query changes' : status}</p>
      <p><b>Result:</b> {result?.name ?? 'none'} · <b>Input:</b> picker text only</p>
      <p>Fixture predictions only. Changes stay in memory; reload to reset.</p>
    </details>
  </div>
}
