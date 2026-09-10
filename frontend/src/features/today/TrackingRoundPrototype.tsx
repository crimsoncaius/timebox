// THROWAWAY: round 1 asks whether tracking belongs above the Day timeline.
import { useState } from 'react'
import { DragDropProvider } from '@dnd-kit/react'
import { DayTimeline } from '../../components/DayTimeline'
import type { DayRead, TimeBlock } from '../../lib/api'

const date = '2026-09-10'
const stamp = `${date}T00:00:00+08:00`
const taskType = { id: 1, name: 'Work', created_at: stamp, updated_at: stamp }
const time = (minute: number) => `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`
type Entry = { name: string; start: number; end: number | null }
const planned: TimeBlock[] = [
  { id: 1, lane: 'planned', task_type_id: 1, task_type: taskType, name: 'Writing a proposal', note: null, start_minute: 600, end_minute: 720, created_at: stamp, updated_at: stamp },
  { id: 2, lane: 'planned', task_type_id: 1, task_type: taskType, name: 'Lunch', note: null, start_minute: 720, end_minute: 780, created_at: stamp, updated_at: stamp },
]

export function TrackingRoundPrototype() {
  const [now, setNow] = useState(660)
  const [entries, setEntries] = useState<Entry[]>([])
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const current = entries.find(e => e.end === null)
  function startOrSwitch() {
    const chosen = name.trim() || planned.find(p => p.start_minute <= now && p.end_minute > now)?.name || 'Unnamed activity'
    setEntries(old => [...old.map(e => e.end === null ? { ...e, end: now } : e), { name: chosen, start: now, end: null }])
    setName(''); setEditing(false)
  }
  const day: DayRead = {
    id: 1, date, start_hour: 9, end_hour: 15, show_full_day: false, created_at: stamp, updated_at: stamp,
    time_blocks: planned,
    actual_blocks: entries.map((entry, i) => ({ date, start_minute: entry.start, end_minute: entry.end ?? now,
      duration_minutes: (entry.end ?? now) - entry.start,
      actual_block: { id: 100 + i, task_type_id: 1, task_type: taskType, task_id: null, task: null,
        name: entry.name, note: null, planned_block_id: entry.name === 'Writing a proposal' ? 1 : null,
        start_at: `${date}T${time(entry.start)}:00+08:00`, end_at: entry.end === null ? null : `${date}T${time(entry.end)}:00+08:00`, created_at: stamp, updated_at: stamp },
    })),
    meta: { timezone: 'Asia/Singapore', today: date, server_now_iso: `${date}T${time(now)}:00+08:00` },
  }
  const button = 'rounded-lg border border-outline-variant px-4 py-2 text-sm hover:bg-surface-container'
  return <div className="min-h-screen bg-surface font-body text-on-surface">
    <div className="bg-on-surface px-6 py-2 text-xs text-surface flex items-center justify-between gap-4">
      <span>THROWAWAY · Round 1: tracking above the Day timeline · sample data</span>
      <div className="flex items-center gap-4"><span>Sample clock {time(now)}</span><button onClick={() => setNow(n => n + 5)}>+5 min</button><button onClick={() => { setEntries([]); setNow(660); setEditing(false); setName('') }}>Reset</button></div>
    </div>
    <aside className="fixed left-0 top-16 hidden w-64 px-8 py-8 lg:block" aria-label="Surrounding app context">
      <h1 className="font-headline text-lg uppercase tracking-widest">Timebox</h1><p className="mt-1 text-xs text-on-surface-variant">Monastic productivity</p>
      <nav className="mt-12 flex flex-col gap-6 text-sm"><strong className="border-l-4 border-primary pl-4">Day</strong><span className="pl-5">Chronicle</span><span className="pl-5">Battle Plan</span><span className="pl-5">Task types</span></nav>
    </aside>
    <main className="mx-auto max-w-6xl p-8 lg:ml-64">
      <header className="mb-8"><p className="text-xs uppercase tracking-widest text-on-surface-variant">Day</p><h1 className="mt-2 font-headline text-3xl">Thursday, September 10, 2026</h1></header>
      <section aria-label="Activity tracking" className="mb-6 rounded-xl border border-outline-variant bg-surface-container-low p-5">
        <div className="flex flex-wrap items-center justify-between gap-4"><div><p className="text-xs text-on-surface-variant">{current ? 'Recording now' : 'Tracking is off'}</p><h2 className="mt-1 text-lg font-medium">{current ? current.name : 'Start with your current plan or name an activity'}</h2>{current && <p className="mt-1 text-sm text-on-surface-variant">Since {time(current.start)} · {now-current.start} min</p>}</div>
          <div className="flex gap-2">{current ? <><button className={button} onClick={() => setEditing(true)}>Switch activity</button><button className={button} onClick={() => { setEntries(old => old.map(e => e.end === null ? { ...e, end: now } : e)); setEditing(false) }}>Stop tracking</button></> : <button className={button} onClick={() => setEditing(true)}>Start tracking</button>}</div>
        </div>
        {editing && <form className="mt-4 border-t border-outline-variant pt-4" onSubmit={e => { e.preventDefault(); startOrSwitch() }}><label className="text-sm">Activity name<input autoFocus className="mt-2 block w-full max-w-md rounded border border-outline-variant bg-surface p-2" placeholder="Writing a proposal (current plan)" value={name} onChange={e => setName(e.target.value)} /></label><p className="my-3 text-xs text-on-surface-variant">Leave empty to use the current Planned Block. Recording starts at {time(now)}.</p><div className="flex gap-2"><button className={button}>{current ? 'Switch now' : 'Start now'}</button><button type="button" className={button} onClick={() => { setEditing(false); setName('') }}>Cancel</button></div></form>}
      </section>
      <DragDropProvider><DayTimeline day={day} readOnly draft={null} selectedBlockId={null} onLaneSlotClick={() => {}} onPatchBlock={async () => {}} /></DragDropProvider>
    </main>
  </div>
}
