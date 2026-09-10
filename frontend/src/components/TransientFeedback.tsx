import type { HTMLAttributes, ReactNode } from 'react'
import './TransientFeedback.css'

/** Presentation only: callers retain ownership of action, expiry, and recovery state. */
export function TransientFeedback({ title, detail, action, onDismiss, dismissLabel = 'Dismiss',
  disabled = false, error = false, floating = false, className = '', ...props
}: Omit<HTMLAttributes<HTMLDivElement>, 'title'> & {
  title: ReactNode
  detail?: ReactNode
  action?: ReactNode
  onDismiss?: () => void
  dismissLabel?: string
  disabled?: boolean
  error?: boolean
  floating?: boolean
}) {
  return <div role={error ? 'alert' : 'status'} {...props}
    className={`transient-feedback ${floating ? 'feedback-dock' : ''} ${error ? 'feedback-error' : ''} ${className}`}>
    <div className="feedback-copy"><div className="feedback-title">{title}</div>{detail && <div className="feedback-detail">{detail}</div>}</div>
    {action && <div className="feedback-actions">{action}</div>}
    {onDismiss && <button type="button" className="feedback-dismiss" aria-label={dismissLabel} disabled={disabled} onClick={onDismiss}><span aria-hidden="true">×</span></button>}
  </div>
}
