import { useEffect, useRef, useState } from 'react'
import { ApiHttpError } from '../lib/api/client'
import { errorMessage } from '../lib/errors'
import { TransientFeedback } from './TransientFeedback'
import type { UndoOpportunity } from './useUndoNotice'

export function UndoNotice({ notice, onDismiss, onFailure }: {
  notice: UndoOpportunity
  onDismiss: (id: number) => void
  onFailure: (message: string) => void
}) {
  const [phase, setPhase] = useState<'ready' | 'undoing' | 'expiring' | 'failed' | 'unavailable'>('ready')
  const [failure, setFailure] = useState('')
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const [visible, setVisible] = useState(document.visibilityState !== 'hidden')
  const remaining = useRef(10_000)
  const started = useRef<number | null>(null)
  const inFlight = useRef(false)
  const mounted = useRef(true)
  const dismissRef = useRef(onDismiss)
  const failureRef = useRef(onFailure)
  useEffect(() => { dismissRef.current = onDismiss; failureRef.current = onFailure })
  useEffect(() => {
    mounted.current = true
    const changed = () => setVisible(document.visibilityState !== 'hidden')
    document.addEventListener('visibilitychange', changed)
    return () => { mounted.current = false; document.removeEventListener('visibilitychange', changed) }
  }, [])

  const counting = visible && !hovered && !focused && phase === 'ready'
  useEffect(() => {
    if (!counting) return
    started.current = Date.now()
    const timer = window.setTimeout(() => {
      started.current = null
      remaining.current = 0
      if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) dismissRef.current(notice.id)
      else setPhase('expiring')
    }, remaining.current)
    return () => {
      window.clearTimeout(timer)
      if (started.current != null) {
        remaining.current = Math.max(0, remaining.current - (Date.now() - started.current))
        started.current = null
      }
    }
  }, [counting, notice.id])
  useEffect(() => {
    if (phase !== 'expiring') return
    const timer = window.setTimeout(() => dismissRef.current(notice.id), 150)
    return () => window.clearTimeout(timer)
  }, [phase, notice.id])

  const undo = async () => {
    if (inFlight.current || (phase !== 'ready' && phase !== 'failed')) return
    inFlight.current = true
    setPhase('undoing')
    try {
      await notice.undo()
      if (mounted.current) dismissRef.current(notice.id)
    } catch (cause) {
      const unavailable = cause instanceof ApiHttpError && [404, 409, 410].includes(cause.status)
      const message = unavailable ? `Undo is no longer available for ${notice.title}. ${errorMessage(cause, '')}` : errorMessage(cause, `Could not undo ${notice.title}`)
      if (mounted.current) { setFailure(message); setPhase(unavailable ? 'unavailable' : 'failed') }
      else {
        failureRef.current(`${notice.title}: ${message}`)
        window.dispatchEvent(new CustomEvent('timebox:undo-failure', { detail: `${notice.title}: ${message}` }))
      }
    } finally { inFlight.current = false }
  }
  const disabled = phase === 'undoing' || phase === 'expiring'
  return <TransientFeedback floating error={phase === 'failed' || phase === 'unavailable'}
    title={phase === 'undoing' ? `${notice.progressLabel ?? `Undoing ${notice.title}`}…` : phase === 'failed' ? notice.failureLabel ?? `Could not undo ${notice.title}` : phase === 'unavailable' ? `Undo unavailable for ${notice.title}` : notice.label}
    detail={failure || notice.detail} onDismiss={() => onDismiss(notice.id)} disabled={disabled}
    aria-label={phase === 'failed' || phase === 'unavailable' ? `${notice.ariaLabel} failed` : notice.ariaLabel}
    onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}
    onFocusCapture={() => setFocused(true)}
    onBlurCapture={event => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocused(false) }}
    className={`transition-opacity duration-150 motion-reduce:transition-none ${phase === 'expiring' ? 'opacity-0' : 'opacity-100'}`}
    action={phase === 'unavailable' ? undefined : <button type="button" aria-label={phase === 'undoing' ? notice.progressLabel : undefined} disabled={disabled} onClick={() => void undo()}>{phase === 'undoing' ? `${notice.progressLabel?.split(' ')[0] ?? 'Undoing'}…` : phase === 'failed' ? 'Retry' : 'Undo'}</button>}
  />
}
