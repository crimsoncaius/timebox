import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { fetchJson, fetchVoid, protocolHeaders } from '../../lib/api/client'
import './assistant.css'
import { getActivityRepository } from '../activity/activityRepository'

type RecordData = { id: number; name: string | null; task_type_id: number; task_type: { name: string }; start_at: string; end_at: string | null }
type Review = { id: number; revision: string; record: RecordData | null; reviewed: boolean; changed_since_review: boolean; receipt: { question: string; action: string; record: RecordData } }
type Page = { items: Review[]; next_before: number | null; unchecked_count: number; timezone: string; task_types: { id: number; name: string }[] }
type Result = { status: string; name?: string; message?: string }
type Exchange = { run: string; question: string; answer: string; result?: Result; error?: string; task?: boolean }
const post = (body: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(body) })
const message = (error: unknown) => error instanceof Error ? error.message : 'Could not connect. Please try again.'

export function AssistantPage() {
  const [tab, setTab] = useState<'conversation' | 'review'>('conversation')
  return <Layout><section className="assistant-page">
    <header><p className="assistant-eyebrow">A little help with your day</p><h1>Assistant</h1></header>
    <nav aria-label="Assistant sections" className="assistant-tabs">
      <button aria-current={tab === 'conversation' ? 'page' : undefined} onClick={() => setTab('conversation')}>Conversation</button>
      <button aria-current={tab === 'review' ? 'page' : undefined} onClick={() => setTab('review')}>Review</button>
    </nav>
    <div hidden={tab !== 'conversation'}><Conversation onReview={() => setTab('review')} /></div>
    {tab === 'review' && <Reviews />}
  </section></Layout>
}

