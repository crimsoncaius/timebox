import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

export interface PrototypeVariant {
  key: string
  name: string
}

export function PrototypeSwitcher({
  variants,
  current,
}: {
  variants: PrototypeVariant[]
  current: string
}) {
  const [searchParams, setSearchParams] = useSearchParams()

  const select = (offset: number) => {
    const currentIndex = Math.max(0, variants.findIndex((variant) => variant.key === current))
    const next = variants[(currentIndex + offset + variants.length) % variants.length]
    const params = new URLSearchParams(searchParams)
    params.set('variant', next.key)
    setSearchParams(params, { replace: true })
  }

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target
      if (
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement ||
        (target instanceof HTMLElement && target.isContentEditable)
      ) {
        return
      }
      if (event.key === 'ArrowLeft') select(-1)
      if (event.key === 'ArrowRight') select(1)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  })

  if (!import.meta.env.DEV) return null

  const currentVariant = variants.find((variant) => variant.key === current) ?? variants[0]

  return (
    <div className="fixed bottom-4 left-1/2 z-[100] flex -translate-x-1/2 items-center gap-2 rounded-full bg-inverse-surface px-2 py-2 text-inverse-on-surface shadow-2xl">
      <button
        type="button"
        className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-white/12"
        aria-label="Previous prototype variant"
        onClick={() => select(-1)}
      >
        ←
      </button>
      <span className="min-w-44 px-2 text-center text-xs font-medium tracking-wide text-white">
        {currentVariant.key} — {currentVariant.name}
      </span>
      <button
        type="button"
        className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-white/12"
        aria-label="Next prototype variant"
        onClick={() => select(1)}
      >
        →
      </button>
    </div>
  )
}
