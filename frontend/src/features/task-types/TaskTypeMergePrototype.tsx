// Throwaway: compare rename collision and explicit merge on /task-types?prototype=merge&variant=A|B.
import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { BattlePlanSidebarFrame } from '../battle-plan/BattlePlanSidebarFrame'

const initial = ['Reading', 'Transportation', 'Transportation/Train', 'Travel', 'Travel/Flights', 'Travel/Train', 'unspecified']
const button = 'rounded-lg border border-outline-variant/30 px-4 py-2 text-sm hover:bg-surface-container-high disabled:opacity-40'
const primary = `${button} bg-primary text-on-primary hover:opacity-90`
const field = 'mt-2 w-full rounded-lg bg-surface-container-high p-3 text-on-surface'

export function TaskTypeMergePrototype() {
  const [params, setParams] = useSearchParams()
  const variant = params.get('variant') === 'B' ? 'B' : 'A'
  const [types, setTypes] = useState(initial)
  const [source, setSource] = useState<string | null>(null)
  const [target, setTarget] = useState('')
  const [stage, setStage] = useState<'edit' | 'preview'>('edit')
  const [notice, setNotice] = useState('')
  const [filter, setFilter] = useState('')
  const canonical = target.trim().toLowerCase()
  const destination = types.find(t => t.toLowerCase() === canonical)
  const overlap = !!source && !!destination && (source === destination || source.startsWith(destination + '/') || destination.startsWith(source + '/'))
  const blocked = overlap || destination === 'unspecified'
  const affected = types.filter(t => t === source || t.startsWith(source + '/'))
  const paths = affected.map(t => ({ from: t, to: (destination ?? target.trim()) + t.slice(source?.length ?? 0) }))
  const reset = () => { setTypes(initial); setSource(null); setNotice(''); setFilter(''); setTarget('') }
  const switchVariant = () => { const next = new URLSearchParams(params); next.set('variant', variant === 'A' ? 'B' : 'A'); setParams(next, { replace: true }); reset() }
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.key === 'ArrowLeft' || e.key === 'ArrowRight') && !(e.target instanceof HTMLElement && e.target.closest('input,textarea,select,[contenteditable]'))) {
        e.preventDefault(); switchVariant()
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  })
  const open = (name: string) => { setSource(name); setTarget(variant === 'A' ? name : ''); setStage('edit'); setNotice('') }
  const finish = () => {
    setTypes(Array.from(new Set(types.filter(t => !affected.includes(t)).concat(paths.map(p => p.to)))).sort())
    setNotice(destination ? `${source} merged into ${destination}. All associated work now uses the surviving types. The running activity continues without interruption.` : `${source} renamed to ${target.trim()}.`)
    setSource(null)
  }
  return <Layout mainClassName="w-full px-4 py-6 sm:px-8 lg:px-10">
    <BattlePlanSidebarFrame>
      <div className="mb-6 rounded-xl bg-primary-container/40 p-4 text-sm">
        <strong>Throwaway prototype · Sample data</strong>
        <p className="mt-1">Try merging Travel into Transportation. Switch flows using the bottom bar. All changes stay in this preview.</p>
      </div>
      <div className="grid gap-8 xl:grid-cols-[240px_1fr] pb-28">
        <header><h1 className="font-headline text-4xl font-light tracking-tight">Task Types</h1><p className="mt-4 text-on-surface-variant">Organize the categories used across your tasks and recorded time.</p><p className="mt-6 text-sm text-on-surface-variant">{variant === 'A' ? 'A · Start with Rename, then discover the merge when a name already exists.' : 'B · Choose Merge into… and select an existing destination.'}</p></header>
        <section>
          {notice && <div role="status" className="mb-5 rounded-xl bg-primary-container/40 p-4">{notice}</div>}
          <input aria-label="Search task types" placeholder="Search task types" value={filter} onChange={e => setFilter(e.target.value)} className={field} />
          <ul className="mt-5 divide-y divide-outline-variant/15">
            {types.filter(t => t.toLowerCase().includes(filter.toLowerCase())).map(name => <li key={name} className="flex flex-wrap items-center justify-between gap-3 py-4">
              <span className={name.includes('/') ? 'pl-5 text-on-surface-variant' : 'font-medium'}>{name.includes('/') ? '↳ ' + name.split('/').at(-1) : name}</span>
              {name !== 'unspecified' && <button className={button} onClick={() => open(name)} aria-label={`${variant === 'A' ? 'Rename' : 'Merge'} ${name}`}>{variant === 'A' ? 'Rename' : 'Merge into…'}</button>}
            </li>)}
          </ul>
          <details className="mt-6 rounded-xl bg-surface-container-low p-4 text-sm"><summary className="cursor-pointer">Preview state · {types.length} Task Types</summary><pre className="mt-3 whitespace-pre-wrap">{JSON.stringify({ variant, types, source, destination: target, stage: source ? stage : 'list', runningActivity: { type: types.includes('Travel') ? 'Travel' : destination ?? 'Transportation', interrupted: false }, persistence: 'memory only' }, null, 2)}</pre></details>
        </section>
      </div>
      {source && <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/40 p-3" onClick={() => setSource(null)}>
        <section role="dialog" aria-modal="true" aria-labelledby="merge-title" className="max-h-[90vh] w-full max-w-xl overflow-auto rounded-2xl bg-surface p-6 text-on-surface shadow-xl" onClick={e => e.stopPropagation()}>
          <div className="flex items-start justify-between gap-4"><h2 id="merge-title" className="font-headline text-2xl">{stage === 'preview' ? 'Review merge' : variant === 'A' ? `Rename ${source}` : `Merge ${source} into…`}</h2><button aria-label="Close dialog" className={button} onClick={() => setSource(null)}>×</button></div>
          {stage === 'edit' ? <>
            {variant === 'A' ? <label className="mt-6 block">Task Type Path<input autoFocus className={field} value={target} onChange={e => setTarget(e.target.value)} /></label> : <label className="mt-6 block">Keep this existing Task Type<select autoFocus className={field} value={target} onChange={e => setTarget(e.target.value)}><option value="">Choose destination</option>{types.filter(t => t !== 'unspecified' && t !== source && !t.startsWith(source + '/') && !source.startsWith(t + '/')).map(t => <option key={t}>{t}</option>)}</select></label>}
            {source === destination ? <p className="mt-4 text-on-surface-variant">Enter a new name to continue.</p> : blocked ? <p className="mt-4 text-error">Choose a different, separate branch. A type cannot merge with itself, its ancestors, descendants, or unspecified.</p> : destination ? <div className="mt-5 rounded-xl bg-primary-container/40 p-4"><strong>{variant === 'A' ? `${destination} already exists.` : `${destination} will be kept.`}</strong><p className="mt-2">Combine {source} and its descendants with this branch. Review what changes before confirming.</p></div> : <p className="mt-4 text-on-surface-variant">{variant === 'A' ? 'Renaming also updates descendant paths and historical names.' : 'The destination survives; matching descendants combine.'}</p>}
            <div className="mt-6 flex justify-end gap-3"><button className={button} onClick={() => setSource(null)}>Cancel</button><button className={primary} disabled={!target.trim() || blocked || (variant === 'B' && !destination)} onClick={() => destination ? setStage('preview') : finish()}>{destination || variant === 'B' ? 'Review merge' : 'Save name'}</button></div>
          </> : <>
            <p className="mt-4 text-lg">{source} → <strong>{destination}</strong></p>
            <ul className="mt-4 divide-y divide-outline-variant/20">{paths.map(p => <li className="py-3 text-sm" key={p.from}><span className="block">{p.from} → {p.to}</span><span className="text-on-surface-variant">{types.includes(p.to) ? 'Combine into existing type' : 'Move into destination branch'}</span></li>)}</ul>
            <div className="mt-4 rounded-xl bg-surface-container-low p-4 text-sm"><strong>Associated work · sample counts</strong><p className="mt-2">8 Tasks · 12 Planned Blocks · 34 Actual Blocks · 2 Recurring Task Series</p><p className="mt-2 text-on-surface-variant">Tasks include 2 completed, 1 archived and 1 trashed. Historical time is combined under {destination}. Existing links and unrelated classifications stay intact.</p><p className="mt-2">Running activity: {source} → {destination}, without stopping.</p></div>
            <p className="mt-5 font-medium">This permanently combines the categories. {source} disappears. This cannot be undone.</p>
            <div className="mt-6 flex flex-wrap justify-end gap-3"><button className={button} onClick={() => setStage('edit')}>Back</button><button className={primary} onClick={finish}>Merge into {destination}</button></div>
          </>}
        </section>
      </div>}
    </BattlePlanSidebarFrame>
    <div className="fixed bottom-5 left-1/2 z-[70] flex -translate-x-1/2 items-center gap-3 whitespace-nowrap rounded-full bg-neutral-900 px-3 py-2 text-sm text-white shadow-xl">
      <button aria-label="Previous variant" className="px-2 py-2" onClick={switchVariant}>←</button><span>{variant} · {variant === 'A' ? 'Rename collision' : 'Explicit merge'}</span><button aria-label="Next variant" className="px-2 py-2" onClick={switchVariant}>→</button><button className="border-l border-white/30 pl-3 pr-2" onClick={reset}>Reset</button>
    </div>
  </Layout>
}


