import { blockPrimaryIdentity, blockSecondaryIdentity, type IdentifiableBlock } from '../../lib/blockIdentity'
import { useEffect, useRef, useState } from 'react'
import type { PlannedRecordingResult } from '../../lib/api'
import { recordingTimeline, type RecordingPiece } from './recordingTimeline'
import './RecordingPreview.css'

export function RecordingPreview({ preview, timezone, onConfirm, onCancel, error, plannedBlock }: {
  preview: PlannedRecordingResult; timezone: string; onConfirm: () => Promise<void>; onCancel: () => void; error?: string | null; plannedBlock?: IdentifiableBlock
}) {
  const [busy, setBusy] = useState(false)
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { const element = dialog.current; element?.showModal(); return () => element?.close() }, [])
  const replacement = { ...plannedBlock, name: preview.replacement.name }
  const model = recordingTimeline(preview, replacement)
  const exact = (value: string | number) => new Date(value).toLocaleString(undefined, { timeZone: timezone, timeZoneName: 'shortOffset' })
  const clock = (value: number) => new Date(value).toLocaleTimeString(undefined, { timeZone: timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
  const date = (value: number) => new Date(value).toLocaleDateString(undefined, { timeZone: timezone, month: 'short', day: 'numeric' })
  const range = model.start % 60_000 === 0 && model.end % 60_000 === 0 && date(model.start) === date(model.end)
    ? `${clock(model.start)} – ${clock(model.end)}` : `${exact(model.start)} – ${exact(model.end)}`
  const changed = preview.conflicts.filter(row => row.name !== preview.replacement.name || row.note !== preview.replacement.note)
  const label = preview.conflicts.length ? 'Replace overlapping time' : 'Record Actual'
  return <dialog ref={dialog} aria-label={label} onCancel={event => { event.preventDefault(); if (!busy) onCancel() }} onKeyDown={event => {
    if (event.key !== 'Tab' || event.ctrlKey || event.altKey || event.metaKey) return
    const controls = [...event.currentTarget.querySelectorAll<HTMLElement>('button:not([disabled]), summary')]
      .filter(element => element.getClientRects().length > 0)
    const first = controls[0], last = controls[controls.length - 1]
    if (event.shiftKey && document.activeElement === first && last) { event.preventDefault(); last.focus() }
    else if (!event.shiftKey && document.activeElement === last && first) { event.preventDefault(); first.focus() }
  }} className="recording-preview">
    <section className="recording-preview__body">
      <p className="recording-preview__kicker">Record Actual</p>
      <h2>{label}</h2>
      <p className="recording-preview__muted">{date(model.start)} · {timezone}</p>
      {error && <p role="alert" className="recording-preview__error">{error}</p>}
      {preview.stale && <p role="alert" className="recording-preview__error">Records changed. Review this updated preview before confirming again.</p>}
      <div className="recording-preview__new">
        <p className="recording-preview__range">{range}</p>
        <p className="recording-preview__muted">{Math.floor((model.end - model.start) / 60_000)} min {Math.floor((model.end - model.start) / 1000) % 60} sec</p>
        <p className="font-medium">{blockPrimaryIdentity(replacement)}</p>
        <p className="recording-preview__muted">{blockSecondaryIdentity(replacement)}</p>
        <p className="recording-preview__muted">From your Planned Block</p>
      </div>
      {model.before.length ? <>
        <RecordingDiagram model={model} clock={clock} />
        <p className="recording-preview__muted">Only overlapping time is replaced. Time outside this range keeps its original details.</p>
        <ul className="recording-preview__changes">{preview.conflicts.map(row => <li key={row.id}>
          <p className="font-medium">{blockPrimaryIdentity(row)}</p>
          {blockSecondaryIdentity(row) && <p className="recording-preview__muted">{blockSecondaryIdentity(row)}</p>}
          <p className="recording-preview__error">Replace {exact(Math.max(Date.parse(row.start_at), model.start))} – {exact(Math.min(row.end_at ? Date.parse(row.end_at) : model.end, model.end))}</p>
          {Date.parse(row.start_at) < model.start && <p>Keep {exact(row.start_at)} – {exact(model.start)}.</p>}
          {row.end_at && Date.parse(row.end_at) > model.end && <p>Keep {exact(model.end)} – {exact(row.end_at)}.</p>}
          {!row.end_at && <p className="recording-preview__tracking">Keep tracking from {exact(model.end)}. Tracking stays on.</p>}
        </li>)}</ul>
      </> : <p>No Actual time overlaps this updated range.</p>}
      {changed.length > 0 && <details key={preview.fingerprint} className="recording-preview__details">
        <summary>Name or note will change for {changed.length} overlapping {changed.length === 1 ? 'block' : 'blocks'}.<span>See name and note changes</span></summary>
        {changed.map(row => <div key={row.id}>
          <p className="font-medium">Existing: {blockPrimaryIdentity(row)}</p>
          <p className="recording-preview__muted">{exact(row.start_at)} – {row.end_at ? exact(row.end_at) : 'Running'}</p>
          <p className="whitespace-pre-wrap">{row.note || 'No note'}</p>
        </div>)}
        <p className="font-medium">After: {blockPrimaryIdentity(replacement)}</p>
        <p className="whitespace-pre-wrap">{preview.replacement.note || 'No note'}</p>
        <p className="recording-preview__muted">Kept time retains its original details.</p>
      </details>}
      <p className="recording-preview__muted">The plan is kept. Task completion is unchanged.</p>
    </section>
    <footer className="recording-preview__footer">
      <button disabled={busy} onClick={onCancel}>Cancel</button>
      <button disabled={busy} onClick={async () => { setBusy(true); try { await onConfirm() } finally { setBusy(false) } }} className="recording-preview__confirm">{busy ? 'Recording…' : label}</button>
    </footer>
  </dialog>
}

function RecordingDiagram({ model, clock }: { model: ReturnType<typeof recordingTimeline>; clock: (value: number) => string }) {
  const fraction = (at: number) => (at - model.first) / Math.max(1, model.last - model.first)
  const boundaries = [...new Set([model.first, model.last, model.start, model.end, ...model.before.flatMap(row => [row.start, row.end])])].sort((a, b) => a - b)
  const labels = boundaries.reduce<number[]>((result, at) => {
    if (!result.length || (fraction(at) - fraction(result[result.length - 1])) * 280 >= 22) result.push(at)
    return result
  }, [])
  const piece = (row: RecordingPiece, i: number, before: boolean) => {
    const height = 280 * (fraction(row.end) - fraction(row.start))
    const a = Math.max(model.start, row.start), b = Math.min(model.end, row.end)
    return <div key={i} className={`recording-preview__piece ${row.fresh ? 'recording-preview__piece--new' : ''}`}
      style={{ top: `${fraction(row.start) * 100}%`, height: `${(fraction(row.end) - fraction(row.start)) * 100}%` }}>
      {height > 26 && <p>{row.title}</p>}
      {height > 60 && <small>{row.fresh ? 'From plan' : row.running ? 'Tracking →' : 'Recorded'}</small>}
      {row.fresh && height > 95 && <small>{clock(row.start)}–{clock(row.end)}</small>}
      {before && b > a && <i className="recording-preview__replaced" style={{ top: `${(a - row.start) / (row.end - row.start) * 100}%`, height: `${(b - a) / (row.end - row.start) * 100}%` }} />}
    </div>
  }
  return <div aria-hidden="true" className="recording-preview__diagram">
    <div className="recording-preview__lane-head"><span>Before</span><span>After</span></div>
    <div className="recording-preview__lanes">
      {boundaries.map(at => <div key={at} className="recording-preview__rule" style={{ top: `${fraction(at) * 100}%` }}>{labels.includes(at) && <span>{clock(at)}</span>}</div>)}
      <div className="recording-preview__lane">{model.before.map((row, i) => piece(row, i, true))}</div>
      <div className="recording-preview__lane">{model.after.map((row, i) => piece(row, i, false))}</div>
    </div>
  </div>
}
