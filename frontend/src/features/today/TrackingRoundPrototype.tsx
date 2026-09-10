// THROWAWAY: round 1 asks whether tracking belongs above the Day timeline.
import { useState } from 'react'
import { DragDropProvider } from '@dnd-kit/react'
import { DayTimeline } from '../../components/DayTimeline'
import type { DayRead, TimeBlock } from '../../lib/api'

const date = '2026-09-10'
const stamp = `${date}T00:00:00+08:00`
const taskType = { id: 1, name: 'Work', created_at: stamp, updated_at: stamp }
const time = (minute: number) => `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`
const taskTypes = [taskType, { ...taskType, id: 2, name: 'Break' }, { ...taskType, id: 3, name: 'Personal' }, { ...taskType, id: 4, name: 'unspecified' }]
type Entry = { name: string; taskType: typeof taskType; plannedId: number | null; start: number; end: number | null }
const planned: TimeBlock[] = [
  { id: 1, lane: 'planned', task_type_id: 1, task_type: taskType, name: 'Writing a proposal', note: null, start_minute: 600, end_minute: 720, created_at: stamp, updated_at: stamp },
  { id: 2, lane: 'planned', task_type_id: 1, task_type: taskType, name: 'Lunch', note: null, start_minute: 720, end_minute: 780, created_at: stamp, updated_at: stamp },
]

