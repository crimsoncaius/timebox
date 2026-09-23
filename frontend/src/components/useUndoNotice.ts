import { useCallback, useRef, useState } from 'react'

export type UndoOpportunity = {
  id: number
  title: string
  detail?: string
  label: string
  ariaLabel: string
  kind: 'trash' | 'completion' | 'recording'
  targetId: number
  progressLabel?: string
  failureLabel?: string
  undo: () => Promise<void>
}

/** One in-memory slot per surface. An offer consumes the previous opportunity. */
export function useUndoNotice() {
  const [notice, setNotice] = useState<UndoOpportunity | null>(null)
  const nextId = useRef(1)
  const offer = useCallback((opportunity: Omit<UndoOpportunity, 'id'>) => {
    setNotice({ ...opportunity, id: nextId.current++ })
  }, [])
  const dismiss = useCallback((id?: number) => setNotice(current => id == null || current?.id === id ? null : current), [])
  return { notice, offer, dismiss }
}
