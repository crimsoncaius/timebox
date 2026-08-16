import { useState } from 'react'
import { OptimisticSortingPlugin } from '@dnd-kit/dom/sortable'
import { KeyboardSensor, PointerSensor } from '@dnd-kit/react'
import { useSortable } from '@dnd-kit/react/sortable'
import { useNavigate } from 'react-router-dom'
import { deadlineBadge, STATUS_LABELS, type DeadlineBadge as DeadlineBadgeValue } from '../../lib/battlePlan'
import type { BattleTask, TaskStatus } from '../../lib/api'

const cardSensors = [
  PointerSensor,
  KeyboardSensor.configure({
    keyboardCodes: {
      ...KeyboardSensor.defaults.keyboardCodes,
      start: ['Space'],
    },
  }),
]

export function BattlePlanCard({
  task,
  index,
  column,
  timezone,
  serverNowIso,
  onOpen,
  onAddSubtask,
  onPatchSubtask,
  onToggleReady,
}: {
  task: BattleTask
  index: number
  column: TaskStatus
  timezone: string
  serverNowIso: string
  onOpen: (id?: number) => void
  onAddSubtask: (parentId: number, title: string) => Promise<void>
  onPatchSubtask: (id: number, status: TaskStatus) => Promise<void>
  onToggleReady: (id: number, ready: boolean) => Promise<void>
}) {
  const navigate = useNavigate()
  const { ref, isDragging } = useSortable({
    id: task.id,
    index,
    group: column,
    type: 'battle-task',
    accept: 'battle-task',
    sensors: cardSensors,
    // React owns cross-column placement. Optimistic DOM reparenting can make
    // React remove a card from the wrong column after a successful drop.
    plugins: (defaults) => defaults.filter((plugin) => plugin !== OptimisticSortingPlugin),
  })
  const [subtasksOpen, setSubtasksOpen] = useState(false)
  const [subtaskTitle, setSubtaskTitle] = useState('')
  const [addingSubtask, setAddingSubtask] = useState(false)
  const [busySubtaskId, setBusySubtaskId] = useState<number | null>(null)
  const due = deadlineBadge(task, serverNowIso, timezone)
  const completed = task.subtasks.filter((subtask) => subtask.status === 'completed').length
  const progressLabel = task.subtasks.length === 0
    ? `Add a subtask to ${task.title}`
    : `${completed} of ${task.subtasks.length} subtasks completed for ${task.title}`

  return (
    <article
      ref={ref}
      data-task-id={task.id}
      data-dragging={isDragging ? 'true' : undefined}
      tabIndex={0}
      aria-label={`Move ${task.title}`}
      title="Drag task to change its status"
      onClick={() => onOpen()}
      onKeyDown={(event) => {
        if (event.currentTarget !== event.target || event.key !== 'Enter') return
        event.preventDefault()
        onOpen()
      }}
      className={[
        'group cursor-grab rounded-2xl bg-surface-container-lowest p-4 shadow-[0_0_32px_rgba(45,52,53,0.045)] transition-opacity active:cursor-grabbing dark:bg-dark-surface-container-lowest',
        isDragging ? 'opacity-45' : 'opacity-100',
      ].join(' ')}
    >
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1 text-left">
          <h3 className="font-headline text-base font-normal leading-snug tracking-tight text-on-surface dark:text-dark-on-surface">
            {task.title}
          </h3>
        </div>
        <span
          aria-hidden
          className="-mr-1 -mt-1 rounded-full p-1.5 text-on-surface-variant/55 opacity-60 transition hover:bg-surface-container-low group-hover:opacity-100 dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden>drag_indicator</span>
        </span>
      </div>

      <div className="mt-3 block w-full text-left">
        <div className="flex flex-wrap gap-1.5">
          {task.project ? <MetaChip>{task.project.name}</MetaChip> : <MetaChip>Admin</MetaChip>}
          {task.task_type ? <MetaChip>{task.task_type.name}</MetaChip> : null}
          {task.urgency ? <MetaChip>U · {task.urgency}</MetaChip> : null}
          {task.importance ? <MetaChip>I · {task.importance}</MetaChip> : null}
          {task.recurring_template_id ? (
            <button
              type="button"
              className="rounded-full bg-primary/10 px-2 py-1 text-[10px] font-medium text-primary transition hover:bg-primary/15"
              onPointerDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation()
                navigate(`/battle-plan?view=recurring&recurring=${task.recurring_template_id}`)
              }}
            >
              ↻ {task.recurring_template_title ?? 'Recurring'}
            </button>
          ) : null}
          {task.recurrence_kind === 'quota_parent' && task.quota_period_start && task.quota_period_end ? (
            <MetaChip>
              {formatQuotaPeriod(task.quota_period_start, task.quota_period_end)} · {task.quota_completed ?? 0}/{task.expected_sessions ?? 0}
            </MetaChip>
          ) : null}
        </div>
      </div>

      <div className="mt-3 flex items-center justify-between gap-3">
        {due ? <DeadlineBadge badge={due} /> : <span />}
        <div className="flex items-center gap-1">
          <button
            type="button"
            aria-label={`${task.ready_to_plan ? 'Remove' : 'Add'} ${task.title} ${task.ready_to_plan ? 'from' : 'to'} Ready to Plan`}
            aria-pressed={task.ready_to_plan}
            title={task.ready_to_plan ? 'Remove from Ready to Plan' : 'Add to Ready to Plan'}
            className={`flex shrink-0 items-center gap-1 rounded-lg px-2 py-1 text-[10px] font-medium uppercase tracking-wide transition ${task.ready_to_plan ? 'bg-primary/12 text-primary' : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'}`}
            onPointerDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation()
              void onToggleReady(task.id, !task.ready_to_plan)
            }}
          >
            <span className="material-symbols-outlined text-[14px]" aria-hidden>{task.ready_to_plan ? 'event_available' : 'event_upcoming'}</span>
            {task.ready_to_plan ? 'Ready' : 'Plan'}
          </button>
          <button
          type="button"
          aria-label={progressLabel}
          aria-expanded={subtasksOpen}
          aria-controls={`task-${task.id}-subtasks`}
          className="flex shrink-0 items-center gap-1 rounded-lg px-1.5 py-1 text-xs text-on-surface-variant transition hover:bg-surface-container-low hover:text-on-surface dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container"
          onPointerDown={(event) => event.stopPropagation()}
          onClick={(event) => {
            event.stopPropagation()
            setSubtasksOpen((current) => !current)
          }}
        >
          <span className="material-symbols-outlined text-[15px]" aria-hidden>account_tree</span>
          {completed}/{task.subtasks.length}
          </button>
        </div>
      </div>

      {subtasksOpen ? (
        <section
          id={`task-${task.id}-subtasks`}
          aria-label={`Subtasks for ${task.title}`}
          className="mt-3 border-t border-outline-variant/15 pt-3 dark:border-dark-outline-variant/30"
          onPointerDown={(event) => event.stopPropagation()}
          onClick={(event) => event.stopPropagation()}
        >
          {task.subtasks.length > 0 ? (
            <div className="space-y-1.5">
              {task.subtasks.map((subtask) => {
                const subtaskDue = deadlineBadge(subtask, serverNowIso, timezone)
                const isCompleted = subtask.status === 'completed'
                return (
                  <div key={subtask.id} className="rounded-xl bg-surface-container-low/70 p-2 dark:bg-dark-surface-container/70">
                    <div className="flex items-start gap-2">
                      <input
                        type="checkbox"
                        className="mt-0.5 shrink-0"
                        checked={isCompleted}
                        disabled={busySubtaskId === subtask.id}
                        aria-label={`${isCompleted ? 'Reopen' : 'Complete'} subtask ${subtask.title}`}
                        onChange={async () => {
                          setBusySubtaskId(subtask.id)
                          try {
                            await onPatchSubtask(subtask.id, isCompleted ? 'open' : 'completed')
                          } catch {
                            // The page presents the request error and the server state remains authoritative.
                          } finally {
                            setBusySubtaskId(null)
                          }
                        }}
                      />
                      <button type="button" className={`min-w-0 flex-1 text-left text-sm leading-snug ${isCompleted ? 'text-on-surface-variant line-through' : ''}`} onClick={() => onOpen(subtask.id)}>
                        {subtask.title}
                      </button>
                      {subtask.status !== 'open' && subtask.status !== 'completed' ? (
                        <span className="shrink-0 rounded-full bg-surface-container-lowest px-1.5 py-0.5 text-[9px] uppercase tracking-wide text-on-surface-variant dark:bg-dark-surface-container-lowest">
                          {STATUS_LABELS[subtask.status]}
                        </span>
                      ) : null}
                    </div>
                    {subtaskDue ? <div className="mt-1.5 pl-6"><DeadlineBadge badge={subtaskDue} /></div> : null}
                  </div>
                )
              })}
            </div>
          ) : (
            <p className="text-xs text-on-surface-variant">No subtasks yet.</p>
          )}
          <form
            className="mt-2 flex gap-1.5"
            onSubmit={async (event) => {
              event.preventDefault()
              const cleanTitle = subtaskTitle.trim()
              if (!cleanTitle || addingSubtask) return
              setAddingSubtask(true)
              try {
                await onAddSubtask(task.id, cleanTitle)
                setSubtaskTitle('')
              } catch {
                // Keep the subtask title available for retry.
              } finally {
                setAddingSubtask(false)
              }
            }}
          >
            <input
              aria-label={`New subtask for ${task.title}`}
              placeholder="Add a subtask"
              value={subtaskTitle}
              onChange={(event) => setSubtaskTitle(event.target.value)}
              className="min-w-0 flex-1 rounded-lg bg-surface-container-low px-2 py-1.5 text-xs outline-none focus-visible:ring-1 focus-visible:ring-primary/25 dark:bg-dark-surface-container"
            />
            <button type="submit" aria-label={`Add subtask to ${task.title}`} disabled={addingSubtask || !subtaskTitle.trim()} className="rounded-lg bg-primary px-2 py-1.5 text-xs text-on-primary disabled:opacity-40">
              Add
            </button>
          </form>
        </section>
      ) : null}
    </article>
  )
}

