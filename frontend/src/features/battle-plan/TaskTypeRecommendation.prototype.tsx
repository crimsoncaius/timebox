// THROWAWAY: Three Android recommendation placements on /battle-plan?prototype=task-type-168&variant=A.
// Browser facsimile of the native sheets; all data and provider results are in-memory fixtures.
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import './TaskTypeRecommendation.prototype.css'

const variants = ['A', 'B', 'C'] as const
type Variant = typeof variants[number]
const titles = { A: 'At the moment of naming', B: 'Back in the details', C: 'Name and type together' }
const descriptions = {
  A: 'Keep the focused name editor. Offer a compact recommendation directly below the name before returning to the details.',
  B: 'Keep the name editor quiet. Carry the recommendation back to the Task Type row after the name is saved.',
  C: 'Make identity one editing step. Name and Task Type share a sheet, with the recommendation attached to its destination.',
}
const categories = ['health / exercise', 'learning / music', 'work / writing', 'home / errands']
type Outcome = 'match' | 'low' | 'unavailable' | 'no-match' | 'limit'
type Entity = 'Battle Plan Task' | 'Planned Block' | 'Actual Block' | 'Recurring Task Series' | 'Session Task'
const entities: Entity[] = ['Battle Plan Task', 'Planned Block', 'Actual Block', 'Recurring Task Series', 'Session Task']

export default function RecommendationPrototype() {
  const [params, setParams] = useSearchParams()
  const raw = params.get('variant')
  const variant: Variant = raw === 'B' || raw === 'C' ? raw : 'A'
  const [revision, setRevision] = useState(0)
  function changeVariant(next: Variant) { setParams({ prototype: 'task-type-168', variant: next }, { replace: true }) }
  useEffect(() => {
    function keydown(event: KeyboardEvent) {
      if ((event.target as HTMLElement).closest('input, textarea, select, [contenteditable]')) return
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
      event.preventDefault()
      const direction = event.key === 'ArrowRight' ? 1 : -1
      setParams({ prototype: 'task-type-168', variant: variants[(variants.indexOf(variant) + direction + 3) % 3] }, { replace: true })
    }
    window.addEventListener('keydown', keydown)
    return () => window.removeEventListener('keydown', keydown)
  }, [variant, setParams])
  return <div className="rec-prototype">
    <header className="rec-heading"><a href="/battle-plan?prototype=task-type-168&variant=A">TIMEBOX <span>/ INTERACTION LAB</span></a><span>168 · THROWAWAY PROTOTYPE</span></header>
    <PrototypeVariant key={`${variant}-${revision}`} variant={variant} onReset={() => setRevision(revision + 1)} />
    {import.meta.env.DEV && <nav className="rec-switcher" aria-label="Prototype variants"><button aria-label="Previous variant" onClick={() => changeVariant(variants[(variants.indexOf(variant) + 2) % 3])}>←</button><div><small>COMPARE PLACEMENTS · {variants.indexOf(variant) + 1} / 3</small><strong>{variant} — {titles[variant]}</strong></div><button aria-label="Next variant" onClick={() => changeVariant(variants[(variants.indexOf(variant) + 1) % 3])}>→</button></nav>}
  </div>
}

