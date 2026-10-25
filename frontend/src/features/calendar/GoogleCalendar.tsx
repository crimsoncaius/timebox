import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchJson } from '../../lib/api/client'
import { api } from '../../lib/api'
import type { BattleTask, TaskType } from '../../lib/api'
import { zonedLocalDateTimeToIso } from '../../lib/time'

type Plan = { id: number; name: string | null; start_at?: string; end_at?: string; start?: string; end?: string; occurrence_id?: string | null }
type Occurrence = { id: string; block_id: number | null; name: string; start: string; end: string; status: string; previous: string[] | null; url: string | null; calendar_id: string; task_type_id: number | null; task_id: number | null; note: string | null; overlaps: Plan[]; competing_ids: string[]; updates: { kind: string; at: string; start: string; end: string }[] }
type CalendarState = { configured: boolean; connected: boolean; account: string | null; selected: string[]; calendars: { id: string; name: string; primary?: boolean }[]; last_success: string | null; error: string | null; timezone: string; occurrences: Occurrence[] }
type Command = { kind: string; id?: string; selected?: string[]; edits?: { id: number; action: string; start?: string; end?: string }[]; exclude_ids?: string[]; task_type_id?: number | null; task_id?: number | null; note?: string | null }
type Preview = { fingerprint: string; before: Plan[]; after: Plan[]; state: CalendarState }
const button = 'min-h-11 rounded-lg border border-outline-variant/40 px-4 py-2 text-sm hover:bg-surface-container-high disabled:opacity-50 dark:border-dark-outline-variant dark:hover:bg-dark-surface-container-high'
const field = 'min-h-11 w-full rounded-lg border border-outline-variant/40 bg-surface px-3 py-2 text-sm dark:bg-dark-surface dark:border-dark-outline-variant'
const format = (value: string, zone: string) => new Date(value).toLocaleString(undefined, { timeZone: zone, dateStyle: 'medium', timeStyle: 'short' })
function local(value: string, zone: string) {
  const parts = new Intl.DateTimeFormat('sv-SE', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(value))
  return parts.replace(' ', 'T')
}

