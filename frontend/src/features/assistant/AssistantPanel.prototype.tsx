// Three Android Assistant layouts on /assistant?variant=A|B|C.
// Question: should conversation, the answer, or the plan lead? Sample data only.
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PrototypeSwitcher } from '../../components/PrototypeSwitcher'
import './AssistantPanel.prototype.css'

const variants = [
  { key: 'A', name: 'Quiet conversation', description: 'Keep the conversation familiar. A compact header and composer give the answer more room; the plan stays inline.', tradeoff: 'Closest to the current Android screen. Long schedules still push the interpretation down.' },
  { key: 'B', name: 'Answer first', description: 'Lead with the useful conclusion. Open the supporting schedule only when you need it.', tradeoff: 'Easier to scan, but the plan takes another tap. Reopens the approved card-before-answer decision.' },
  { key: 'C', name: 'Plan + discussion', description: 'Give the plan its own space above the conversation. Collapse it when you want to focus on the discussion.', tradeoff: 'Keeps context close, but leaves less room for messages. Historical snapshots would need an explicit selector.' },
]
const rows = [
  ['09:00', '10:30', 'Draft the launch brief', 'Work / Writing'],
  ['11:00', '12:00', 'Design review', 'Work / Collaboration'],
  ['13:00', '13:45', 'Walk and lunch', 'Personal'],
  ['14:00', '15:15', 'Build the first iteration', 'Work / Development'],
  ['16:00', '16:30', 'Read and take notes', 'Learning'],
]
const prompts = ['Show today’s plan', 'Do I have a 30-minute gap?', 'Help me think through my morning']
type Scene = 'response' | 'empty' | 'interrupted' | 'empty-plan'
type Props = { scene: Scene; question: string; expanded: boolean; setExpanded: (v: boolean) => void; prompt: (v: string) => void }

