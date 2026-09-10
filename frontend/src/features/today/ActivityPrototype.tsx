// THROWAWAY: three activity / inactivity layouts on /day/:date?variant=A|B|C.
// Fixtures and all mutations are in memory. No real records, permissions, or wake locks.
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import './ActivityPrototype.css'
type RecordRow = { name: string; start: number; end: number }
const names = ['A', 'B', 'C']
const labels = ['Inline question', 'Side companion', 'Bottom dock']
const clock = (n: number) => `${String(Math.floor(n / 60) % 24).padStart(2, '0')}:${String(n % 60).padStart(2, '0')}`
export function ActivityPrototype() {
  const [params, setParams] = useSearchParams()
  const variant = names.includes(params.get('variant') || '') ? params.get('variant')! : 'A'
  const [now, setNow] = useState(675)
  const [activity, setActivity] = useState<string | null>('Writing a proposal')
  const [start, setStart] = useState(600)
  const [rows, setRows] = useState<RecordRow[]>([{ name: 'Morning walk', start: 540, end: 570 }, { name: 'Breakfast', start: 570, end: 600 }])
  const [focus, setFocus] = useState(false)
  const [planning, setPlanning] = useState(false)
  const [pending, setPending] = useState(true)
  const [offline, setOffline] = useState(false)
  const [notice, setNotice] = useState('')
  const [editor, setEditor] = useState(false)
  const [draft, setDraft] = useState('')
  const [from, setFrom] = useState('11:15')
  const [wake, setWake] = useState(true)
  const [view, setView] = useState('Day')
  const [dark, setDark] = useState(false)
  const [mobile, setMobile] = useState(false)
  const [threshold, setThreshold] = useState(60)
  const [enabled, setEnabled] = useState(true)
  const [details, setDetails] = useState(false)
  function cycle(delta: number) { setParams({ variant: names[(names.indexOf(variant) + delta + 3) % 3] }, { replace: true }) }
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement
      if (el.closest('input,textarea,select,[contenteditable=true]')) return
      if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.preventDefault(); cycle(e.key === 'ArrowLeft' ? -1 : 1) }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })
  function switchTo(name: string, at = now) {
    if (activity !== null && at > start) setRows(r => [...r, { name: activity || 'Unnamed activity', start, end: at }])
    setActivity(name); setStart(at); setPending(false); setEditor(false)
    setNotice(offline ? 'Saved on this device · waiting to sync' : 'Activity updated')
  }
  function stop(at = now) {
    if (activity !== null && at > start) setRows(r => [...r, { name: activity || 'Unnamed activity', start, end: at }])
    setActivity(null); setPending(false); setFocus(false); setEditor(false)
  }
  function enterFocus() {
    if (planning) return
    if (activity === null) switchTo(now < 720 ? 'Writing a proposal' : '')
    setFocus(true)
  }
  function edit() { setDraft(''); setFrom(clock(now)); setEditor(true) }
  function apply() {
    const [h, m] = from.split(':').map(Number)
    const at = h * 60 + m
    if (!Number.isFinite(at) || at < start || at > now) { setNotice('Choose a time within the current activity.'); return }
    switchTo(draft.trim() || (now < 720 ? 'Writing a proposal' : ''), at)
  }
  function scenario(key: string) {
    setEditor(false); setNotice(''); setPlanning(false); setView('Day'); setPending(false); setOffline(false)
    setRows([{ name: 'Morning walk', start: 540, end: 570 }, { name: 'Breakfast', start: 570, end: 600 }])
    setNow(675); setStart(600); setFocus(false); setActivity('Writing a proposal')
    if (key === 'prompt') setPending(true)
    if (key === 'unnamed') { setNow(810); setStart(800); setActivity(''); setFocus(true) }
    if (key === 'late') { setNow(735); setDraft('Lunch'); setFrom('12:00'); setEditor(true) }
    if (key === 'plan') setPlanning(true)
    if (key === 'offline') { setOffline(true); setNotice('Offline Lunch change at 12:00 is pending. Simulate sync to receive newer Reading change at 12:10.'); setNow(750); setStart(720); setActivity('Lunch') }
  }
  const question = <section className="ap-question" aria-label="Inactivity prompt">
    <span className="ap-eyebrow">A moment to check</span><h2>Still {activity ? `doing ${activity.toLowerCase()}` : 'on this activity'}?</h2>
    <p>Your device has been quiet. Your time is still being recorded.</p>
    <div className="ap-actions"><button className="ap-primary" onClick={() => { setPending(false); setNotice('Confirmed · a fresh inactivity interval starts now') }}>Still doing this</button><button onClick={edit}>Switch activity</button>{!focus && <button onClick={() => { setDraft(''); setFrom(clock(now)); setEditor(true); setNotice('Choose an end time, then Stop at this time.') }}>Stop tracking…</button>}</div>
  </section>
  const current = <section className="ap-current"><span className="ap-eyebrow">{activity === null ? 'Tracking is off' : '● Current activity'}</span>
    <h1>{activity === null ? 'Make room for your day.' : activity || 'What are you doing right now?'}</h1>
    {activity !== null && <p className="ap-duration">{Math.floor((now - start) / 60)}<small>h</small> {String((now - start) % 60).padStart(2, '0')}<small>m</small><span>Since {clock(start)}</span></p>}
    {activity === '' && <form className="ap-naming" onSubmit={e => { e.preventDefault(); setActivity(draft.trim()); setDraft('') }}><label>Current activity<input value={draft} onChange={e => setDraft(e.target.value)} placeholder="e.g. Reading" required /></label><button className="ap-primary" disabled={!draft.trim()}>Name from {clock(start)}</button><button type="button" disabled={!draft.trim()} onClick={() => { switchTo(draft.trim()); setDraft('') }}>Start now</button></form>}
    <div className="ap-actions">{activity === null ? <><button className="ap-primary" onClick={() => switchTo(now < 720 ? 'Writing a proposal' : '')}>Start tracking</button><button onClick={edit}>Choose activity</button></> : <><button onClick={edit}>Switch activity</button>{!focus && <button onClick={() => stop()}>Stop tracking</button>}</>}{!focus && <button className="ap-primary" disabled={planning} title={planning ? 'Leave planning to enter Focus' : ''} onClick={enterFocus}>Enter Focus</button>}</div>
    {planning && <p>Finish planning before entering Focus. Tracking continues.</p>}
    {activity === 'Writing a proposal' && <details><summary>Proposal · Task details</summary><p>Prepare a clear first draft for the client.</p><label><input type="checkbox" /> Outline recommendations</label><label><input type="checkbox" /> Draft the introduction</label></details>}
  </section>
  return <div className={`ap-root ${dark ? 'ap-dark' : ''} ${mobile ? 'ap-mobile' : ''}`}>
    <div className="ap-lab"><strong>THROWAWAY PROTOTYPE</strong><span>In-memory sample day · signals simulated</span><button onClick={() => setMobile(!mobile)}>{mobile ? 'Wide view' : 'Phone width'}</button><button onClick={() => setDark(!dark)}>{dark ? 'Light' : 'Dark'}</button><button onClick={() => setDetails(!details)}>Scenarios & state</button></div>
    {details && <section className="ap-debug"><div className="ap-actions">{[['prompt','Inactivity'],['unnamed','Unnamed Focus'],['late','Late switch'],['plan','Planning'],['offline','Offline conflict']].map(([k,l]) => <button key={k} onClick={() => scenario(k)}>{l}</button>)}<button onClick={() => setNow(n => n + 15)}>+15 min</button><button disabled={activity === null || !enabled || pending} onClick={() => setPending(true)}>Simulate inactivity</button><button disabled={!offline} onClick={() => { setOffline(false); switchTo('Reading', 730); setNotice('Activity updated from another device · newer change at 12:10 wins'); }}>Sync remote Reading</button><button onClick={() => { stop(); setNotice('Tracking stopped on another device') }}>Remote stop</button><button onClick={() => setNotice('Return simulated · Focus and recorded start preserved; display wake requested if enabled')}>Simulate return</button></div><pre>{JSON.stringify({ variant, now: clock(now), activity, start: clock(start), focus, planning, pending, offline, threshold, wakeRequested: focus && wake, reportingZone: 'Asia/Singapore', records: rows }, null, 2)}</pre></section>}
    <div className={`ap-frame ap-${variant} ${focus ? 'ap-focused' : ''}`}>
      {!focus && <aside className="ap-nav"><div className="ap-brand">TIMEBOX<small>Make time visible.</small></div><nav>{['Day','Battle Plan','Chronicle','Settings'].map(n => <button key={n} className={view === n ? 'selected' : ''} onClick={() => setView(n)}>{n}</button>)}</nav><span className="ap-nav-foot">Thursday, 10 September</span></aside>}
      <main><header className="ap-header"><span>{focus ? 'FOCUS' : view === 'Day' ? 'THURSDAY / 10 SEPTEMBER' : view}</span><div>{offline && <span>● Unsynced</span>}<time>{clock(now)}</time>{focus ? <button onClick={() => setFocus(false)}>Exit Focus</button> : <button onClick={() => { setPlanning(!planning); setView('Day') }}>{planning ? 'Finish planning' : 'Plan day'}</button>}</div></header>
        {notice && <p className="ap-notice" role="status">{notice}</p>}
        {!focus && view === 'Settings' ? <section className="ap-settings"><h1>Your tracking preferences</h1><label><input type="checkbox" checked={enabled} onChange={e => { setEnabled(e.target.checked); if (!e.target.checked) setPending(false) }} /> Inactivity prompts</label><label>Ask after {threshold} minutes<input type="range" min="15" max="480" step="15" value={threshold} onChange={e => setThreshold(Number(e.target.value))} /></label><label><input type="checkbox" checked={wake} onChange={e => setWake(e.target.checked)} /> Keep display awake in Focus</label><p>Device detection is approximate. Unsupported or denied detection disables prompts on that device. Screen wake depends on platform support.</p><label>Reporting time zone<select defaultValue="Asia/Singapore"><option>Asia/Singapore</option></select></label></section> : !focus && view === 'Battle Plan' ? <section className="ap-settings"><h1>Ready when you are.</h1><p>Pick a Task without scheduling it first.</p><button onClick={() => { switchTo('Read a chapter'); setView('Day') }}>Track “Read a chapter”</button></section> : <>
        <div className="ap-workspace"><div className="ap-primary-column">{current}{pending && activity !== null && variant === 'A' && question}
          {now >= 720 && activity !== 'Lunch' && <section className="ap-suggestion"><span>Lunch is planned now.</span><button onClick={() => switchTo('Lunch')}>Switch to Lunch</button></section>}
          {!focus && <section className="ap-timeline"><div className="ap-section-heading"><h2>Your day</h2><span>Plan / Actual</span></div><div className="ap-plan"><span>10:00–12:00</span><p>Writing a proposal <small>Planned</small></p></div>{rows.map((r,i) => <div className="ap-record" key={i}><span>{clock(r.start)}–{clock(r.end)}</span><p>{r.name}<small>{r.end-r.start} min recorded</small></p></div>)}{activity !== null && <div className="ap-record ap-live"><span>{clock(start)}–now</span><p>{activity || 'Unnamed activity'}<small>Recording · {now-start} min</small></p></div>}</section>}
        </div>{pending && activity !== null && variant === 'B' && <aside className="ap-companion">{question}</aside>}</div>
        {focus && <footer className="ap-focus-foot"><span>{wake ? 'Display wake requested · simulated' : 'Display wake off'}</span><span>Your activity continues when you exit.</span></footer>}
        {pending && activity !== null && variant === 'C' && <div className="ap-dock">{question}</div>}
        </>}
        {editor && <section className="ap-editor"><div className="ap-section-heading"><h2>{activity === null ? 'Start an activity' : 'Change your activity'}</h2><button onClick={() => setEditor(false)}>Cancel</button></div><label>Activity<input value={draft} onChange={e => setDraft(e.target.value)} placeholder="Empty uses the current plan" /></label><label>From<input type="time" value={from} onChange={e => setFrom(e.target.value)} /></label><p>{activity !== null ? `${activity || 'Unnamed activity'} will end at ${from}. The next activity starts there.` : 'Time before this start stays unrecorded.'}</p><div className="ap-actions"><button className="ap-primary" onClick={apply}>Apply change</button>{!focus && activity !== null && <button onClick={() => { const [h,m] = from.split(':').map(Number); const at=h*60+m; if(at>=start && at<=now) stop(at); else setNotice('Choose a time within the current activity.') }}>Stop at this time</button>}</div></section>}
      </main>
    </div>
    <div className="ap-switcher"><button aria-label="Previous variant" onClick={() => cycle(-1)}>←</button><span>{variant} — {labels[names.indexOf(variant)]}</span><button aria-label="Next variant" onClick={() => cycle(1)}>→</button></div>
  </div>
}