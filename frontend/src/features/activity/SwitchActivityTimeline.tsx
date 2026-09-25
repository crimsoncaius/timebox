import { useEffect, useRef, useState } from 'react'
import type { ActualBlock } from '../../lib/api'
import { blockIdentityText } from '../../lib/blockIdentity'
import type { ActivityPlan } from './activityRepository'
import './SwitchActivityTimeline.css'

const span = 180 * 60_000
const minute = 60_000
export function SwitchActivityTimeline({ records, plans, selected, now, zone, nextActivity, currentStart, enabled, onSelect }: {
  records: ActualBlock[]; plans: ActivityPlan[]; selected: number; now: number; zone: string
  nextActivity: string; currentStart: number; enabled: boolean; onSelect: (at: number | null) => void
}) {
  const [start, setStart] = useState(() => now - 150 * minute)
  const [dragging, setDragging] = useState(false)
  const [handle, setHandle] = useState(0)
  const [previousSelected, setPreviousSelected] = useState(selected)
  const frame = useRef<HTMLDivElement>(null)
  const pointer = useRef<number | null>(null)
  const latest = useRef({ start, now, onSelect, records, plans })
  useEffect(() => { latest.current = { start, now, onSelect, records, plans } })
  if (previousSelected !== selected) {
    setPreviousSelected(selected)
    if (!dragging && (selected < start || selected > start + span)) setStart(selected - span / 2)
  }
  useEffect(() => {
    if (!dragging || !enabled) return
    let id = 0, previous = performance.now()
    const tick = (time: number) => {
      const rect = frame.current?.getBoundingClientRect()
      const dt = Math.min(time - previous, 50); previous = time
      if (rect && pointer.current !== null) {
        const y = Math.max(0, Math.min(rect.height, pointer.current - rect.top)), edge = 42
        const speed = y < edge ? -(edge - y) / edge : y > rect.height - edge ? (y - rect.height + edge) / edge : 0
        const value = latest.current
        const next = Math.min(value.now - 150 * minute, value.start + Math.sign(speed) * speed * speed * dt * 8100)
        latest.current.start = next; setStart(next)
        const fraction = Math.max(14, Math.min(rect.height - 14, y)) / rect.height
        setHandle(Math.min(fraction, (value.now - next) / span))
        const raw = Math.min(value.now, Math.round((next + fraction * span) / minute) * minute)
        const boundaries = [...value.records.flatMap(r => [Date.parse(r.start_at), ...(r.end_at ? [Date.parse(r.end_at)] : [])]), ...value.plans.flatMap(p => [Date.parse(p.start_at), Date.parse(p.end_at)])].filter(at => at <= value.now)
        const nearest = boundaries.sort((a, b) => Math.abs(a - raw) - Math.abs(b - raw))[0]
        const at = nearest != null && Math.abs(nearest - raw) <= 2 * minute ? nearest : raw
        value.onSelect(at >= value.now ? null : at)
      }
      id = requestAnimationFrame(tick)
    }
    id = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(id)
  }, [dragging, enabled])
  const time = (at: number) => new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit', timeZoneName: 'shortOffset' }).format(at)
  const day = (at: number) => new Intl.DateTimeFormat(undefined, { timeZone: zone, month: 'short', day: 'numeric' }).format(at)
  const fraction = (at: number) => (at - start) / span * 100
  const affected = records.filter(r => Date.parse(r.end_at ?? new Date(now).toISOString()) > selected)
  const recorded = affected.reduce((sum, r) => sum + Math.max(0, (r.end_at ? Date.parse(r.end_at) : now) - Math.max(selected, Date.parse(r.start_at))), 0)
  const gap = Math.max(0, Math.round((now - selected - recorded) / minute))
  const block = (key: string, a: number, b: number, title: string, planned = false, fresh = false) => {
    const top = Math.max(a, start), bottom = Math.min(b, start + span)
    if (bottom <= top) return null
    return <div key={key} className={`switch-time-block ${planned ? 'switch-planned' : 'switch-actual'} ${fresh ? 'switch-new' : ''}`} style={{ top: `${fraction(top)}%`, height: `${(bottom - top) / span * 100}%` }}>
      <strong>{title}</strong>{bottom - top > 25 * minute && <small>{time(a)}–{b === now ? 'now' : time(b)}</small>}</div>
  }
  return <section aria-label="Switch time preview" className="switch-time-preview">
    <div className="switch-time-navigation"><button type="button" disabled={!enabled} onClick={() => setStart(s => s - 120 * minute)}>↑ Earlier</button><span>{day(start)}{day(start) !== day(start + span) ? ` – ${day(start + span)}` : ''}</span><button type="button" disabled={!enabled || start >= now - 150 * minute} onClick={() => setStart(s => Math.min(now - 150 * minute, s + 120 * minute))}>Later ↓</button></div>
    <div className="switch-time-headers"><span>Planned</span><span>After switch</span></div>
    <div ref={frame} className="switch-time-grid" onPointerDown={event => {
      if (!enabled) return
      event.preventDefault(); event.currentTarget.setPointerCapture(event.pointerId); pointer.current = event.clientY
      const rect = event.currentTarget.getBoundingClientRect(), fraction = Math.max(14, Math.min(rect.height - 14, event.clientY - rect.top)) / rect.height
      setHandle(Math.min(fraction, (now - start) / span)); onSelect(Math.min(now, Math.round((start + fraction * span) / minute) * minute)); setDragging(true)
    }} onPointerMove={event => { if (pointer.current !== null) pointer.current = event.clientY }} onPointerUp={() => { pointer.current = null; setDragging(false) }} onPointerCancel={() => { pointer.current = null; setDragging(false) }}>
      {Array.from({ length: 7 }, (_, index) => Math.ceil(start / (30 * minute)) * 30 * minute + index * 30 * minute).filter(at => at <= start + span).map(at => <div className="switch-time-tick" key={at} style={{ top: `${fraction(at)}%` }}><span>{new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit' }).format(at)}</span></div>)}
      {plans.map(p => block(`plan-${p.id}`, Date.parse(p.start_at), Date.parse(p.end_at), p.name || p.task_title || 'Planned activity', true))}
      {records.map(r => block(`actual-${r.id}`, Date.parse(r.start_at), Math.min(r.end_at ? Date.parse(r.end_at) : now, selected), blockIdentityText(r)))}
      {block('next', selected, now, nextActivity, false, true)}
      {(dragging || (selected >= start && selected <= start + span)) && <div role="slider" aria-label="Switch time" aria-valuemin={Math.floor(start / minute)} aria-valuemax={Math.ceil(now / minute)} aria-valuenow={Math.round(selected / minute)} aria-valuetext={`${day(selected)}, ${time(selected)}`} tabIndex={enabled ? 0 : -1} aria-disabled={!enabled} className="switch-time-line" style={{ top: `${dragging ? handle * 100 : fraction(selected)}%` }} onKeyDown={event => {
        if (!enabled || !['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) return
        event.preventDefault(); onSelect(Math.min(now, selected + (['ArrowUp', 'ArrowLeft'].includes(event.key) ? -minute : minute)))
      }}><span>↕ {new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit' }).format(selected)}</span></div>}
    </div>
    <p className="switch-time-hint">Drag the line. Hold near an edge to keep scrolling.</p>
    <p>{selected < currentStart ? `${affected.length} activities change. ${nextActivity} replaces time from ${day(selected)}, ${time(selected)} through now.` : `Previous ends and ${nextActivity} starts at ${time(selected)}.`} You can Undo.</p>
    {selected < currentStart && <details><summary>See what changes</summary><ul>{affected.map(r => <li key={r.id}>{blockIdentityText(r)}: {Date.parse(r.start_at) < selected ? `ends at ${time(selected)}` : 'replaced entirely'}{r.note && Date.parse(r.start_at) >= selected ? ` · Note also replaced: ${r.note}` : ''}</li>)}{gap > 0 && <li>{gap} minutes of unrecorded time filled.</li>}</ul></details>}
  </section>
}
