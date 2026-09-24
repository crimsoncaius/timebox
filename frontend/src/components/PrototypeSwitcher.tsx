import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

/** Throwaway comparison control; never rendered in production. */
export function PrototypeSwitcher({ variants }: { variants: { key: string; name: string }[] }) {
  const [params, setParams] = useSearchParams()
  const index = Math.max(0, variants.findIndex(v => v.key === params.get('variant')))
  function move(delta: number) {
    const next = new URLSearchParams(params)
    next.set('variant', variants[(index + delta + variants.length) % variants.length].key)
    setParams(next, { replace: true })
  }
  useEffect(() => {
    function keydown(e: KeyboardEvent) {
      if ((e.target as HTMLElement).closest('input, textarea, select, [contenteditable="true"]') || e.altKey || e.ctrlKey || e.metaKey) return
      if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.preventDefault(); move(e.key === 'ArrowLeft' ? -1 : 1) }
    }
    window.addEventListener('keydown', keydown)
    return () => window.removeEventListener('keydown', keydown)
  })
  if (!import.meta.env.DEV) return null
  return <nav className="prototype-switcher" aria-label="Prototype variants">
    <button aria-label="Previous variant" onClick={() => move(-1)}>←</button>
    <span><small>PROTOTYPE · {index + 1} / {variants.length}</small><strong>{variants[index].key} — {variants[index].name}</strong></span>
    <button aria-label="Next variant" onClick={() => move(1)}>→</button>
  </nav>
}
