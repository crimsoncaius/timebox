/** THROWAWAY: three replacement-confirmation variants on /day/:date?variant=A|B|C.
 * Question: how can historical replacement stay clear without interrupting a quick switch?
 * In-memory fixtures only. Reuses the real Day shell and Undo presentation.
 */
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { UndoNotice } from '../../components/UndoNotice'
import { useUndoNotice } from '../../components/useUndoNotice'
import { PrototypeSwitcher, switchPrototypeNames, type SwitchPrototypeVariant } from '../../components/PrototypeSwitcher'
import './ActivitySwitchPrototype.css'

type RecordBlock = { id: number; name: string; start: number; end: number | null; note?: string }
type Scenario = 'day' | 'overnight'
const BASE = Date.UTC(2026, 8, 25)
const asDate = (minute: number) => new Date(BASE + minute * 60_000)
const time = (minute: number) => asDate(minute).toLocaleTimeString('en-GB', { timeZone: 'UTC', hour: '2-digit', minute: '2-digit' })
const dateLabel = (minute: number) => asDate(minute).toLocaleDateString('en-GB', { timeZone: 'UTC', day: 'numeric', month: 'short' })
const stamp = (minute: number) => `${dateLabel(minute)}, ${time(minute)}`
const inputTime = (minute: number) => asDate(minute).toISOString().slice(0, 16)
const fixture = (scenario: Scenario): RecordBlock[] => scenario === 'day' ? [
  { id: 1, name: 'Read & research', start: 480, end: 600, note: 'Notes for the opening section' },
  { id: 2, name: 'Team catch-up', start: 630, end: 720 },
  { id: 3, name: 'Write proposal', start: 720, end: null, note: 'First draft' },
] : [
  { id: 1, name: 'Read & research', start: -150, end: -60, note: 'Evening notes' },
  { id: 2, name: 'Write proposal', start: -30, end: null, note: 'First draft' },
]
const replace = (records: RecordBlock[], at: number, name: string): RecordBlock[] => [
  ...records.filter(record => record.start < at).map(record => ({ ...record, end: Math.min(record.end ?? at, at) })),
  { id: Math.max(...records.map(record => record.id)) + 1, name, start: at, end: null },
]

export default function ActivitySwitchPrototype() {
  const [params] = useSearchParams()
  const variant: SwitchPrototypeVariant = params.get('variant') === 'B' ? 'B' : params.get('variant') === 'C' ? 'C' : 'A'
  // A fresh fixture for each comparison makes the variants independently repeatable.
  return <PrototypeSession key={variant} variant={variant} />
}

