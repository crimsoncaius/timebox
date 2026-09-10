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
  const roundFour = new URLSearchParams(location.search).get('round') === '4'
  const roundThree = new URLSearchParams(location.search).get('round') === '3'
  const roundTwo = new URLSearchParams(location.search).get('round') === '2' || roundThree || roundFour
  const initialEntries: Entry[] = roundFour ? [{ name: 'Writing a proposal', taskType, plannedId: 1, start: 600, end: null }] : roundThree ? [{ name: '', taskType: taskTypes[3], plannedId: null, start: 800, end: null }] : []
  const initialNow = roundFour ? 715 : roundThree ? 810 : 660
  const [focus, setFocus] = useState(roundThree)
  const [now, setNow] = useState(initialNow)
  const [entries, setEntries] = useState<Entry[]>(initialEntries)
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [selectedType, setSelectedType] = useState('')
  const current = entries.find(e => e.end === null)
  function startOrSwitch(requestedName = '', typeId?: number) {
    const plan = typeId === undefined ? planned.find(p => p.start_minute <= now && p.end_minute > now) : undefined
    const chosenType = typeId === undefined ? plan?.task_type ?? taskTypes[3] : taskTypes.find(t => t.id === typeId)
    if (!chosenType) return
    setEntries(old => [...old.map(e => e.end === null ? { ...e, end: now } : e), { name: requestedName.trim() || plan?.name || '', taskType: chosenType, plannedId: plan?.id ?? null, start: now, end: null }])
    setName(''); setSelectedType(''); setEditing(false)
  }
  const needsDescription = focus && current?.taskType.id === 4 && !current.name
  function describeCurrent() {
    const chosenType = taskTypes.find(t => t.id === Number(selectedType))
    if (!chosenType) return
    setEntries(old => old.map(e => e.end === null ? { ...e, name: name.trim(), taskType: chosenType } : e))
    setName(''); setSelectedType(''); setEditing(false)
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
  const suggestion = suggestedPlan && <div className={`flex flex-wrap items-center gap-2 text-xs text-on-surface-variant ${focus ? 'mt-10 justify-center' : 'mt-2 justify-end'}`} aria-label="Planned activity suggestion">
    <span>{suggestedPlan.name} is planned now.</span><button className={button} onClick={() => startOrSwitch()}>Switch to {suggestedPlan.name}</button>
  </div>
  const switchForm = <form className="mt-6 w-full max-w-sm text-left" onSubmit={e => { e.preventDefault(); if (selectedType) { if (needsDescription) describeCurrent(); else startOrSwitch(name, Number(selectedType)) } }}>
    <label className="text-sm">Task type<select autoFocus required value={selectedType} onChange={e => setSelectedType(e.target.value)} className="mt-2 block w-full rounded border border-outline-variant bg-surface p-2"><option value="" disabled>Choose a task type</option>{taskTypes.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
    <label className="mt-4 block text-sm">Activity name (optional)<input className="mt-2 block w-full rounded border border-outline-variant bg-surface p-2" value={name} onChange={e => setName(e.target.value)} /></label>
    {needsDescription && <p className="mt-3 text-xs text-on-surface-variant">Apply to the time since {time(current!.start)}, or start now and leave earlier time unspecified.</p>}
    <div className="mt-3 flex gap-2"><button disabled={!selectedType} className={`${button} disabled:opacity-40`}>{needsDescription ? `Apply from ${time(current!.start)}` : 'Switch now'}</button>{needsDescription ? <button type="button" disabled={!selectedType} className={`${button} disabled:opacity-40`} onClick={() => startOrSwitch(name, Number(selectedType))}>Start now</button> : <button type="button" className={button} onClick={() => { setEditing(false); setName(''); setSelectedType('') }}>Cancel</button>}</div>
  </form>
  return <div className="min-h-screen bg-surface font-body text-on-surface">
    <div className="bg-on-surface px-6 py-2 text-xs text-surface flex items-center justify-between gap-4">
      <span>THROWAWAY · {roundFour ? 'Round 4: planned activity suggestion' : roundThree ? 'Round 3: describe an unspecified activity' : roundTwo ? 'Round 2: Focus entry and exit' : 'Round 1: tracking above the Day timeline'} · sample data</span>
      <div className="flex items-center gap-4"><span>Sample clock {time(now)}</span><button onClick={() => setNow(n => n + 5)}>+5 min</button><button onClick={() => { setEntries(initialEntries); setNow(initialNow); setEditing(false); setName(''); setSelectedType(''); setFocus(roundThree) }}>Reset</button></div>
    </div>
    {focus && current ? <main className="mx-auto flex min-h-[calc(100vh-40px)] max-w-5xl flex-col px-8 py-8">
      <header className="flex items-center justify-between"><span className="text-xs uppercase tracking-widest text-on-surface-variant">Focus</span><button className={button} onClick={() => { setFocus(false); setEditing(false); setName('') }}>Exit Focus</button></header>
      <section className="flex flex-1 flex-col items-center justify-center py-20 text-center" aria-label="Focused activity">
        <p className="mb-5 text-xs text-on-surface-variant">Recording · since {time(current.start)}</p>
        <h1 className="max-w-2xl font-headline text-4xl sm:text-5xl">{needsDescription ? 'What are you doing right now?' : current.name || current.taskType.name}</h1>
        <p className="mt-6 text-2xl tabular-nums text-on-surface-variant">{now-current.start} min</p>
        {!needsDescription && <button className={`${button} mt-8`} onClick={() => { setSelectedType(''); setName(''); setEditing(true) }}>Switch activity</button>}
        {(editing || needsDescription) && switchForm}
        {suggestion}
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
          {current ? <><span aria-label="Tracking active" className="h-1.5 w-1.5 rounded-full bg-actual" /><span>{current.name || current.taskType.name}</span><span className="tabular-nums">· {now-current.start} min</span><button className={button} onClick={() => { setSelectedType(''); setName(''); setEditing(true) }}>Switch</button><button className={button} onClick={() => { setEntries(old => old.map(e => e.end === null ? { ...e, end: now } : e)); setEditing(false); setName('') }}>Stop tracking</button></> : <button className={button} onClick={() => startOrSwitch()}>Start tracking</button>}
          {roundTwo && <button className={button} onClick={() => { if (!current) startOrSwitch(); setEditing(false); setName(''); setFocus(true) }}>Focus</button>}
        </div>
        {suggestion}
        {editing && switchForm}
      </section>
      <DragDropProvider><DayTimeline day={day} readOnly draft={null} selectedBlockId={null} onLaneSlotClick={() => {}} onPatchBlock={async () => {}} /></DragDropProvider>
    </main></>}
  </div>
}


