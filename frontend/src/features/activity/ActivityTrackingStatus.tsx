import { compactStatusLabel, statusMarkKind, statusShowsRetry, type StatusFlags } from './activityStatus'

const chipClass = 'inline-flex min-h-8 max-w-full items-center rounded-full border border-outline/70 bg-surface-container-low text-xs font-medium text-on-surface dark:border-dark-outline-variant dark:bg-dark-surface-container dark:text-dark-on-surface'

export function ActivityTrackingStatus({
  flags,
  retryDisabled = false,
  onRetry,
}: {
  flags: StatusFlags
  retryDisabled?: boolean
  onRetry?: () => void
}) {
  if (statusMarkKind(flags) == null) return null
  const label = compactStatusLabel(flags)
  const retryControl = statusShowsRetry(flags) && onRetry
    ? <button type="button" disabled={retryDisabled} className="rounded-full px-2 py-1 font-medium text-planned dark:text-planned-dark" onClick={onRetry}>Retry</button>
    : null
  if (retryControl) {
    return <span role="status" className={`${chipClass} py-0 pl-3 pr-1`}>
      <span className="truncate">{label}</span>
      <span aria-hidden className="mx-2 h-3 w-px shrink-0 bg-outline-variant/50 dark:bg-dark-outline-variant" />
      {retryControl}
    </span>
  }
  return <span role="status" className={`${chipClass} px-3`}>{label}</span>
}
