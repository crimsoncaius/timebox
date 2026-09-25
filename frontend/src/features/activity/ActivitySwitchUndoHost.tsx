import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { UndoNotice } from '../../components/UndoNotice'
import { TransientFeedback } from '../../components/TransientFeedback'
import { useUndoNotice } from '../../components/useUndoNotice'
import { getActivityRepository } from './activityRepository'

export function ActivitySwitchUndoHost() {
  const repository = getActivityRepository()
  const location = useLocation()
  const { notice, offer, dismiss } = useUndoNotice()
  const [error, setError] = useState('')
  useEffect(() => repository.subscribeSwitch(change => offer({ kind: 'switch', targetId: 0,
    title: 'activity switch', label: `Switched to ${change.name}`, ariaLabel: 'Activity switched',
    undo: () => repository.undoSwitch(change.operationId),
  })), [repository, offer])
  useEffect(() => { dismiss() }, [location.key, dismiss])
  useEffect(() => {
    const close = () => dismiss()
    window.addEventListener('timebox:focus-open', close)
    window.addEventListener('timebox:focus-close', close)
    return () => {
      window.removeEventListener('timebox:focus-open', close)
      window.removeEventListener('timebox:focus-close', close)
    }
  }, [dismiss])
  return <>{notice && <UndoNotice key={notice.id} notice={notice} onDismiss={dismiss} onFailure={setError} />}
    {error && <TransientFeedback floating error title={error} onDismiss={() => setError('')} />}</>
}
