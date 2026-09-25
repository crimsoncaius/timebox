// Throwaway review chrome. Deliberately not part of the product UI.
import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

export const switchPrototypeVariants = ['A', 'B', 'C'] as const
export type SwitchPrototypeVariant = typeof switchPrototypeVariants[number]
export const switchPrototypeNames = { A: 'Live preview', B: 'Before / after', C: 'Review step' }

export function PrototypeSwitcher({ variant }: { variant: SwitchPrototypeVariant }) {
  const [, setParams] = useSearchParams()
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.target as HTMLElement).closest('input, textarea, select, [contenteditable], [role="slider"]')) return
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
      event.preventDefault()
      const next = (switchPrototypeVariants.indexOf(variant) + (event.key === 'ArrowRight' ? 1 : 2)) % 3
      setParams(params => { params.set('variant', switchPrototypeVariants[next]); return params }, { replace: true })
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [setParams, variant])
  if (!import.meta.env.DEV) return null
  const cycle = (offset: number) => setParams(params => {
    params.set('variant', switchPrototypeVariants[(switchPrototypeVariants.indexOf(variant) + offset + 3) % 3])
    return params
  }, { replace: true })
  return <nav className="sp-switcher" aria-label="Prototype variants">
    <button aria-label="Previous variant" onClick={() => cycle(-1)}>←</button>
    <span><small>PROTOTYPE</small>{variant} · {switchPrototypeNames[variant]}</span>
    <button aria-label="Next variant" onClick={() => cycle(1)}>→</button>
  </nav>
}