function PrototypeVariant({ variant, onReset }: { variant: Variant; onReset: () => void }) {
  const [entity, setEntity] = useState<Entity>('Battle Plan Task')
  const [outcome, setOutcome] = useState<Outcome>('match')
  const [name, setName] = useState('Practise piano')
  const [draft, setDraft] = useState('Practise piano')
  const [type, setType] = useState('')
  const [draftType, setDraftType] = useState('')
  const [explicit, setExplicit] = useState(false)
  const [draftExplicit, setDraftExplicit] = useState(false)
  const [editing, setEditing] = useState(false)
  const [picker, setPicker] = useState(false)
  const [dirtyName, setDirtyName] = useState(false)
  const [dismissed, setDismissed] = useState(false)
  const [response, setResponse] = useState<{ name: string; outcome: Outcome; category: string } | null>(null)
  const [linked, setLinked] = useState(false)
  const [events, setEvents] = useState<string[]>(['Opened unclassified work. No request.'])
  const currentName = editing ? draft : name
  const currentType = editing ? draftType : type
  const chosen = editing ? draftExplicit : explicit
  const locked = entity === 'Actual Block' && linked
  const eligible = dirtyName && !!currentName.trim() && !chosen && !currentType && !dismissed && !locked
  const fresh = response?.name === currentName && response?.outcome === outcome
  const suggestion = eligible && fresh && outcome === 'match' ? response.category : null
  const pending = eligible && !fresh
  function record(message: string) { setEvents(previous => [message, ...previous].slice(0, 5)) }
  useEffect(() => {
    if (!eligible) return
    const timer = window.setTimeout(() => {
      const lower = currentName.toLowerCase()
      const category = /run|gym|walk|exercise/.test(lower) ? categories[0] : /piano|music|guitar/.test(lower) ? categories[1] : /shop|grocer|errand/.test(lower) ? categories[3] : categories[2]
      setResponse({ name: currentName, outcome, category })
      setEvents(previous => [`${outcome === 'match' ? 'Recommendation ready (0.93 confidence)' : `No recommendation: ${outcome}`}.`, ...previous].slice(0, 5))
    }, 500)
    return () => window.clearTimeout(timer)
  }, [currentName, eligible, outcome])
  function editName() {
    setDraft(name); setDraftType(type); setDraftExplicit(explicit); setEditing(true); setPicker(false)
    record(variant === 'C' ? 'Opened name and type editor.' : 'Opened name editor.')
  }
  function changeName(value: string) { setDraft(value); setDirtyName(true); setDismissed(false); setResponse(null) }
  function saveName() { setName(draft); setType(draftType); setExplicit(draftExplicit); setEditing(false); setPicker(false); record('Saved name and any accepted Task Type.') }
  function discard() { setDraft(name); setEditing(false); setPicker(false); setDirtyName(false); setResponse(null); setDismissed(false); record('Discarded edit and recommendation.') }
  function choose(category: string, source = 'Manually selected') {
    if (editing) { setDraftType(category); setDraftExplicit(true) } else { setType(category); setExplicit(true) }
    setPicker(false); record(`${source}: ${category || 'Unset'}. Further recommendations suppressed.`)
  }
  function dismiss() { setDismissed(true); record('Dismissed until the name changes.') }
  function resetContext(next: Entity) {
    setEntity(next); setName('Practise piano'); setDraft('Practise piano'); setType(''); setDraftType(''); setExplicit(false); setDraftExplicit(false); setEditing(false); setPicker(false); setDirtyName(false); setResponse(null); setDismissed(false); setLinked(false); setEvents(['Opened unclassified work. No request.'])
  }
  const recommendation = suggestion && <div className={`rec-suggestion rec-suggestion-${variant}`}>
    <button onClick={() => choose(suggestion, 'Accepted recommendation')}><span className="rec-spark">✧</span><span><small>Suggested Task Type</small><strong>{suggestion}</strong></span><span className="rec-use">Use</span></button>
    <button className="rec-dismiss" aria-label="Dismiss recommendation" onClick={dismiss}>×</button>
  </div>
  const pickerContent = <div className="rec-picker"><h3>Task Type</h3><input aria-label="Search Task Types" placeholder="Find a Task Type…" onChange={event => {
    const parent = event.currentTarget.parentElement
    parent?.querySelectorAll<HTMLButtonElement>('[data-category]').forEach(button => { button.hidden = !button.dataset.category?.includes(event.target.value.toLowerCase()) })
  }} />{categories.map(category => <button data-category={category} key={category} onClick={() => choose(category)}>{category}<span>＋</span></button>)}<button onClick={() => choose('')}>Clear Task Type<span>−</span></button><button onClick={() => setPicker(false)}>Back</button></div>
  const typeRow = <button className="rec-field" disabled={locked} onClick={() => setPicker(true)}><span className="rec-glyph">◇</span><span><small>Task Type</small><strong>{locked ? 'learning / music' : currentType || (entity.includes('Block') ? 'unspecified' : 'Unset')}</strong></span><span>›</span></button>
  return <main className="rec-workspace">
    <section className="rec-explanation"><div className="rec-eyebrow">THE DESIGN QUESTION</div><h1>Where should a<br />suggestion meet you?</h1><p className="rec-lede">A name is enough to offer a Task Type. The decision stays yours.</p><div className="rec-variant-copy"><span className="rec-letter">{variant}</span><div><h2>{titles[variant]}</h2><p>{descriptions[variant]}</p></div></div>
      <div className="rec-instructions"><h3>Try the flow</h3><ol><li>Tap the name to edit it.</li><li>Change it to “Practise guitar”.</li><li>Pause, then accept, ignore, or discard.</li></ol><p>{variant === 'B' ? 'Save the name to find the suggestion in the details.' : variant === 'C' ? 'Compare manual selection and the suggestion in one place.' : 'Does a second decision interrupt writing the name?'}</p></div>
      <p className="rec-footnote">Browser facsimile of Android · fixture data · no API calls or saved changes. This explores placement, not native keyboard behavior.</p>
    </section>
    <section className="rec-device-stage" aria-label="Android preview"><div className="rec-phone"><div className="rec-status"><b>9:41</b><span>● ▰</span></div><div className="rec-app-background"><small>TIMEBOX</small><h2>{entity.includes('Block') ? 'Day' : 'Battle Plan'}</h2><div className="rec-tabs"><b>All tasks</b><span>Projects</span><span>Recurring</span></div><p>READY TO PLAN <span>3</span></p><div className="rec-background-task">Review the launch brief <small>work / writing</small></div><div className="rec-background-task">Practise piano <small>Unset</small></div></div>
      <div className="rec-sheet"><div className="rec-handle" /><div className="rec-sheet-top"><span>{editing ? variant === 'C' ? 'Name & Task Type' : entity.includes('Block') ? 'Block Name' : 'Name' : entity}</span>{editing ? <button onClick={discard}>Cancel</button> : <span className="rec-context-dot">●</span>}</div>
        <div className="rec-sheet-body">
        {picker ? pickerContent : editing ? <>
          <label className="rec-name-label" htmlFor="rec-name">{entity.includes('Block') ? 'BLOCK NAME' : 'NAME'}</label><textarea id="rec-name" autoFocus value={draft} onChange={event => changeName(event.target.value)} rows={3} />
          {variant === 'A' && recommendation}
          {variant === 'A' && draftType && <p className="rec-selected-note">✓ Task Type: {draftType}</p>}
          {variant === 'C' && <div className="rec-identity-type">{typeRow}{recommendation}</div>}
          <p className="rec-editor-hint">{entity === 'Recurring Task Series' ? 'Name this recurring routine.' : entity.includes('Block') ? 'What is this time for?' : 'A short name for what you want to do.'}</p>
        </> : <>
          <button className="rec-title-button" onClick={editName}><h2>{name || 'Unnamed block'}</h2><span>✎</span></button><div className="rec-description">≡ <span>{entity.includes('Block') ? 'No supporting note' : 'No description'}</span></div>
          {typeRow}{variant === 'B' && recommendation}
          {variant === 'A' && suggestion && <p className="rec-muted-note">Edit the name to review its suggestion.</p>}
          {variant === 'C' && suggestion && <button className="rec-reopen" onClick={editName}>Review suggested Task Type ›</button>}
          {locked && <p className="rec-muted-note">Task Type follows the linked Planned Block.</p>}
          <div className="rec-field"><span className="rec-glyph">◷</span><span><small>{entity.includes('Block') ? 'Time' : entity === 'Recurring Task Series' ? 'Repeat' : 'Deadline'}</small><strong>{entity.includes('Block') ? '10:00 – 10:30' : entity === 'Recurring Task Series' ? 'Every weekday' : 'No deadline'}</strong></span><span>›</span></div>
          <div className="rec-field"><span className="rec-glyph">⚑</span><span><small>Importance</small><strong>Not set</strong></span><span>›</span></div><div className="rec-field"><span className="rec-glyph">☷</span><span><small>Subtasks</small><strong>No subtasks</strong></span><span>＋</span></div>
        </>}
        </div>
        {!picker && <div className="rec-sheet-actions">{editing ? <button className="rec-primary" onClick={saveName} disabled={!draft.trim() && !entity.includes('Block')}>Save {variant === 'C' ? 'changes' : 'name'}</button> : <button className="rec-primary" onClick={editName}>{variant === 'C' ? 'Edit name & type' : 'Edit name'}</button>}</div>}
      </div><div className="rec-home-indicator" /></div></section>
    <aside className="rec-lab"><div className="rec-eyebrow">PROTOTYPE CONTROLS</div><h2>Try the edges</h2><label>Editing surface<select value={entity} onChange={event => resetContext(event.target.value as Entity)}>{entities.map(item => <option key={item}>{item}</option>)}</select></label><label>Simulated response<select value={outcome} onChange={event => { setOutcome(event.target.value as Outcome); setResponse(null) }}><option value="match">Match · confidence 0.93</option><option value="low">Below threshold · 0.72</option><option value="no-match">None of these types fit</option><option value="unavailable">Jev unavailable</option><option value="limit">More than 254 categories</option></select></label>
      {entity === 'Actual Block' && <label className="rec-checkbox"><input type="checkbox" checked={linked} onChange={event => setLinked(event.target.checked)} />Linked to a Planned Block</label>}
      <button className="rec-reset" onClick={onReset}>↺ Reset this variant</button>
      <div className="rec-state"><h3>Live state</h3><dl><dt>Name</dt><dd>{currentName || '(empty)'}</dd><dt>Task Type</dt><dd>{locked ? 'learning / music (linked)' : currentType || 'unclassified'}</dd><dt>Recommendation</dt><dd>{chosen ? 'Manual choice protected' : locked ? 'Linked — suppressed' : dismissed ? 'Dismissed' : pending ? 'Waiting 500 ms…' : suggestion ? suggestion : 'None'}</dd><dt>Editing</dt><dd>{editing ? 'Unsaved name draft' : 'Details'}</dd><dt>Provider</dt><dd>Simulated · confidence ≥0.8</dd></dl></div>
      <div className="rec-events"><h3>What changed</h3>{events.map((event, index) => <p key={`${index}-${event}`}>{event}</p>)}</div>
    </aside>
  </main>
}
