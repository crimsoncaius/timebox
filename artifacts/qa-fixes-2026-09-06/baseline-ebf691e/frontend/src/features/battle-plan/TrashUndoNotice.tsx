import { useEffect, useRef, useState } from 'react'

export type TrashUndoTarget = {
  noticeId: number
  id: number
  title: string
}

export function TrashUndoNotice({ target, onUndo, onDismiss, onExpire }: {
  target: TrashUndoTarget
  onUndo: () => Promise<void>
  onDismiss: () => void
  onExpire: () => void
}) {
  const [phase, setPhase] = useState<'ready' | 'restoring' | 'expiring' | 'error'>('ready')
  const [failure, setFailure] = useState('')
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const [pageVisible, setPageVisible] = useState(document.visibilityState !== 'hidden')
  const remainingMs = useRef(10_000)
  const startedAt = useRef<number | null>(null)
  const fadeTimer = useRef<number | null>(null)
  const restoring = useRef(false)
  const onExpireRef = useRef(onExpire)

  useEffect(() => {
    onExpireRef.current = onExpire
  }, [onExpire])

  useEffect(() => {
    const onVisibilityChange = () => setPageVisible(document.visibilityState !== 'hidden')
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => document.removeEventListener('visibilitychange', onVisibilityChange)
  }, [])

  const counting = pageVisible && !hovered && !focused && phase === 'ready'
  useEffect(() => {
    if (!counting) return
    startedAt.current = Date.now()
    const expiry = window.setTimeout(() => {
      startedAt.current = null
      remainingMs.current = 0
      setPhase('expiring')
      if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
        onExpireRef.current()
      } else {
        fadeTimer.current = window.setTimeout(() => onExpireRef.current(), 150)
      }
    }, remainingMs.current)
    return () => {
      window.clearTimeout(expiry)
      if (startedAt.current != null) {
        remainingMs.current = Math.max(0, remainingMs.current - (Date.now() - startedAt.current))
        startedAt.current = null
      }
    }
  }, [counting])

  useEffect(() => () => {
    if (fadeTimer.current != null) window.clearTimeout(fadeTimer.current)
  }, [])

  const restore = async () => {
    if (restoring.current || (phase !== 'ready' && phase !== 'error')) return
    restoring.current = true
    setPhase('restoring')
    try {
      await onUndo()
    } catch (error) {
      restoring.current = false
      setFailure(error instanceof Error ? error.message : 'Could not restore the item')
      setPhase('error')
    }
  }

  const isError = phase === 'error'
  const actionsDisabled = phase === 'restoring' || phase === 'expiring'

  return (
    <div
      role={isError ? 'alert' : 'status'}
      aria-label={isError ? 'Trash undo failed' : 'Trash undo'}
      aria-live={isError ? 'assertive' : 'polite'}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocusCapture={() => setFocused(true)}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocused(false)
      }}
      className={`fixed bottom-5 left-1/2 z-100 flex -translate-x-1/2 items-center gap-4 rounded-full bg-on-surface px-5 py-3 text-sm text-surface shadow-xl transition-opacity duration-150 motion-reduce:transition-none dark:bg-dark-on-surface dark:text-dark-background ${phase === 'expiring' ? 'opacity-0' : 'opacity-100'}`}
    >
      {isError ? `Could not restore ${target.title}. ${failure}` : `${target.title} moved to Trash`}
      <button
        type="button"
        aria-label={phase === 'restoring' ? `Restoring ${target.title}` : undefined}
        className="font-medium underline disabled:opacity-50"
        disabled={actionsDisabled}
        onClick={() => void restore()}
      >
        {phase === 'restoring' ? 'Restoring…' : isError ? 'Retry' : 'Undo'}
      </button>
      <button type="button" aria-label="Dismiss" disabled={actionsDisabled} onClick={onDismiss}>×</button>
    </div>
  )
}
