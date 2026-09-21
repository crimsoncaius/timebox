import { READY_TASK_DRAG_TYPE } from '../../components/DayTimeline'
import { PointerSensor, useDraggable } from '@dnd-kit/react'
import { useReadinessCoordinator } from '../readiness/readinessCoordinator'
import { useState } from 'react'
import type { BattleTask } from '../../lib/api'
import { ReadinessFailureNotice } from '../readiness/ReadinessFailureNotice'

export function ReadyToPlanDrawer({ tasks, selectedTaskId, dragInstance, busyTaskId, onSelect }: {
  tasks: BattleTask[]
  selectedTaskId: number | null
  dragInstance: 'mobile' | 'desktop'
  busyTaskId: number | null
  onSelect: (taskId: number | null) => void
}) {
  const [query, setQuery] = useState('')
  const visible = tasks.filter((task) => task.title.toLowerCase().includes(query.trim().toLowerCase()))
  const readiness = useReadinessCoordinator()

  return (
    <section className="rounded-2xl bg-surface-container-lowest/90 p-5 shadow-[0_0_40px_rgba(45,52,53,0.04)] dark:bg-dark-surface-container-lowest/85" aria-label="Ready to Plan tasks">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-label text-[10px] uppercase tracking-[0.16em] text-primary">Battle Plan</p>
          <h2 className="mt-1 font-headline text-xl font-light text-on-surface">Ready to Plan</h2>
          <p className="mt-1 text-xs leading-relaxed text-on-surface-variant">Drag a task to Planned, or select it and choose a time slot.</p>
        </div>
        <span className="rounded-full bg-surface-container px-2 py-1 text-xs text-on-surface-variant">{tasks.length}</span>
      </div>

      {tasks.length > 4 || query.length > 0 ? (
        <input
          type="search"
          aria-label="Search Ready to Plan tasks"
          placeholder="Search tasks"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="mt-4 w-full rounded-xl border border-outline-variant/25 bg-surface px-3 py-2 text-sm outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/20 dark:border-dark-outline-variant dark:bg-dark-surface"
        />
      ) : null}

      <div
        data-testid="ready-to-plan-list"
        className={`mt-4 space-y-2 ${dragInstance === 'mobile' ? 'max-h-80 overflow-y-auto overscroll-contain pr-1' : ''}`}
      >
        {visible.map((task) => {
          const pending = readiness.stateFor(task.id).pending
          return (
            <ReadyToPlanTaskCard
              key={task.id}
              task={task}
              selected={task.id === selectedTaskId}
              dragInstance={dragInstance}
              disabled={busyTaskId != null || pending}
              pending={pending}
              onSelect={() => onSelect(task.id === selectedTaskId ? null : task.id)}
            />
          )
        })}
        {visible.length === 0 ? (
          <p className="rounded-xl border border-dashed border-outline-variant/30 px-3 py-5 text-center text-xs text-on-surface-variant">
            {tasks.length === 0 ? 'No tasks are waiting to be planned.' : 'No matching tasks.'}
          </p>
        ) : null}
      </div>
    </section>
  )
}

function ReadyToPlanTaskCard({ task, selected, dragInstance, disabled, pending, onSelect }: {
  task: BattleTask
  selected: boolean
  dragInstance: 'mobile' | 'desktop'
  disabled: boolean
  pending: boolean
  onSelect: () => void
}) {
  const displayTitle = task.recurrence_kind === 'quota_session' && task.parent_title
    ? `${task.parent_title} · ${task.title}`
    : task.title
  const { ref, handleRef, isDragging } = useDraggable({
    id: `ready-task:${dragInstance}:${task.id}`,
    type: READY_TASK_DRAG_TYPE,
    data: { taskId: task.id },
    sensors: [PointerSensor],
    disabled,
  })

  return (
    <div
      ref={ref}
      data-ready-task-id={task.id}
      data-dragging={isDragging ? 'true' : undefined}
      className={`flex w-full items-stretch rounded-xl border text-left transition ${selected ? 'border-primary/40 bg-primary/10 ring-1 ring-primary/15' : 'border-outline-variant/20 bg-surface hover:border-primary/25 dark:border-dark-outline-variant dark:bg-dark-surface'} ${pending ? 'border-primary/30 bg-primary/5' : ''} ${isDragging ? 'z-80 cursor-grabbing opacity-80 shadow-xl' : ''}`}
    >
      <button
        type="button"
        aria-label={pending ? `${displayTitle} is saving and unavailable to plan` : displayTitle}
        aria-pressed={selected}
        disabled={disabled}
        onClick={onSelect}
        className="min-w-0 flex-1 px-3 py-3 text-left disabled:opacity-55"
      >
        <span className="block truncate text-sm font-medium text-on-surface">{displayTitle}</span>
        <span className="mt-1 block text-xs text-on-surface-variant">
          {pending ? 'Saving · unavailable to plan' : task.task_type?.name ?? 'Unset'}
        </span>
      </button>
      <ReadinessFailureNotice task={task} className="self-center px-2" />
      <button
        ref={handleRef}
        type="button"
        disabled={disabled}
        aria-label={`Drag ${displayTitle} to Planned timeline`}
        title="Drag onto the Planned timeline"
        className="flex w-11 shrink-0 touch-none cursor-grab items-center justify-center rounded-r-xl text-on-surface-variant/65 hover:bg-primary/8 hover:text-primary active:cursor-grabbing disabled:cursor-wait disabled:opacity-40"
      >
        <span className="material-symbols-outlined text-[20px]" aria-hidden>drag_indicator</span>
      </button>
    </div>
  )
}