function Conversation({ onReview }: { onReview: () => void }) {
  const [draft, setDraft] = useState('')
  const [exchanges, setExchanges] = useState<Exchange[]>([])
  const [busy, setBusy] = useState(false)
  const conversation = useRef<string | null>(null)
  const previous = useRef<string | null>(null)
  const abort = useRef<AbortController | null>(null)
  useEffect(() => () => abort.current?.abort(), [])
  function update(run: string, change: Partial<Exchange> | ((e: Exchange) => Partial<Exchange>)) {
    setExchanges(all => all.map(e => e.run === run ? { ...e, ...(typeof change === 'function' ? change(e) : change) } : e))
  }
  async function check(run: string) {
    try { update(run, { result: await fetchJson<Result>(`/assistant/tracking-results/${run}`), error: undefined }) }
    catch (error) { update(run, { error: message(error) }) }
  }
  async function send() {
    if (busy || !draft.trim()) return
    const run = crypto.randomUUID(), question = draft.trim()
    setBusy(true); setDraft(''); setExchanges(all => [...all, { run, question, answer: '' }])
    try {
      if (!conversation.current) conversation.current = (await fetchJson<{ conversation_id: string }>('/assistant/conversations', post({ capabilities: ['tracking_proposal_v1'] }))).conversation_id
      if (previous.current) { await fetchVoid(`/assistant/conversations/${conversation.current}/runs/${previous.current}/ack`, post({})); previous.current = null }
      const controller = new AbortController(); abort.current = controller
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || '/api'}/assistant/conversations/${conversation.current}/messages`, {
        ...post({ run_id: run, message: question, tracking_mode: 'review_later' }), signal: AbortSignal.any([controller.signal, AbortSignal.timeout(135000)]), headers: { 'Content-Type': 'application/json', ...protocolHeaders() },
      })
      if (!response.ok) { const error = await response.json(); throw new Error(typeof error.detail === 'string' ? error.detail : 'Could not start Assistant.') }
      const reader = response.body!.getReader(), decoder = new TextDecoder()
      let buffer = '', completed = false, sequence = 0
      while (true) {
        const { done, value } = await reader.read(); if (done) break
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
        let boundary: number
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const frame = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2)
          const kind = frame.split('\n').find(l => l.startsWith('event:'))?.slice(6).trim()
          const raw = frame.split('\n').filter(l => l.startsWith('data:')).map(l => l.slice(5).trimStart()).join('\n')
          if (!raw) continue
          const data = JSON.parse(raw)
          if (data.run_id !== run || data.sequence !== ++sequence) throw new Error('Response interrupted. Check the saved result before repeating your command.')
          if (kind === 'text_delta') update(run, e => ({ answer: e.answer + data.text }))
          if (kind === 'tracking_result') { update(run, { result: data }); if (data.status === 'recorded') void getActivityRepository().refresh() }
          if (kind === 'task_proposal') update(run, { task: true })
          if (kind === 'failed' || kind === 'stopped') throw new Error(data.message || 'Response stopped. Check result before repeating your command.')
          if (kind === 'completed') completed = true
        }
      }
      if (!completed) throw new Error('Connection lost. Check result before repeating your command.')
      previous.current = run
    } catch (error) { update(run, { error: message(error) }) }
    finally { setBusy(false); abort.current = null }
  }
  return <div className="assistant-conversation">
    {exchanges.length === 0 && <div className="assistant-intro"><h2>Say it. Get on with your day.</h2><p>“I’m going to the gym.” “Starting work.” “I’m done.”</p><p>Your activity is recorded immediately. Check or correct it later in Review.</p></div>}
    <div aria-live="polite">{exchanges.map(e => <article className="assistant-exchange" key={e.run}>
      <p className="assistant-question">{e.question}</p><p className="assistant-answer">{e.answer}</p>
      {e.task && <p>Task changes still need confirmation. Use the Android Assistant to request and confirm this task change.</p>}
      {e.result?.status === 'recorded' && <p className="assistant-receipt">✓ Recorded · {e.result.name} <button onClick={onReview}>Review activity →</button></p>}
      {e.result && e.result.status !== 'recorded' && !e.task && <p>{e.result.message}</p>}
      {e.error && <p role="alert">{e.error}</p>}
      {(e.error || e.result?.status === 'pending') && <button disabled={busy} onClick={() => void check(e.run)}>Check saved result</button>}
    </article>)}</div>
    <form onSubmit={e => { e.preventDefault(); void send() }} className="assistant-composer">
      <label htmlFor="tracking-command">Track now, review later <span>Activity only · Online</span></label>
      <div><input id="tracking-command" maxLength={4000} value={draft} onChange={e => setDraft(e.target.value)} placeholder="What are you doing?" /><button disabled={busy || !draft.trim()} type="submit">{busy ? 'Recording…' : 'Send →'}</button></div>
      <small>Sending authorizes activity tracking. Task and subtask changes are never applied here.</small>
    </form>
  </div>
}

function Reviews() {
  const [page, setPage] = useState<Page | null>(null), [rows, setRows] = useState<Review[]>([])
  const [reviewed, setReviewed] = useState(false), [selected, setSelected] = useState<Set<number>>(new Set())
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [edit, setEdit] = useState<Review | null>(null)
  const requestId = useRef(0)
  const load = useCallback(async (more = false, history = false, before: number | null = null) => {
    const id = ++requestId.current
    setBusy(true); setError('')
    try {
      const data = await fetchJson<Page>(`/assistant/reviews?reviewed=${history}${more && before ? `&before=${before}` : ''}`)
      if (id !== requestId.current) return
      setPage(data); setRows(data.items); setSelected(new Set())
    } catch (e) { if (id === requestId.current) setError(message(e)) }
    finally { if (id === requestId.current) setBusy(false) }
  }, [])
  useEffect(() => { void load() }, [load])
  const zone = page?.timezone || 'UTC'
  const time = (value: string | null) => value ? new Intl.DateTimeFormat(undefined, { timeZone: zone, dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Still running'
  async function acknowledge() {
    setBusy(true); setError('')
    try { await fetchJson('/assistant/reviews/acknowledge', post({ items: rows.filter(r => selected.has(r.id)).map(({ id, revision }) => ({ id, revision })) })); await load(false, reviewed) }
    catch (e) { setError(message(e)); setBusy(false) }
  }
  return <section>
    <div className="assistant-review-heading"><div><h2>A moment to look back</h2><p>Already recorded. Correct anything that doesn’t look right.</p></div><button disabled={busy} onClick={() => void load(false, reviewed)}>Refresh</button></div>
    <div className="assistant-review-toolbar"><div className="assistant-filters">{[false, true].map(history => <button key={String(history)} aria-pressed={reviewed === history} disabled={busy} onClick={() => { setReviewed(history); setRows([]); void load(false, history) }}>{history ? 'Reviewed' : `To review${page ? ` · ${page.unchecked_count}` : ''}`}</button>)}</div><span>Times in {zone}</span></div>
    {error && <p role="alert" className="assistant-error">{error}</p>}
    {busy && <p role="status">Loading…</p>}
    {!reviewed && rows.length > 0 && <div className="assistant-selection"><label><input type="checkbox" disabled={busy} checked={selected.size === rows.length} onChange={e => setSelected(e.target.checked ? new Set(rows.map(r => r.id)) : new Set())} /> Select loaded</label><button disabled={busy || !selected.size} onClick={() => void acknowledge()}>Mark reviewed ({selected.size})</button></div>}
    {!busy && !error && rows.length === 0 && <div className="assistant-empty"><h3>{reviewed ? 'No reviewed activity yet' : 'All caught up'}</h3><p>Activity recorded with Review later will appear here.</p></div>}
    {rows.map(row => { const record = row.record || row.receipt.record; return <article key={row.id} className="assistant-review-row">
      {!reviewed && <input aria-label={`Select ${row.receipt.question}`} type="checkbox" disabled={busy} checked={selected.has(row.id)} onChange={e => setSelected(old => { const next = new Set(old); if (e.target.checked) next.add(row.id); else next.delete(row.id); return next })} />}
      <div><blockquote>“{row.receipt.question}”</blockquote><h3><span>{row.receipt.action}</span> {record.name || record.task_type.name}</h3><p>{record.task_type.name} · {time(record.start_at)} → {time(record.end_at)}</p>
      {!row.record && <p>This activity was removed or split. Inspect it in Day.</p>}{row.changed_since_review && <p>Changed since you reviewed it.</p>}
      <details><summary>Originally recorded</summary><p>{row.receipt.record.name || row.receipt.record.task_type.name} · {time(row.receipt.record.start_at)} → {row.receipt.record.end_at ? time(row.receipt.record.end_at) : 'Running when recorded'}</p></details>
      <div className="assistant-row-actions"><button disabled={!row.record || busy} onClick={() => setEdit(row)}>Correct</button><Link to={`/day/${new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(record.start_at))}`}>Open day →</Link></div></div>
    </article> })}
    {page?.next_before && <button disabled={busy} onClick={() => void load(true, reviewed, page.next_before)}>Earlier activity</button>}
    {edit && page && <Correction key={edit.id} row={edit} types={page.task_types} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); void load(false, reviewed) }} />}
  </section>
}

function localInput(value: string) { const d = new Date(value); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16) }
function Correction({ row, types, onClose, onSaved }: { row: Review; types: Page['task_types']; onClose: () => void; onSaved: () => void }) {
  const record = row.record!
  const [name, setName] = useState(record.name || ''), [type, setType] = useState(record.task_type_id)
  const [start, setStart] = useState(localInput(record.start_at)), [end, setEnd] = useState(record.end_at ? localInput(record.end_at) : '')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const dialog = useRef<HTMLDialogElement>(null), pending = useRef<{ fields: string; body: unknown } | null>(null)
  useEffect(() => { dialog.current?.showModal() }, [])
  async function save() {
    setBusy(true); setError('')
    try {
      const fields = JSON.stringify([name, type, start, end])
      if (pending.current?.fields !== fields) pending.current = { fields, body: { operation_id: crypto.randomUUID(), revision: row.revision, name: name || null, task_type_id: type,
        start_at: start === localInput(record.start_at) ? record.start_at : new Date(start).toISOString(), end_at: record.end_at ? end === localInput(record.end_at) ? record.end_at : new Date(end).toISOString() : null } }
      await fetchJson(`/assistant/reviews/${row.id}/correct`, post(pending.current.body)); void getActivityRepository().refresh(); onSaved()
    } catch (e) { setError(message(e)) } finally { setBusy(false) }
  }
  return <dialog ref={dialog} onCancel={e => { if (busy) e.preventDefault(); else onClose() }} className="assistant-dialog"><form onSubmit={e => { e.preventDefault(); void save() }}>
    <h2>Correct activity</h2><p>Times in your device’s timezone ({Intl.DateTimeFormat().resolvedOptions().timeZone}).</p>
    <label>Name<input maxLength={500} disabled={busy} value={name} onChange={e => setName(e.target.value)} /></label>
    <label>Task Type<select disabled={busy} value={type} onChange={e => setType(Number(e.target.value))}>{types.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
    <label>Start<input disabled={busy} required type="datetime-local" value={start} onChange={e => setStart(e.target.value)} /></label>
    {record.end_at && <label>End<input disabled={busy} required type="datetime-local" value={end} onChange={e => setEnd(e.target.value)} /></label>}
    {error && <p role="alert">{error}</p>}<footer><button type="button" disabled={busy} onClick={onClose}>Cancel</button><button disabled={busy} type="submit">{busy ? 'Saving…' : 'Save correction'}</button></footer>
  </form></dialog>
}