function PrototypeSession({ variant }: { variant: SwitchPrototypeVariant }) {
  const [scenario, setScenario] = useState<Scenario>('day')
  const [records, setRecords] = useState(() => fixture('day'))
  const [now, setNow] = useState(840)
  const [selected, setSelected] = useState(840)
  const [name, setName] = useState('Design exploration')
  const [open, setOpen] = useState(true)
  const [review, setReview] = useState(false)
  const [offline, setOffline] = useState(false)
  const [outcome, setOutcome] = useState('')
  const { notice, offer, dismiss } = useUndoNotice()
  const current = records.find(record => record.end === null)!
  const preview = replace(records, selected, name.trim() || 'New activity')
  const historical = selected < current.start
  const affected = records.filter(record => (record.end ?? now) > selected)
  const reset = (next: Scenario) => {
    const at = next === 'day' ? 840 : 75
    setScenario(next); setRecords(fixture(next)); setNow(at); setSelected(at)
    setOpen(true); setReview(false); setOutcome(''); dismiss()
  }
  const commit = () => {
    const previous = records
    setRecords(preview); setOpen(false); setReview(false); setOutcome(`Switched at ${stamp(selected)}.`)
    offer({ kind: 'recording', targetId: preview.at(-1)!.id, title: 'activity switch',
      label: `Switched to ${name.trim()}`, detail: `From ${stamp(selected)}${offline ? ' · Offline' : ''}`,
      ariaLabel: 'Activity switched', undo: async () => {
        setRecords(previous); setOutcome('Switch undone. Previous activity continues.');
      },
    })
  }
  const startSwitch = () => { setSelected(now); setReview(false); setOpen(true); setOutcome('') }
  const summary = historical
    ? `${affected.length} activities change. ${name.trim() || 'New activity'} replaces time from ${stamp(selected)} through now.`
    : `${current.name} ends at ${time(selected)}. ${name.trim() || 'New activity'} starts then.`
  const editorProps = { records, preview, now, selected, onSelect: setSelected, name, setName, historical, summary }
  const header = <div className="sp-editor-header"><div><p className="sp-eyebrow">ACTIVITY TRACKING</p><h2>{review ? 'Review this switch' : 'Switch activity'}</h2></div><button aria-label="Close switch" className="sp-icon" onClick={() => { setOpen(false); setReview(false) }}>×</button></div>
  const footer = <div className="sp-actions"><button className="sp-secondary" onClick={() => review ? setReview(false) : setOpen(false)}>{review ? 'Back' : 'Cancel'}</button>
    <button className="sp-primary" disabled={!name.trim()} onClick={() => variant === 'C' && historical && !review ? setReview(true) : commit()}>{variant === 'C' && historical && !review ? 'Review changes' : 'Switch activity'}</button></div>
  return <Layout mainClassName="sp-page px-4 sm:px-8 lg:px-12 py-6">
    <div className="sp-prototype-banner"><strong>Switching, without losing your place</strong><span>Interactive prototype · Sample data · {switchPrototypeNames[variant]}</span></div>
    <div className="sp-demo-controls"><label>Try a scenario <select aria-label="Scenario" value={scenario} onChange={e => reset(e.target.value as Scenario)}><option value="day">Long activity + earlier gap</option><option value="overnight">Across midnight</option></select></label>
      <label><input type="checkbox" checked={offline} onChange={e => setOffline(e.target.checked)} /> Simulate offline</label><button onClick={() => reset(scenario)}>Reset</button><button onClick={() => setNow(value => value + 1)}>Advance 1 minute</button></div>
    <div className="sp-day-title"><div><p className="sp-eyebrow">DAY</p><h1>Friday, 25 September</h1></div><span>{time(now)} · Singapore</span></div>
    <div className="sp-tracking"><div><span className="sp-live-dot" /><strong>{current.name}</strong><span>since {stamp(current.start)}{offline ? ' · Offline' : ''}</span></div><button className="sp-secondary" onClick={startSwitch}>Switch</button></div>
    {outcome && <p role="status" className="sp-outcome">{outcome}</p>}
    <div className={`sp-workspace ${open && variant === 'B' ? 'sp-wide-editor' : ''}`}>
      <section className="sp-day-context"><div className="sp-section-heading"><h2>Your day</h2><span>Planned & Actual</span></div><DayContext records={records} now={now} scenario={scenario} />
        <p className="sp-muted">Recording continues while you choose.</p></section>
      {open && <section className={`sp-editor sp-variant-${variant}`} aria-label="Switch activity editor">
        {header}
        {variant === 'A' && <VariantA {...editorProps} />}
        {variant === 'B' && <VariantB {...editorProps} />}
        {variant === 'C' && <VariantC {...editorProps} review={review} />}
        {footer}
      </section>}
    </div>
    <details className="sp-state"><summary>Prototype state · {open ? review ? 'reviewing' : 'choosing' : 'recording'} · Undo {notice ? 'available' : 'unavailable'}</summary>
      <p>Changes are in memory. Reload or switch variants to reset. Offline is simulated; no server synchronization is exercised.</p>
      <pre>{JSON.stringify({ variant, scenario, now: stamp(now), selected: stamp(selected), offline, current: current.name, records, preview: open ? preview : null, undoAvailable: Boolean(notice) }, null, 2)}</pre></details>
    <PrototypeSwitcher variant={variant} />
    {notice && <div className="sp-undo"><UndoNotice key={notice.id} notice={notice} onDismiss={dismiss} onFailure={setOutcome} /></div>}
  </Layout>
}

