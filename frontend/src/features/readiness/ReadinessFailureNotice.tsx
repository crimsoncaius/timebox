import type { BattleTask } from '../../lib/api'
import { useReadinessCoordinator } from './readinessCoordinator'

export function ReadinessFailureNotice({ task, className = '' }: { task: BattleTask; className?: string }) {
  const readiness = useReadinessCoordinator()
  const state = readiness.stateFor(task.id)
  if (!state.failure) return null

  return (
    <div
      role="alert"
      aria-label={`${task.title} readiness error`}
      className={`flex items-center justify-between gap-2 text-xs text-error ${className}`}
    >
      <span>{state.failure.message}</span>
      <button
        type="button"
        className="shrink-0 font-medium underline"
        aria-label={`Retry Ready to Plan for ${task.title}`}
        onClick={(event) => {
          event.stopPropagation()
          void readiness.retry(task)
        }}
      >
        Retry
      </button>
    </div>
  )
}
