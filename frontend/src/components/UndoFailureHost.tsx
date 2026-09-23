import { useEffect, useState } from 'react'
import { TransientFeedback } from './TransientFeedback'

/** Keeps a late Undo failure visible after its original surface unmounts. */
export function UndoFailureHost() {
  const [message, setMessage] = useState('')
  useEffect(() => {
    const failed = (event: Event) => setMessage((event as CustomEvent<string>).detail)
    window.addEventListener('timebox:undo-failure', failed)
    return () => window.removeEventListener('timebox:undo-failure', failed)
  }, [])
  return message ? <TransientFeedback error title="Undo failed" detail={message}
    onDismiss={() => setMessage('')} className="fixed right-4 top-20 z-70 max-w-lg" /> : null
}