type EditorProps = {
  records: RecordBlock[]; preview: RecordBlock[]; now: number; selected: number; onSelect: (at: number) => void
  name: string; setName: (value: string) => void; historical: boolean; summary: string
}
function ChoiceFields({ name, setName, selected, onSelect, now }: EditorProps) {
  return <div className="sp-fields"><label>Next activity<input aria-label="Next activity" value={name} onChange={e => setName(e.target.value)} /></label>
    <div className="sp-time-row"><label>Starts at<input aria-label="Starts at" type="datetime-local" value={inputTime(selected)} max={inputTime(now)} onChange={e => { const value = (Date.parse(`${e.target.value}:00Z`) - BASE) / 60_000; if (Number.isFinite(value)) onSelect(Math.min(now, value)) }} /></label><button className="sp-secondary" onClick={() => onSelect(now)}>Now</button></div>
  </div>
}
function VariantA(props: EditorProps) {
  return <><ChoiceFields {...props} /><Timeline {...props} mode="after" />
    <div className={`sp-inline-impact ${props.historical ? 'sp-historical' : ''}`}><span aria-hidden>{props.historical ? '↶' : '↳'}</span><p>{props.summary} <span className="sp-muted">You can Undo.</span></p></div>
    {props.historical && <details className="sp-impact-details"><summary>See what changes</summary><ImpactList {...props} /></details>}</>
}
function VariantB(props: EditorProps) {
  return <><ChoiceFields {...props} /><div className="sp-comparison-caption"><h3>See the change as you drag</h3><span>Recorded → After switch</span></div>
    <Timeline {...props} mode="compare" /><ImpactList {...props} /><p className="sp-muted">One switch. Undo restores the original timeline.</p></>
}
function VariantC(props: EditorProps & { review: boolean }) {
  return props.review ? <div className="sp-review"><p className="sp-review-lead">Start <strong>{props.name}</strong><br />from <strong>{stamp(props.selected)}</strong>?</p>
    <p>These changes happen together:</p><ImpactList {...props} /><p className="sp-muted">Earlier time stays as recorded. Undo restores the whole switch.</p></div>
    : <><ChoiceFields {...props} /><Timeline {...props} mode="before" /><p className="sp-muted">{props.historical ? 'This reaches earlier activities. Review the changes before switching.' : props.summary}</p></>
}
function ImpactList({ records, now, selected, name }: EditorProps) {
  const affected = records.filter(record => (record.end ?? now) > selected)
  const recorded = affected.reduce((sum, record) => sum + Math.max(0, (record.end ?? now) - Math.max(selected, record.start)), 0)
  const gap = Math.max(0, now - selected - recorded)
  return <ul className="sp-impact-list">{affected.map(record => <li key={record.id}><strong>{record.name}</strong><span>{record.start < selected ? `Ends ${time(selected)} instead of ${record.end === null ? 'continuing' : time(record.end)}` : 'Replaced entirely'}</span>{record.note && record.start >= selected && <small>Note also replaced: “{record.note}”</small>}</li>)}
    {gap > 0 && <li><strong>Unrecorded time</strong><span>{gap} minutes filled</span></li>}
    <li className="sp-added"><strong>{name || 'New activity'}</strong><span>{stamp(selected)} → continuing</span></li></ul>
}

