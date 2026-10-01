import { useState } from 'react'
import { Layout } from '../../components/Layout'
import { ActivitySwitchDialog } from './ActivitySwitchDialog'
import './planNowPrototype.css'

// Review-only fixture. All mutations and the review clock stay in component state.
type Block = { name: string; start: number; end: number }
const initialPlans: Block[] = [
  { name: 'Writing', start: 600, end: 660 },
  { name: 'Meeting', start: 660, end: 690 },
]
const clock = (minute: number) => `${Math.floor(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}`

export default function PlanNowPrototype() {
  const [plans, setPlans] = useState(initialPlans)
  const [records, setRecords] = useState<Block[]>([])
  const [now, setNow] = useState(620)
  const [activity, setActivity] = useState('Writing')
  const [started, setStarted] = useState(600)
  const [plannedEnd, setPlannedEnd] = useState<number | null>(660)
  const [plannedStart, setPlannedStart] = useState(600)
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('Email')
  const [duration, setDuration] = useState<number | 'custom' | null>(null)
  const [custom, setCustom] = useState('45')
  const [focus, setFocus] = useState(false)
  const [confirmReplacement, setConfirmReplacement] = useState(false)

  const minutes = duration === 'custom' ? Number(custom) : duration
  const valid = minutes === null || (Number.isInteger(minutes) && minutes > 0 && minutes <= 90)
  const end = now + (minutes ?? 0)
  const affected = minutes && valid ? plans.filter(p => p.start < end && p.end > now) : []
  const laterPlans = affected.filter(p => p.start > now)
  const resumes = affected.find(p => p.end > end)
  const remaining = plannedEnd === null ? null : plannedEnd - now
  const currentPlan = plans.find(p => p.start <= now && p.end > now)
  const elapsed = now - started
  const afterPlans = minutes && valid ? [
    ...plans.flatMap(p => p.end <= now || p.start >= end ? [p] : [
      ...(p.start < now ? [{ ...p, end: now }] : []),
      ...(p.end > end ? [{ ...p, start: end }] : []),
    ]), { name: name.trim() || 'Email', start: now, end },
  ].sort((a, b) => a.start - b.start) : plans

  function reset() {
    setPlans(initialPlans); setRecords([]); setNow(620); setActivity('Writing'); setStarted(600)
    setPlannedStart(600); setPlannedEnd(660); setEditing(false); setFocus(false)
    setName('Email'); setDuration(null); setConfirmReplacement(false)
  }
  function commit() {
    setRecords([...records, { name: activity, start: started, end: now }])
    setPlans(afterPlans); setActivity(name.trim()); setStarted(now)
    setPlannedStart(now); setPlannedEnd(minutes === null ? null : end)
    setEditing(false); setConfirmReplacement(false)
  }
  function submit() {
    if (laterPlans.length) { setConfirmReplacement(true) }
    else commit()
  }
  const review = <aside className="pn-review" aria-label="Prototype controls">
    <span><strong>Prototype #297</strong> · Sample day · {clock(now)}</span>
    <div><button disabled={now >= 710} onClick={() => setNow(now + 5)}>Advance 5 min</button><button onClick={reset}>Reset scenario</button></div>
  </aside>
  const progress = remaining !== null ? <div className="pn-progress">
    <p className={focus ? 'pn-countdown' : 'pn-remaining'}>{remaining > 0 ? `${remaining} min remaining` : remaining === 0 ? 'Planned time finished' : `${-remaining} min over`}</p>
    <progress aria-label="Planned time progress" max={Math.max(1, plannedEnd! - plannedStart)} value={Math.min(plannedEnd! - plannedStart, Math.max(0, now - plannedStart))} />
    <p>{clock(plannedStart)}–{clock(plannedEnd!)} · {plannedEnd! - plannedStart} min planned</p>
  </div> : <p>Open-ended activity</p>
  const handoff = remaining !== null && remaining <= 0 ? <section className="pn-handoff" aria-live="polite">
    <p>{activity} is still recording.{currentPlan && currentPlan.name !== activity ? ` ${currentPlan.name} is planned now.` : ' Switch when you are ready.'}</p>
    {currentPlan && currentPlan.name !== activity && <button className="pn-primary" onClick={() => {
      setRecords([...records, { name: activity, start: started, end: now }]); setActivity(currentPlan.name)
      setStarted(now); setPlannedStart(currentPlan.start); setPlannedEnd(currentPlan.end)
    }}>Switch to {currentPlan.name}</button>}
  </section> : null
  const dialog = editing ? <ActivitySwitchDialog open onClose={() => { setEditing(false); setConfirmReplacement(false) }}>
    {confirmReplacement ? <div className="pn-form">
      <h3 className="pn-subtitle">Replace this planned time?</h3>
      <p>{name} will take {clock(now)}–{clock(end)}.</p>
      <ul>{affected.map(p => <li key={p.start}>{p.name}: {clock(Math.max(now, p.start))}–{clock(Math.min(end, p.end))} replaced</li>)}</ul>
      <p>{resumes ? `${resumes.name} resumes at ${clock(end)}.` : 'Plans after this interval stay in place.'}</p>
      <div className="pn-actions"><button onClick={() => setConfirmReplacement(false)}>Back</button><button className="pn-primary" onClick={() => commit()}>Replace this time</button></div>
    </div> : <form className="pn-form" onSubmit={e => { e.preventDefault(); submit() }}>
      <label>Activity<select value={name} onChange={e => setName(e.target.value)}><option>Email</option><option>Reading</option><option>Break</option></select></label>
      <fieldset><legend>How long from now?</legend><div className="pn-chips">
        {([null, 15, 30, 60, 90, 'custom'] as const).map(value => <button type="button" key={String(value)} aria-pressed={duration === value} onClick={() => setDuration(value)}>{value === null ? 'Open-ended' : value === 'custom' ? 'Custom' : `${value} min`}</button>)}
      </div></fieldset>
      {duration === 'custom' && <label>Minutes<input type="number" min="1" max="90" step="1" value={custom} onChange={e => setCustom(e.target.value)} />{!valid && <span role="alert">Choose 1–90 whole minutes for this prototype.</span>}</label>}
      <section className="pn-preview" aria-label="Plan preview" aria-live="polite">
        <h3>After switching · {clock(now)}</h3>
        {minutes === null ? <p>{name} starts recording. Your plan stays as it is.</p> : valid ? <>
          <p><strong>{name} until {clock(end)}</strong></p>
          <p>{resumes ? `${resumes.name} resumes at ${clock(end)}, until ${clock(resumes.end)}.` : 'Nothing else is moved later.'}</p>
          <ol>{afterPlans.filter(p => p.end > now).map(p => <li key={p.start}><span>{clock(Math.max(now, p.start))}–{clock(p.end)}</span><strong>{p.name}</strong></li>)}</ol>
          {laterPlans.length > 0 && <p className="pn-warning">This replaces time in {laterPlans.map(p => p.name).join(', ')}. Review before switching.</p>}
        </> : <p>Enter a duration to preview your plan.</p>}
      </section>
      <div className="pn-actions"><button type="button" onClick={() => setEditing(false)}>Cancel</button><button className="pn-primary" disabled={!valid}>Switch</button></div>
    </form>}
  </ActivitySwitchDialog> : null
  if (focus) return <div className="pn-root pn-focus">{review}<button className="pn-exit" onClick={() => setFocus(false)}>Exit Focus</button><main>
    <h1>{activity}</h1>{progress}<p className="pn-running">Running Time · {elapsed} min</p>{handoff}
    <button onClick={() => setEditing(true)}>Switch activity</button>
  </main>{dialog}</div>
  return <Layout><div className="pn-root">{review}
    <section className="pn-tracking" aria-label="Activity tracking"><div><span className="pn-recording">Recording</span><strong>{activity}</strong><span>{elapsed} min</span></div><div><button onClick={() => setEditing(true)}>Switch</button><button onClick={() => setFocus(true)}>Focus</button></div></section>
    <div className="pn-heading"><h1>Monday, 28 September</h1><p>A little room to change your plan.</p></div>
    {handoff}
    <div className="pn-day"><section aria-label="Day timeline" className="pn-timeline">
      <div className="pn-lane-head"><span /><h2>Planned</h2><h2>Actual</h2></div>
      <div className="pn-grid"><div className="pn-times">{[600, 630, 660, 690, 720].map(t => <span key={t} style={{ top: (t - 600) * 4 }}>{clock(t)}</span>)}</div>
        <div className="pn-lane pn-planned">{plans.map(p => <div className="pn-block" key={p.start} style={{ top: (p.start - 600) * 4, height: (p.end - p.start) * 4 }}><strong>{p.name}</strong><span>{clock(p.start)}–{clock(p.end)}</span></div>)}</div>
        <div className="pn-lane pn-actual">{[...records, { name: activity, start: started, end: now }].filter(p => p.end > p.start).map((p, i) => <div className="pn-block" key={i} style={{ top: (p.start - 600) * 4, height: (p.end - p.start) * 4 }}><strong>{p.name}</strong><span>{clock(p.start)}–{clock(p.end)}</span></div>)}</div>
        {now <= 720 && <div className="pn-now" style={{ top: (now - 600) * 4 }}><span>Now {clock(now)}</span></div>}
      </div>
    </section><section className="pn-summary"><h2>{activity}</h2>{progress}<p className="pn-muted">Your plan can change. Recording continues until you switch.</p></section></div>
    {dialog}
  </div></Layout>
}