function Schedule({ expanded, setExpanded, empty = false }: Pick<Props, 'expanded' | 'setExpanded'> & { empty?: boolean }) {
  return <section className="ap-plan" aria-label="Plan snapshot">
    <div className="ap-plan-title"><strong>Today’s plan</strong><span>{empty ? '0' : '5'} blocks</span></div>
    <p className="ap-meta">24 Sep 2026 · Read 08:42 · Asia/Singapore</p>
    {empty ? <p className="ap-empty-plan">No Planned Blocks for this date.</p> : <>
      <div className="ap-rows">{rows.slice(0, expanded ? 5 : 3).map(([start, end, title, type]) => <div className="ap-row" key={start}>
        <time>{start}<small>{end}</small></time><div><strong>{title}</strong><small>{type}</small></div>
      </div>)}</div>
      <button className="ap-text-button" onClick={() => setExpanded(!expanded)}>{expanded ? 'Show fewer' : 'Show all 5 blocks'} <span>{expanded ? '−' : '+'}</span></button>
    </>}
  </section>
}
function Welcome({ prompt }: Pick<Props, 'prompt'>) {
  return <div className="ap-welcome"><span className="ap-mark">✳</span><p className="ap-eyebrow">A LITTLE CLARITY</p><h2>Make room<br />for your day.</h2><p>Think through your Planned Blocks.<br />Assistant can read your plan, but can’t change it.</p><div className="ap-prompts">{prompts.map((p, i) => <button key={p} onClick={() => prompt(p)}><span>0{i + 1}</span>{p}<b>↗</b></button>)}</div></div>
}
function Answer({ empty = false }: { empty?: boolean }) {
  return <div className="ap-answer">{empty ? <p>Your plan is empty. You can add Planned Blocks from Day, then ask me to take another look.</p> : <><p>You have a <strong>30-minute opening at 10:30</strong>, between writing and your design review.</p><p>Keep it as a breather, or use it for something small. Your next longer opening is <strong>12:00–13:00</strong>.</p></>}</div>
}
export function VariantA(p: Props) {
  if (p.scene === 'empty') return <Welcome prompt={p.prompt} />
  return <><div className="ap-user">{p.question}</div><div className="ap-speaker">✳ <span>Assistant</span></div><Schedule {...p} empty={p.scene === 'empty-plan'} /><Answer empty={p.scene === 'empty-plan'} /></>
}
export function VariantB(p: Props) {
  if (p.scene === 'empty') return <Welcome prompt={p.prompt} />
  return <><div className="ap-question"><span className="ap-eyebrow">YOU ASKED</span><p>{p.question}</p></div><div className="ap-brief"><span className="ap-eyebrow">{p.scene === 'empty-plan' ? 'A FRESH START' : 'ROOM IN YOUR DAY'}</span><h2>{p.scene === 'empty-plan' ? 'An open day.' : <>30 minutes.<br />Just for you.</>}</h2>{p.scene !== 'empty-plan' && <span className="ap-time-chip">10:30–11:00</span>}<Answer empty={p.scene === 'empty-plan'} /></div><details className="ap-evidence"><summary>See the plan behind this answer <span>↗</span></summary><Schedule {...p} empty={p.scene === 'empty-plan'} /></details></>
}
export function VariantC(p: Props) {
  if (p.scene === 'empty') return <Welcome prompt={p.prompt} />
  return <><details className="ap-pinned" open><summary>Plan reference <span>24 Sep · 08:42</span></summary><Schedule {...p} empty={p.scene === 'empty-plan'} /></details><div className="ap-discussion"><span className="ap-eyebrow">DISCUSSION</span><div className="ap-user">{p.question}</div><Answer empty={p.scene === 'empty-plan'} /></div></>
}
export function AssistantPanelPrototype() {
  const [params] = useSearchParams()
  const variant = variants.find(v => v.key === params.get('variant')) ?? variants[0]
  const [scene, setScene] = useState<Scene>('response')
  const [dark, setDark] = useState(false)
  const [large, setLarge] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [info, setInfo] = useState(false)
  const [draft, setDraft] = useState('')
  const [question, setQuestion] = useState(prompts[1])
  const [busy, setBusy] = useState(false)
  const [demo, setDemo] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  const scroll = useRef<HTMLDivElement>(null)
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])
  function reset(next: Scene) { if (timer.current) clearTimeout(timer.current); setBusy(false); setScene(next); setExpanded(false); setDemo(false); setDraft('') }
  function send() {
    if (!draft.trim()) return
    setQuestion(draft.trim()); setDraft(''); setScene('response'); setBusy(true); setDemo(true)
    timer.current = setTimeout(() => { setBusy(false); scroll.current?.scrollTo({ top: scroll.current.scrollHeight, behavior: 'smooth' }) }, 1600)
  }
  const p: Props = { scene, question, expanded, setExpanded, prompt: v => { setDraft(v); input.current?.focus() } }
  const Component = variant.key === 'B' ? VariantB : variant.key === 'C' ? VariantC : VariantA
  return <main className="ap-lab">
    <aside className="ap-notes"><p className="ap-eyebrow">TIMEBOX / DESIGN STUDY</p><h1>A calmer<br />Assistant.</h1><p className="ap-intro">Three ways to make a conversation about your day feel more useful.</p><div className="ap-direction"><span>{variant.key}</span><div><h2>{variant.name}</h2><p>{variant.description}</p></div></div><p className="ap-tradeoff">{variant.tradeoff}</p>
      <fieldset><legend>Try a moment</legend><div className="ap-scenarios">{(['response', 'empty', 'interrupted', 'empty-plan'] as Scene[]).map(s => <button key={s} aria-pressed={scene === s} onClick={() => reset(s)}>{({ response: 'Plan response', empty: 'Start fresh', interrupted: 'Interrupted', 'empty-plan': 'Empty plan' })[s]}</button>)}</div></fieldset>
      <div className="ap-options"><label><input type="checkbox" checked={dark} onChange={e => setDark(e.target.checked)} /> Dark theme</label><label><input type="checkbox" checked={large} onChange={e => setLarge(e.target.checked)} /> Larger text</label></div>
      <p className="ap-footnote">Android layout study · sample data<br />Prompts and Send simulate a response. No model calls or saved changes. Native keyboard and TalkBack still need Android validation.</p>
      <details className="ap-state"><summary>Prototype state</summary><pre>{JSON.stringify({ variant: variant.key, scene, dark, largeText: large, expanded, busy, question, draft, data: 'fixed sample; 24 Sep 2026, Asia/Singapore', plan: rows }, null, 2)}</pre></details>
    </aside>
    <section className={`ap-phone ${dark ? 'ap-dark' : ''} ${large ? 'ap-large' : ''}`} aria-label="Android Assistant preview">
      <div className="ap-status"><span>08:42</span><span>● ▰</span></div>
      <header className="ap-header"><div><h2>Assistant</h2><button className="ap-session" onClick={() => setInfo(!info)} aria-expanded={info}>Temporary conversation ⓘ</button></div><button className="ap-new" aria-label="New conversation" onClick={() => reset('empty')}>＋</button></header>
      {info && <p className="ap-session-info">Clears after 60 minutes of inactivity, 20 completed exchanges, or an app restart. Read-only access to your Planned Blocks.</p>}
      <div className={`ap-scroll ap-variant-${variant.key}`} ref={scroll}>
        {busy ? <><div className="ap-user">{question}</div><p className="ap-loading" role="status">✳ Reading today’s plan…</p></> : <Component {...p} />}
        {demo && !busy && <p className="ap-demo-note">Sample response for layout review; not generated from your message.</p>}
        {scene === 'interrupted' && <div className="ap-interrupted"><strong>Response interrupted</strong><p>This attempt won’t be used in later answers.</p><button onClick={() => { setDraft(question); input.current?.focus() }}>Retry response</button></div>}
        {!busy && scene !== 'empty' && <button className="ap-followup" onClick={() => p.prompt('Help me think through my morning')}>Think through my morning ↗</button>}
      </div>
      <form className="ap-composer" onSubmit={e => { e.preventDefault(); send() }}><label className="ap-sr-only" htmlFor="ap-draft">Message Assistant</label><textarea id="ap-draft" ref={input} value={draft} onChange={e => setDraft(e.target.value)} maxLength={4000} placeholder={scene === 'empty' ? 'What’s on your mind?' : 'Ask a follow-up…'} rows={2} />{busy ? <button type="button" aria-label="Stop response" onClick={() => { if (timer.current) clearTimeout(timer.current); setBusy(false); setScene('interrupted') }}>■</button> : <button type="submit" aria-label="Send message" disabled={!draft.trim()}>↑</button>}</form>
      <nav className="ap-bottom" aria-label="Android navigation context"><span>▦<small>Day</small></span><span>☷<small>Battle Plan</small></span><span>◷<small>Chronicle</small></span><span className="active">✳<small>Assistant</small></span></nav><div className="ap-gesture" />
    </section>
    <PrototypeSwitcher variants={variants} />
  </main>
}