function Timeline({ records, preview, now, selected, onSelect, mode }: EditorProps & { mode: 'after' | 'before' | 'compare' }) {
  const [start, setStart] = useState(now - 150)
  const [dragging, setDragging] = useState(false)
  const frame = useRef<HTMLDivElement>(null)
  const dragY = useRef<number | null>(null)
  const live = useRef({ start, now, onSelect, records })
  useEffect(() => { live.current = { start, now, onSelect, records } }, [start, now, onSelect, records])
  useEffect(() => {
    if (dragging) return
    if (selected < start || selected > start + 180) setStart(selected - 90)
  }, [selected, dragging]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!dragging) return
    let id = 0; let previous = performance.now()
    const tick = (timestamp: number) => {
      const rect = frame.current?.getBoundingClientRect()
      const delta = Math.min(timestamp - previous, 50); previous = timestamp
      if (rect && dragY.current !== null) {
        const y = Math.max(0, Math.min(rect.height, dragY.current - rect.top))
        const edge = 42
        const speed = y < edge ? -(edge - y) / edge : y > rect.height - edge ? (y - rect.height + edge) / edge : 0
        const value = live.current
        const nextStart = Math.min(value.now - 150, value.start + speed * delta * 0.045)
        live.current.start = nextStart
        if (speed !== 0) setStart(nextStart)
        const raw = Math.min(value.now, Math.round(nextStart + y / rect.height * 180))
        const boundary = value.records.flatMap(record => [record.start, record.end]).find(at => at !== null && Math.abs(at - raw) <= 2)
        value.onSelect(Math.min(value.now, boundary ?? raw))
      }
      id = requestAnimationFrame(tick)
    }
    id = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(id)
  }, [dragging])
  const end = start + 180
  const fraction = (at: number) => (at - start) / 180 * 100
  const block = (record: RecordBlock, lane: 'left' | 'right', newBlock: boolean): ReactNode => {
    const a = Math.max(start, record.start); const b = Math.min(end, record.end ?? now)
    if (b <= a) return null
    return <div key={`${lane}-${record.id}`} className={`sp-time-block sp-${lane} ${newBlock ? 'sp-new-block' : ''}`} style={{ top: `${fraction(a)}%`, height: `${(b - a) / 180 * 100}%` }}>
      <strong>{record.name}</strong>{b - a >= 20 && <span>{time(record.start)}–{record.end === null ? 'now' : time(record.end)}</span>}
    </div>
  }
  return <div className="sp-timeline-wrap">
    <div className="sp-timeline-nav"><button aria-label="Earlier time" onClick={() => setStart(value => value - 120)}>↑ Earlier</button><strong>{dateLabel(start)}{dateLabel(start) !== dateLabel(end) ? ` → ${dateLabel(end)}` : ''}</strong><button aria-label="Later time" disabled={start >= now - 150} onClick={() => setStart(value => Math.min(now - 150, value + 120))}>Later ↓</button></div>
    <div className="sp-lane-labels"><span>{mode === 'compare' ? 'RECORDED' : 'PLANNED'}</span><span>{mode === 'before' ? 'RECORDED' : 'AFTER SWITCH'}</span></div>
    <div ref={frame} className={`sp-timeline ${dragging ? 'sp-dragging' : ''}`} data-testid="switch-timeline" onWheel={event => setStart(value => Math.min(now - 150, value + event.deltaY / 7))}
      onPointerDown={event => { event.preventDefault(); event.currentTarget.setPointerCapture(event.pointerId); dragY.current = event.clientY; const rect = event.currentTarget.getBoundingClientRect(); onSelect(Math.min(now, Math.round(start + (event.clientY - rect.top) / rect.height * 180))); setDragging(true) }}
      onPointerMove={event => { if (dragY.current !== null) dragY.current = event.clientY }}
      onPointerUp={() => { dragY.current = null; setDragging(false) }} onPointerCancel={() => { dragY.current = null; setDragging(false) }}>
      {Array.from({ length: 7 }, (_, i) => Math.ceil(start / 30) * 30 + i * 30).filter(at => at <= end).map(at => <div key={at} className={`sp-tick ${at % 1440 === 0 ? 'sp-midnight' : ''}`} style={{ top: `${fraction(at)}%` }}><span>{at % 1440 === 0 ? dateLabel(at) : time(at)}</span></div>)}
      {mode === 'compare' ? records.map(record => block(record, 'left', false)) : <div className="sp-plan-block" style={{ top: `${Math.max(0, fraction(now - 120))}%`, bottom: `${Math.max(0, 100 - fraction(now))}%` }}><strong>Design exploration</strong><span>Planned</span></div>}
      {(mode === 'before' ? records : preview).map(record => block(record, 'right', mode !== 'before' && record.id === preview.at(-1)!.id))}
      {now >= start && now <= end && <div className="sp-now-line" style={{ top: `${fraction(now)}%` }}><span>NOW</span></div>}
      {selected >= start && selected <= end && <div className="sp-selection" role="slider" aria-label="Switch time" aria-valuemin={Math.floor(start)} aria-valuemax={now} aria-valuenow={selected} aria-valuetext={stamp(selected)} tabIndex={0}
        onKeyDown={event => { if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) { event.preventDefault(); event.stopPropagation(); onSelect(Math.min(now, selected + (event.key === 'ArrowUp' || event.key === 'ArrowLeft' ? -1 : 1))) } }} style={{ top: `${fraction(selected)}%` }}><span>↕ {time(selected)}</span></div>}
    </div>
    <p className="sp-drag-hint">Drag the white line. Hold near an edge to keep scrolling.</p>
  </div>
}

function DayContext({ records, now, scenario }: { records: RecordBlock[]; now: number; scenario: Scenario }) {
  const start = scenario === 'day' ? 480 : -180
  const end = scenario === 'day' ? 960 : 120
  const position = (at: number) => (at - start) / (end - start) * 100
  return <><div className="sp-context-labels"><span>PLANNED</span><span>ACTUAL</span></div><div className="sp-day-timeline">
    {Array.from({ length: Math.ceil((end - start) / 60) }, (_, i) => start + i * 60).map(at => <div className="sp-tick" key={at} style={{ top: `${position(at)}%` }}><span>{time(at)}</span></div>)}
    <div className="sp-time-block sp-left sp-planned" style={{ top: `${position(start + 30)}%`, height: '23%' }}><strong>Read & research</strong><span>Reading</span></div>
    <div className="sp-time-block sp-left sp-planned" style={{ top: '53%', height: '30%' }}><strong>Design exploration</strong><span>Creative work</span></div>
    {records.filter(record => (record.end ?? now) > start).map(record => <div className="sp-time-block sp-right" key={record.id} style={{ top: `${Math.max(0, position(record.start))}%`, height: `${(Math.min(end, record.end ?? now) - Math.max(start, record.start)) / (end - start) * 100}%` }}><strong>{record.name}</strong><span>{time(record.start)}–{record.end === null ? 'now' : time(record.end)}</span></div>)}
    <div className="sp-now-line" style={{ top: `${position(now)}%` }}><span>NOW</span></div>
  </div></>
}
