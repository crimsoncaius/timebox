import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

const names = { A: 'Separate suggestion', B: 'One results list', C: 'By meaning tab' }
export function PrototypeSwitcher() {
  const [params, setParams] = useSearchParams()
  const variant = params.get('variant') ?? 'A'
  const cycle = (delta: number) => {
    const keys = Object.keys(names)
    const next = keys[(keys.indexOf(variant) + delta + keys.length) % keys.length]
    setParams(previous => { previous.set('variant', next); return previous }, { replace: true })
  }
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (e.target instanceof Element && e.target.closest('input,textarea,select,[contenteditable="true"]')) return
      if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.preventDefault(); cycle(e.key === 'ArrowLeft' ? -1 : 1) }
    }
    window.addEventListener('keydown', key)
    return () => window.removeEventListener('keydown', key)
  })
  if (!import.meta.env.DEV) return null
  return <aside className="prototype-switcher" aria-label="Prototype variants">
    <button onClick={() => cycle(-1)} aria-label="Previous variant">←</button>
    <div><small>PROTOTYPE · SIMULATED JEV · NOTHING SAVED</small><strong>{variant} — {names[variant as keyof typeof names] ?? names.A}</strong></div>
    <button onClick={() => cycle(1)} aria-label="Next variant">→</button>
  </aside>
}