export function TrackingRoundPrototype() {
  const roundSix = new URLSearchParams(location.search).get('round') === '6'
  const [endEditor, setEndEditor] = useState(false)
  const [atTime, setAtTime] = useState('12:15')
  const roundFive = new URLSearchParams(location.search).get('round') === '5'
  const [pending, setPending] = useState(roundFive)
  const roundFour = new URLSearchParams(location.search).get('round') === '4'
  const roundThree = new URLSearchParams(location.search).get('round') === '3'
  const roundTwo = new URLSearchParams(location.search).get('round') === '2' || roundThree || roundFour || roundFive || roundSix
  const initialEntries: Entry[] = roundFour || roundFive || roundSix ? [{ name: 'Writing a proposal', taskType, plannedId: 1, start: 600, end: null }] : roundThree ? [{ name: '', taskType: taskTypes[3], plannedId: null, start: 800, end: null }] : []
  const initialNow = roundSix ? 735 : roundFive ? 690 : roundFour ? 715 : roundThree ? 810 : 660
  const [focus, setFocus] = useState(roundThree)
  const [now, setNow] = useState(initialNow)
  const [entries, setEntries] = useState<Entry[]>(initialEntries)
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [selectedType, setSelectedType] = useState('')
  const current = entries.find(e => e.end === null)
  function startOrSwitch(requestedName = '', typeId?: number, at = now) {
    if (!Number.isFinite(at) || at > now || (current && at < current.start)) return
    const plan = typeId === undefined ? planned.find(p => p.start_minute <= now && p.end_minute > now) : undefined
    const chosenType = typeId === undefined ? plan?.task_type ?? taskTypes[3] : taskTypes.find(t => t.id === typeId)
    if (!chosenType) return
    setEntries(old => [...old.map(e => e.end === null ? { ...e, end: at } : e), { name: requestedName.trim() || plan?.name || '', taskType: chosenType, plannedId: plan?.id ?? null, start: at, end: null }])
    setName(''); setSelectedType(''); setEditing(false); setPending(false)
  }
  const parsedTime = atTime.split(':').map(Number)
  const correctionTime = parsedTime.length === 2 ? parsedTime[0] * 60 + parsedTime[1] : NaN
  const validTime = Number.isFinite(correctionTime) && correctionTime <= now && !!current && correctionTime >= current.start
  function openSwitch() {
    setSelectedType(''); setName(''); setAtTime(time(now)); setEndEditor(false); setEditing(true)
  }
  function stopTracking(at = now) {
    if (!Number.isFinite(at) || at > now || (current && at < current.start)) return
    setEndEditor(false)
    setEntries(old => old.map(e => e.end === null ? { ...e, end: at } : e))
    setEditing(false); setName(''); setSelectedType(''); setPending(false)
  }
  const needsDescription = focus && current?.taskType.id === 4 && !current.name
  function describeCurrent() {
    const chosenType = taskTypes.find(t => t.id === Number(selectedType))
    if (!chosenType) return
    setEntries(old => old.map(e => e.end === null ? { ...e, name: name.trim(), taskType: chosenType } : e))
    setName(''); setSelectedType(''); setEditing(false); setPending(false)
  }
  const day: DayRead = {
    id: 1, date, start_hour: 9, end_hour: 15, show_full_day: false, created_at: stamp, updated_at: stamp,
    time_blocks: planned,
    actual_blocks: entries.map((entry, i) => ({ date, start_minute: entry.start, end_minute: entry.end ?? now,
      duration_minutes: (entry.end ?? now) - entry.start,
      actual_block: { id: 100 + i, task_type_id: entry.taskType.id, task_type: entry.taskType, task_id: null, task: null,
        name: entry.name || entry.taskType.name, note: null, planned_block_id: entry.plannedId,
        start_at: `${date}T${time(entry.start)}:00+08:00`, end_at: entry.end === null ? null : `${date}T${time(entry.end)}:00+08:00`, created_at: stamp, updated_at: stamp },
    })),
    meta: { timezone: 'Asia/Singapore', today: date, server_now_iso: `${date}T${time(now)}:00+08:00` },
  }
  const button = 'rounded px-2 py-1 text-xs text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
  const suggestedPlan = roundFour && current ? planned.find(p => p.start_minute <= now && p.end_minute > now && p.id !== current.plannedId && p.start_minute > current.start) : undefined
  const suggestion = suggestedPlan && <div className={`flex flex-wrap items-center gap-2 rounded-md bg-surface-container-low px-3 py-2 text-xs text-on-surface-variant ${focus ? 'mt-10 justify-center' : 'mt-2 justify-end'}`} aria-label="Planned activity suggestion">
    <span className="font-medium text-on-surface">{suggestedPlan.name} is planned now.</span><button className={button} onClick={() => startOrSwitch()}>Switch to {suggestedPlan.name}</button>
  </div>
  const timeField = <div className="mt-5 border-t border-outline-variant pt-5"><label className="text-xs font-medium text-on-surface-variant" htmlFor="correction-time">{endEditor ? 'When did you stop?' : 'When did you switch?'}</label><div className="mt-2 flex items-center gap-3"><input id="correction-time" type="time" required min={current ? time(current.start) : undefined} max={time(now)} value={atTime} onChange={e => setAtTime(e.target.value)} className="rounded-lg border border-outline-variant bg-surface px-3 py-2 text-sm" /><button type="button" className="rounded-full border border-outline-variant px-3 py-1.5 text-xs text-on-surface-variant hover:bg-surface-container-low" onClick={() => setAtTime(time(Math.max(current?.start ?? 0, now - 15)))}>15 min ago</button></div></div>
  const preview = current && <div className="mt-5 rounded-lg bg-surface-container-low p-4" aria-label="Recording preview"><p className="mb-3 text-[10px] font-semibold uppercase tracking-widest text-on-surface-variant">After this change</p>{validTime ? <div className="space-y-3 text-sm"><div className="flex items-start justify-between gap-4"><span className="min-w-0 break-words">{current.name || current.taskType.name}</span><span className="shrink-0 text-xs tabular-nums text-on-surface-variant">{time(current.start)}–{atTime}</span></div><div className="flex items-start justify-between gap-4 border-t border-outline-variant pt-3"><span className="min-w-0 break-words font-medium">{endEditor ? 'Untracked' : name.trim() || taskTypes.find(t => t.id === Number(selectedType))?.name || 'Next activity'}</span><span className="shrink-0 text-xs tabular-nums text-on-surface-variant">{atTime}–{endEditor ? time(now) : 'now'}</span></div></div> : <p className="text-xs">Choose a time between {time(current.start)} and {time(now)}.</p>}</div>
  const stopForm = <form className="mt-5 w-full max-w-md rounded-xl border border-outline-variant bg-surface p-5 text-left shadow-sm" onSubmit={e => { e.preventDefault(); if (validTime) stopTracking(correctionTime) }}><h2 className="text-base font-semibold">Finish tracking</h2><p className="mt-1 text-xs text-on-surface-variant">Check when your activity ended.</p>{timeField}{preview}<div className="mt-5 flex flex-row-reverse items-center gap-3"><button disabled={!validTime} className="rounded-lg bg-on-surface px-4 py-2.5 text-xs font-medium text-surface disabled:opacity-40">Stop at {atTime}</button><button type="button" className={button} onClick={() => setEndEditor(false)}>Cancel</button></div></form>
  const switchForm = <form className={roundSix ? "mt-5 w-full max-w-md rounded-xl border border-outline-variant bg-surface p-5 text-left shadow-sm" : "mt-6 w-full max-w-sm text-left"} onSubmit={e => { e.preventDefault(); if (selectedType) { if (needsDescription) describeCurrent(); else startOrSwitch(name, Number(selectedType), roundSix ? correctionTime : now) } }}>
    {roundSix && <header className="mb-5"><h2 className="text-base font-semibold">Switch activity</h2><p className="mt-1 text-xs text-on-surface-variant">Choose what you moved on to, then check the time.</p></header>}
    <label className="text-sm">Task type<select autoFocus required value={selectedType} onChange={e => setSelectedType(e.target.value)} className="mt-2 block w-full rounded border border-outline-variant bg-surface p-2"><option value="" disabled>Choose a task type</option>{taskTypes.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
    <label className="mt-4 block text-sm">Activity name (optional)<input className="mt-2 block w-full rounded border border-outline-variant bg-surface p-2" value={name} onChange={e => setName(e.target.value)} /></label>
    {roundSix && !needsDescription && <>{timeField}{preview}</>}
    {needsDescription && <p className="mt-3 text-xs text-on-surface-variant">Apply to the time since {time(current!.start)}, or start now and leave earlier time unspecified.</p>}
    <div className={roundSix ? "mt-5 flex flex-row-reverse items-center gap-3" : "mt-3 flex gap-2"}><button disabled={!selectedType || (roundSix && !validTime)} className="rounded-md bg-on-surface px-3 py-2 text-xs text-surface disabled:opacity-40">{needsDescription ? `Apply from ${time(current!.start)}` : roundSix ? `Switch at ${atTime}` : 'Switch now'}</button>{needsDescription ? <button type="button" disabled={!selectedType} className={`${button} disabled:opacity-40`} onClick={() => startOrSwitch(name, Number(selectedType))}>Start now</button> : <button type="button" className={button} onClick={() => { setEditing(false); setName(''); setSelectedType('') }}>Cancel</button>}</div>
  </form>
  const inactivityPrompt = pending && current && <section aria-label="Inactivity check-in" className={`rounded-lg border border-outline-variant bg-surface-container-low p-4 text-left ${focus ? 'mt-10 w-full max-w-md' : 'mt-4'}`}>
    <p className="text-xs text-on-surface-variant">Still doing this?</p><p className="mt-1 text-lg font-semibold text-on-surface">{current.name || current.taskType.name}</p>
    <p className="mt-1 text-xs text-on-surface-variant">Your device has been quiet for an hour. Tracking is still running.</p>
    <div className="mt-3 flex flex-wrap gap-2"><button className="rounded-md bg-on-surface px-3 py-2 text-xs font-medium text-surface hover:opacity-90" onClick={() => { setPending(false); setEditing(false); setName(''); setSelectedType('') }}>Still doing this</button><button className="rounded-md border border-outline-variant bg-surface px-3 py-2 text-xs font-medium text-on-surface hover:bg-surface-container" onClick={openSwitch}>Switch activity</button>{!focus && <button className={button} onClick={() => { if (roundSix) { setEditing(false); setEndEditor(true); setAtTime(time(now)) } else stopTracking() }}>Stop tracking</button>}</div>
  </section>
  return <div className="min-h-screen bg-surface font-body text-on-surface">
    <div className="bg-on-surface px-6 py-2 text-xs text-surface flex items-center justify-between gap-4">
      <span>THROWAWAY · {roundSix ? 'Round 6: earlier switch or stop' : roundFive ? 'Round 5: inactivity check-in (simulated)' : roundFour ? 'Round 4: planned activity suggestion' : roundThree ? 'Round 3: describe an unspecified activity' : roundTwo ? 'Round 2: Focus entry and exit' : 'Round 1: tracking above the Day timeline'} · sample data</span>
      <div className="flex items-center gap-4"><span>Sample clock {time(now)}</span><button onClick={() => setNow(n => n + 5)}>+5 min</button><button onClick={() => { setEntries(initialEntries); setNow(initialNow); setEditing(false); setName(''); setSelectedType(''); setFocus(roundThree); setPending(roundFive); setEndEditor(false); setAtTime(time(initialNow)) }}>Reset</button></div>
    </div>
    {focus && current ? <main className="mx-auto flex min-h-[calc(100vh-40px)] max-w-5xl flex-col px-8 py-8">
      <header className="flex items-center justify-between"><span className="text-xs uppercase tracking-widest text-on-surface-variant">Focus</span><button className={button} onClick={() => { setFocus(false); setEditing(false); setName('') }}>Exit Focus</button></header>
      <section className="flex flex-1 flex-col items-center justify-center py-20 text-center" aria-label="Focused activity">
        <p className="mb-5 text-xs text-on-surface-variant">Recording · since {time(current.start)}</p>
        <h1 className="max-w-2xl font-headline text-4xl sm:text-5xl">{needsDescription ? 'What are you doing right now?' : current.name || current.taskType.name}</h1>
        <p className="mt-6 text-2xl tabular-nums text-on-surface-variant">{now-current.start} min</p>
        {!needsDescription && <button className={`${button} mt-8`} onClick={openSwitch}>Switch activity</button>}
        {(editing || needsDescription) && switchForm}
        {suggestion}
        {inactivityPrompt}
      </section>
      <p className="text-center text-xs text-on-surface-variant">Tracking continues when you exit Focus.</p>
    </main> : <><aside className="fixed left-0 top-16 hidden w-64 px-8 py-8 lg:block" aria-label="Surrounding app context">
      <h1 className="font-headline text-lg uppercase tracking-widest">Timebox</h1><p className="mt-1 text-xs text-on-surface-variant">Monastic productivity</p>
      <nav className="mt-12 flex flex-col gap-6 text-sm"><strong className="border-l-4 border-primary pl-4">Day</strong><span className="pl-5">Chronicle</span><span className="pl-5">Battle Plan</span><span className="pl-5">Task types</span></nav>
    </aside>
    <main className="mx-auto max-w-6xl p-8 lg:ml-64">
      <header className="mb-8"><p className="text-xs uppercase tracking-widest text-on-surface-variant">Day</p><h1 className="mt-2 font-headline text-3xl">Thursday, September 10, 2026</h1></header>
      <section aria-label="Activity tracking" className="mb-3">
        <div className="flex flex-wrap items-center justify-end gap-2 text-xs text-on-surface-variant">
          {current ? <><span aria-label="Tracking active" className="h-1.5 w-1.5 rounded-full bg-actual" /><span>{current.name || current.taskType.name}</span><span className="tabular-nums">· {now-current.start} min</span><button className={button} onClick={openSwitch}>Switch</button><button className={button} onClick={() => { if (roundSix) { setEditing(false); setEndEditor(true); setAtTime(time(now)) } else stopTracking() }}>Stop tracking</button></> : <button className={button} onClick={() => startOrSwitch()}>Start tracking</button>}
          {roundTwo && <button className={button} onClick={() => { if (!current) startOrSwitch(); setEditing(false); setEndEditor(false); setName(''); setFocus(true) }}>Focus</button>}
        </div>
        {suggestion}
        {inactivityPrompt}
        {editing && switchForm}
        {endEditor && stopForm}
      </section>
      <DragDropProvider><DayTimeline day={day} readOnly draft={null} selectedBlockId={null} onLaneSlotClick={() => {}} onPatchBlock={async () => {}} /></DragDropProvider>
    </main></>}
  </div>
}