export function GoogleCalendar({ date, onChanged }: { date?: string; onChanged?: () => void }) {
  const [state, setState] = useState<CalendarState | null>(null)
  const [selected, setSelected] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [open, setOpen] = useState(false)
  const [detail, setDetail] = useState<string | null>(null)
  const [preview, setPreview] = useState<{ command: Command; result: Preview } | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const changed = useRef(onChanged)
  const previousSync = useRef<string | null>(null)
  const loadVersion = useRef(0)
  useEffect(() => { changed.current = onChanged }, [onChanged])
  const load = useCallback(async () => {
    const version = ++loadVersion.current
    const next = await fetchJson<CalendarState>(`/google-calendar${date ? `?date=${date}` : ''}`)
    if (version !== loadVersion.current) return
    if (!Array.isArray(next.occurrences) || !Array.isArray(next.selected) || !Array.isArray(next.calendars)) throw new Error('Calendar response is unavailable. Refresh or update the server.')
    setState(next)
    setError(null)
    setSelected(current => stateSelection.current === null || JSON.stringify(current) === stateSelection.current ? next.selected : current)
    stateSelection.current = JSON.stringify(next.selected)
    if (date && previousSync.current && previousSync.current !== next.last_success) changed.current?.()
    previousSync.current = next.last_success
  }, [date])
  const stateSelection = useRef<string | null>(null)
  useEffect(() => {
    let active = true
    const reload = () => { if (active) void load().catch(e => setError(String(e.message))) }
    reload()
    const timer = setInterval(reload, 30000)
    window.addEventListener('focus', reload)
    return () => { active = false; loadVersion.current++; clearInterval(timer); window.removeEventListener('focus', reload) }
  }, [load])
  useEffect(() => {
    if (!message) return
    const timer = setTimeout(() => setMessage(null), 6000)
    return () => clearTimeout(timer)
  }, [message])
  const run = async (command: Command, fingerprint?: string) => {
    setBusy(true); setError(null)
    try {
      const result = await fetchJson<Preview>('/google-calendar/action', { method: 'POST', body: JSON.stringify({ command, fingerprint }) })
      if (fingerprint) {
        setPreview(null); setDetail(null); stateSelection.current = null
        await load(); changed.current?.(); setMessage('Calendar plan updated')
      } else setPreview({ command, result })
    } catch (e) { setError((e as Error).message); if (fingerprint) setPreview(null) }
    finally { setBusy(false) }
  }
  const pending = state?.occurrences.filter(o => o.status === 'pending') ?? []
  const updates = state?.occurrences.filter(o => o.updates.length > 0) ?? []
  if (date && !pending.length && !updates.length && !state?.error && !error) return null
  return <section aria-label="Google Calendar" className="mb-6 space-y-4 text-on-surface dark:text-dark-on-surface">
    {!date && <>
      <p className="text-sm text-on-surface-variant">Import commitments into your plan. Google Calendar is never changed.</p>
      {!state && !error && <p role="status">Loading calendar connection…</p>}
      {state && !state.configured && <p className="text-sm">Google Calendar is not configured on this server. Set up Google OAuth and authenticated access using the deployment guide.</p>}
      {state?.account && <p className="text-sm">{state.connected ? 'Connected to' : 'Disconnected from'} {state.account}</p>}
      <div className="flex flex-wrap gap-2">
        <button className={button} disabled={busy || !state?.configured} onClick={async () => {
          const popup = window.open('about:blank', '_blank')
          if (popup) popup.opener = null
          setBusy(true); setError(null)
          try { const { url } = await fetchJson<{ url: string }>('/google-calendar/connect', { method: 'POST' }); if (popup) popup.location.href = url; else window.location.assign(url) }
          catch (e) { popup?.close(); setError((e as Error).message) }
          finally { setBusy(false) }
        }}>{state?.connected ? 'Reconnect Google' : 'Connect Google Calendar'}</button>
        {state?.connected && <button className={button} disabled={busy} onClick={() => void run({ kind: 'disconnect' })}>Disconnect</button>}
        {state?.account && <button className={button} disabled={busy} onClick={() => void run({ kind: 'forget' })}>{state.connected ? 'Disconnect and forget' : 'Forget retained Google import data'}</button>}
      </div>
      {state?.connected && <fieldset disabled={busy} className="space-y-3">
        <legend className="mb-2 text-sm font-medium">Calendars, in priority order</legend>
        <p className="text-sm text-on-surface-variant">The first selected copy supplies a duplicated meeting’s details.</p>
        {[...selected.map(id => state.calendars.find(c => c.id === id)).filter(c => !!c), ...state.calendars.filter(c => !selected.includes(c.id)).sort((a, b) => Number(b.primary) - Number(a.primary))].map(c => <div key={c.id} className="flex items-center gap-3">
          <label className="flex min-h-11 flex-1 items-center gap-3"><input type="checkbox" checked={selected.includes(c.id)} onChange={e => setSelected(e.target.checked ? [...selected, c.id] : selected.filter(id => id !== c.id))} />{c.name}</label>
          {selected.includes(c.id) && <button className={button} disabled={selected.indexOf(c.id) === 0} aria-label={`Move ${c.name} earlier`} onClick={() => setSelected(current => { const copy = [...current]; const i = copy.indexOf(c.id); [copy[i - 1], copy[i]] = [copy[i], copy[i - 1]]; return copy })}>Move up</button>}
        </div>)}
        <button className={button} disabled={JSON.stringify(selected) === JSON.stringify(state.selected)} onClick={() => void run({ kind: 'select', selected })}>Preview calendar changes</button>
      </fieldset>}
    </>}
    {(state?.connected || state?.error) && <div className="flex flex-wrap items-center gap-3 text-sm">
      <span>{state.last_success ? `Last synced ${format(state.last_success, state.timezone)}` : 'Not synced yet'}</span>
      <button className={button} disabled={busy} onClick={async () => { setBusy(true); setError(null); try { await fetchJson('/google-calendar/refresh', { method: 'POST' }); await load(); changed.current?.() } catch (e) { setError((e as Error).message) } finally { setBusy(false) } }}>Refresh now</button>
    </div>}
    {(error || state?.error) && <p role="alert" className="text-sm text-error dark:text-error-container">{error || state?.error}</p>}
    {message && <p role="status" className="text-sm">{message}</p>}
    {pending.length > 0 && <button className={button} aria-expanded={open} onClick={() => setOpen(!open)}>{pending.length} calendar {pending.length === 1 ? 'conflict' : 'conflicts'} — review</button>}
    {(open || !date) && <div className="divide-y divide-outline-variant/30">{state?.occurrences.filter(o => o.status === 'pending' || o.status === 'excluded').map(o => <div className="py-3" key={o.id}>
      <button className="min-h-11 w-full text-left" onClick={() => setDetail(detail === o.id ? null : o.id)} aria-expanded={detail === o.id}><span className="block font-medium">{o.name}</span><span className="text-sm text-on-surface-variant">{format(o.start, state.timezone)} · {o.status === 'excluded' ? 'Left out' : 'Needs room'}</span></button>
      {detail === o.id && <OccurrenceDetails occurrence={o} state={state} busy={busy} onAction={run} onClose={() => setDetail(null)} />}
    </div>)}</div>}
    {date && updates.length > 0 && <details><summary className="min-h-11 cursor-pointer py-3 text-sm">Calendar updates</summary>{updates.map(o => <div key={o.id} className="mb-3 text-sm"><strong>{o.name}</strong>{o.updates.map((u, i) => <p key={i}>{u.kind} · {format(u.at, state!.timezone)}</p>)}</div>)}</details>}
    {preview && state && <CalendarPreview preview={preview.result} command={preview.command} zone={state.timezone} busy={busy} onCancel={() => setPreview(null)} onConfirm={() => void run(preview.command, preview.result.fingerprint)} />}
  </section>
}