function formatQuotaPeriod(start: string, end: string) {
  const formatter = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
  const left = formatter.format(new Date(`${start}T12:00:00Z`))
  const right = formatter.format(new Date(`${end}T12:00:00Z`))
  return start === end ? left : `${left}–${right}`
}

function DeadlineBadge({ badge }: { badge: DeadlineBadgeValue }) {
  const toneClass = {
    overdue: 'bg-error-container/45 text-error dark:bg-error/15',
    today: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/55 dark:text-emerald-300',
    tomorrow: 'bg-amber-100 text-amber-800 dark:bg-amber-950/55 dark:text-amber-300',
    upcoming: 'bg-violet-100 text-violet-800 dark:bg-violet-950/55 dark:text-violet-300',
    later: 'bg-surface-container-low text-on-surface-variant dark:bg-dark-surface-container dark:text-dark-on-surface-variant',
  }[badge.tone]
  return (
    <span className={`inline-flex max-w-full items-center gap-1 rounded-md px-1.5 py-1 text-[10px] font-medium ${toneClass}`}>
      <span className="material-symbols-outlined text-[12px]" aria-hidden>calendar_today</span>
      <span className="truncate">{badge.label}</span>
    </span>
  )
}

function MetaChip({ children }: { children: React.ReactNode }) {
  return (
    <span className="max-w-full truncate rounded-full bg-surface-container-low px-2 py-1 font-label text-[10px] uppercase tracking-[0.09em] text-on-surface-variant dark:bg-dark-surface-container dark:text-dark-on-surface-variant">
      {children}
    </span>
  )
}