function OccurrenceDetails({ occurrence: o, state, busy, onAction, onClose }: { occurrence: Occurrence; state: CalendarState; busy: boolean; onAction: (c: Command) => Promise<void>; onClose: () => void }) {
  const [types, setTypes] = useState<TaskType[]>([])
  const [tasks, setTasks] = useState<BattleTask[]>([])
  const [typeId, setTypeId] = useState(o.task_type_id)
  const [taskId, setTaskId] = useState(o.task_id)
  const [note, setNote] = useState(o.note ?? '')
  const [edits, setEdits] = useState<Record<number, { action: string; start: string; end: string }>>({})
  const [excludeIds, setExcludeIds] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  useEffect(() => { void Promise.all([api.listTaskTypes(), api.listBattleTasks('active')]).then(([tt, bt]) => { setTypes(tt); setTasks(bt.items.flatMap(t => [t, ...(t.session_tasks ?? [])])) }).catch(e => setError(e.message)) }, [])
  return <div className="space-y-4 pb-3">
    <p className="text-sm">{format(o.start, state.timezone)} – {format(o.end, state.timezone)}</p>
    {o.previous?.[0] && <p className="text-sm">Previously {format(o.previous[0], state.timezone)} – {format(o.previous[1], state.timezone)}</p>}
    <p className="text-sm">Google Calendar · {state.calendars.find(c => c.id === o.calendar_id)?.name ?? o.calendar_id} {o.url && <a className="underline" href={o.url} target="_blank" rel="noreferrer">Open source event</a>}</p>
    {o.overlaps.map(block => {
      const edit = edits[block.id] ?? { action: '', start: local(block.start!, state.timezone), end: local(block.end!, state.timezone) }
      const change = (patch: Partial<typeof edit>) => setEdits({ ...edits, [block.id]: { ...edit, ...patch } })
      return <fieldset key={block.id} className="space-y-2 border-t border-outline-variant/30 pt-3" disabled={busy}>
        <legend className="font-medium">{block.name || 'Unnamed Planned Block'}</legend><p className="text-sm">{format(block.start!, state.timezone)} – {format(block.end!, state.timezone)}</p>
        <label className="block text-sm">Make room<select className={field} value={edit.action} onChange={e => change({ action: e.target.value })}><option value="">Keep unchanged</option>{!block.occurrence_id && <option value="move">Move or shorten</option>}<option value="remove">{block.occurrence_id ? 'Leave this meeting out' : 'Remove Planned Block'}</option></select></label>
        {edit.action === 'move' && <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Start<input className={field} type="datetime-local" value={edit.start} onChange={e => change({ start: e.target.value })} /></label><label className="text-sm">End<input className={field} type="datetime-local" value={edit.end} onChange={e => change({ end: e.target.value })} /></label></div>}
      </fieldset>
    })}
    {o.competing_ids.map(id => <label key={id} className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={excludeIds.includes(id)} onChange={e => setExcludeIds(e.target.checked ? [...excludeIds, id] : excludeIds.filter(x => x !== id))} />Leave out {state.occurrences.find(x => x.id === id)?.name ?? 'the other pending meeting'}</label>)}
    <div className="flex flex-wrap gap-2">
      <button className={button} disabled={busy || !state.connected} onClick={() => {
        try { void onAction({ kind: o.status === 'excluded' ? 'include' : 'resolve', id: o.id, exclude_ids: excludeIds, edits: Object.entries(edits).filter(([, e]) => e.action).map(([id, e]) => ({ id: Number(id), action: e.action, ...(e.action === 'move' ? { start: zonedLocalDateTimeToIso(e.start, state.timezone, 'earlier'), end: zonedLocalDateTimeToIso(e.end, state.timezone, 'earlier') } : {}) })) }) }
        catch (e) { setError((e as Error).message) }
      }}>{o.status === 'excluded' ? 'Include again' : 'Preview making room'}</button>
      {o.status !== 'excluded' && <button className={button} disabled={busy} onClick={() => void onAction({ kind: 'exclude', id: o.id })}>Leave this meeting out</button>}
      <button className={button} onClick={onClose}>Resolve later</button>
    </div>
    <details><summary className="min-h-11 cursor-pointer py-3 text-sm">Local classification and notes</summary><div className="space-y-3">
      <label className="block text-sm">Task Type<select className={field} value={typeId ?? ''} onChange={e => setTypeId(Number(e.target.value) || null)}><option value="">unspecified</option>{types.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
      <label className="block text-sm">Battle Plan Task<select className={field} value={taskId ?? ''} onChange={e => setTaskId(Number(e.target.value) || null)}><option value="">No linked Task</option>{tasks.map(t => <option key={t.id} value={t.id}>{t.title}</option>)}</select></label>
      <label className="block text-sm">Supporting Note<textarea className={field} value={note} onChange={e => setNote(e.target.value)} /></label>
      <button className={button} disabled={busy} onClick={() => void onAction({ kind: 'metadata', id: o.id, task_type_id: typeId, task_id: taskId, note })}>Preview local changes</button>
    </div></details>
    {error && <p role="alert">{error}</p>}
  </div>
}

function CalendarPreview({ preview, command, zone, busy, onCancel, onConfirm }: { preview: Preview; command: Command; zone: string; busy: boolean; onCancel: () => void; onConfirm: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal(); return () => dialog.current?.close() }, [])
  const byStart = (a: Plan, b: Plan) => (a.start_at ?? '').localeCompare(b.start_at ?? '')
  const changed = preview.before.filter(a => JSON.stringify(a) !== JSON.stringify(preview.after.find(b => b.id === a.id))).sort(byStart)
  const added = preview.after.filter(a => JSON.stringify(a) !== JSON.stringify(preview.before.find(b => b.id === a.id))).sort(byStart)
  return <dialog ref={dialog} onCancel={e => { e.preventDefault(); if (!busy) onCancel() }} aria-labelledby="calendar-preview-title" className="m-auto max-h-[85dvh] w-[min(40rem,94vw)] overflow-y-auto rounded-xl bg-surface p-6 text-on-surface backdrop:bg-black/40 dark:bg-dark-surface dark:text-dark-on-surface">
    <h2 id="calendar-preview-title" className="mb-4 font-headline text-2xl">Review calendar changes</h2>
    <p className="mb-4 text-sm">Google events and recorded activity stay unchanged. All plan edits apply together.</p>
    {(command.kind === 'forget' || command.kind === 'disconnect') && <p className="mb-4 text-sm">Disconnect Google and remove future imported plans.{command.kind === 'forget' && ' Also forget cached details and remembered choices; historical plan identities remain protected.'}</p>}
    {command.kind === 'metadata' && <p className="mb-4 text-sm">Save the chosen Task Type, Task linkage, and Supporting Note for this occurrence.</p>}
    {command.kind === 'exclude' && <p className="mb-4 text-sm">Leave this occurrence out until Google changes its scheduled interval.</p>}
    {changed.length > 0 && <><h3 className="font-medium">Before</h3>{changed.map(p => <p className="my-2 text-sm" key={p.id}>{p.name || 'Unnamed Planned Block'} · {format(p.start_at!, zone)} – {format(p.end_at!, zone)}</p>)}</>}
    {added.length > 0 && <><h3 className="mt-4 font-medium">After</h3>{added.map(p => <p className="my-2 text-sm" key={p.id}>{p.name || 'Unnamed Planned Block'} · {format(p.start_at!, zone)} – {format(p.end_at!, zone)}</p>)}</>}
    <p className="my-4 text-sm">{preview.state.occurrences.filter(o => o.status === 'pending').length} pending conflicts after this change.</p>
    <div className="mt-6 flex flex-wrap gap-3"><button className={button} disabled={busy} onClick={onCancel}>Back</button><button className={`${button} bg-planned text-on-planned`} disabled={busy} onClick={onConfirm}>{busy ? 'Applying…' : 'Confirm changes'}</button></div>
  </dialog>
}

export function ApiKeySetting() {
  const [value, setValue] = useState(() => sessionStorage.getItem('timebox.api-key') ?? '')
  const [saved, setSaved] = useState(false)
  return <form className="space-y-3" onSubmit={e => { e.preventDefault(); sessionStorage.setItem('timebox.api-key', value.trim()); setSaved(true); window.dispatchEvent(new Event('focus')) }}>
    <label className="block text-sm">Timebox API key<input className={field} type="password" autoComplete="off" value={value} onChange={e => { setValue(e.target.value); setSaved(false) }} /></label>
    <p className="text-sm text-on-surface-variant">Stored for this browser tab’s session. Required when your server uses authenticated access.</p>
    <button className={button}>Save connection</button>{saved && <p role="status" className="text-sm">Connection saved.</p>}
  </form>
}
