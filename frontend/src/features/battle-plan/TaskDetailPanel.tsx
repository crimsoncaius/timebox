import { useCallback, useEffect, useRef, useState } from 'react'
import {
  defaultReminderIso,
  isoToZonedLocal,
  PRIORITY_LEVELS,
  STATUS_LABELS,
  TASK_STATUSES,
  zonedLocalToIso,
} from '../../lib/battlePlan'
import type {
  BattleTask,
  BattleTaskWrite,
  PriorityLevel,
  Project,
  TaskStatus,
  TaskType,
} from '../../lib/api'

type DeadlineMode = 'none' | 'date' | 'datetime'

type TaskDraft = {
  title: string
  description: string
  status: TaskStatus
  locationId: string
  urgency: PriorityLevel | null
  importance: PriorityLevel | null
  taskTypeId: string
  deadlineMode: DeadlineMode
  deadlineDate: string
  deadlineAt: string
  reminderAt: string
}

function draftFromTask(task: BattleTask, timezone: string): TaskDraft {
  return {
    title: task.title,
    description: task.description,
    status: task.status,
    locationId: task.project_id?.toString() ?? '',
    urgency: task.urgency,
    importance: task.importance,
    taskTypeId: task.task_type_id?.toString() ?? '',
    deadlineMode: task.deadline_at ? 'datetime' : task.deadline_date ? 'date' : 'none',
    deadlineDate: task.deadline_date ?? '',
    deadlineAt: isoToZonedLocal(task.deadline_at, timezone),
    reminderAt: isoToZonedLocal(task.reminder_at, timezone),
  }
}

export function TaskDetailPanel({
  task,
  projects,
  taskTypes,
  timezone,
  onClose,
  onPatch,
  onAddSubtask,
  onTrash,
}: {
  task: BattleTask
  projects: Project[]
  taskTypes: TaskType[]
  timezone: string
  onClose: () => void
  onPatch: (id: number, patch: Partial<BattleTaskWrite>) => Promise<void>
  onAddSubtask: (parentId: number, title: string) => Promise<void>
  onTrash: (id: number) => Promise<void>
}) {
  const initialDraftRef = useRef<TaskDraft>(draftFromTask(task, timezone))
  const initialDraft = initialDraftRef.current
  const [draft, setDraft] = useState<TaskDraft>(initialDraft)
  const [subtaskTitle, setSubtaskTitle] = useState('')
  const [isAddingSubtask, setIsAddingSubtask] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const dialogRef = useRef<HTMLElement>(null)
  const titleRef = useRef<HTMLInputElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(
    typeof document === 'undefined' ? null : document.activeElement as HTMLElement | null,
  )

  const setDraftField = <Key extends keyof TaskDraft>(key: Key, value: TaskDraft[Key]) => {
    setDraft((current) => ({ ...current, [key]: value }))
  }

  const isDirty = JSON.stringify(draft) !== JSON.stringify(initialDraft)
  const canSave = isDirty && !isSaving && Boolean(draft.title.trim())

  const requestClose = useCallback(() => {
    if (isDirty && !window.confirm('Discard your unsaved changes?')) return
    onClose()
  }, [isDirty, onClose])

  useEffect(() => {
    titleRef.current?.focus()
    const previousFocus = previousFocusRef.current
    return () => previousFocus?.focus()
  }, [])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        requestClose()
        return
      }
      if (event.key !== 'Tab') return

      const focusable = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ) ?? [])
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [requestClose])

  const setDeadlineMode = (mode: DeadlineMode) => {
    setDraft((current) => mode === 'none'
      ? { ...current, deadlineMode: mode, deadlineDate: '', deadlineAt: '', reminderAt: '' }
      : { ...current, deadlineMode: mode })
  }

  const changeDeadlineDate = (value: string) => {
    setDraft((current) => ({
      ...current,
      deadlineDate: value,
      reminderAt: current.reminderAt
        ? isoToZonedLocal(defaultReminderIso(value || null, null, timezone), timezone)
        : '',
    }))
  }

  const changeDeadlineAt = (value: string) => {
    const deadlineIso = value ? zonedLocalToIso(value, timezone) : null
    setDraft((current) => ({
      ...current,
      deadlineAt: value,
      reminderAt: current.reminderAt
        ? isoToZonedLocal(defaultReminderIso(null, deadlineIso, timezone), timezone)
        : '',
    }))
  }

  const toggleReminder = (enabled: boolean) => {
    if (!enabled) {
      setDraftField('reminderAt', '')
      return
    }
    const reminder = defaultReminderIso(
      draft.deadlineMode === 'date' ? draft.deadlineDate || null : null,
      draft.deadlineMode === 'datetime' && draft.deadlineAt
        ? zonedLocalToIso(draft.deadlineAt, timezone)
        : null,
      timezone,
    )
    if (!reminder) return
    if ('Notification' in window && Notification.permission === 'default') {
      void Notification.requestPermission()
    }
    setDraftField('reminderAt', isoToZonedLocal(reminder, timezone))
  }

  const save = async () => {
    if (!canSave) return
    setIsSaving(true)
    try {
      await onPatch(task.id, {
        title: draft.title.trim(),
        description: draft.description,
        status: draft.status,
        project_id: draft.locationId ? Number(draft.locationId) : null,
        task_type_id: draft.taskTypeId ? Number(draft.taskTypeId) : null,
        urgency: draft.urgency,
        importance: draft.importance,
        deadline_date: draft.deadlineMode === 'date' ? draft.deadlineDate || null : null,
        deadline_at: draft.deadlineMode === 'datetime' && draft.deadlineAt
          ? zonedLocalToIso(draft.deadlineAt, timezone)
          : null,
        reminder_at: draft.deadlineMode !== 'none' && draft.reminderAt
          ? zonedLocalToIso(draft.reminderAt, timezone)
          : null,
      })
      onClose()
    } finally {
      setIsSaving(false)
    }
  }

  const confirmTrash = async (item: BattleTask) => {
    if (!window.confirm(`Move “${item.title}” to Trash?`)) return
    await onTrash(item.id)
  }

  const closeButton = (
    <button
      type="button"
      aria-label="Close task details"
      onClick={requestClose}
      className="rounded-full p-1.5 leading-none text-[var(--task-detail-secondary)] transition-colors duration-120 ease-out hover:text-[var(--task-detail-primary)] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)]"
    >
      <span className="material-symbols-outlined text-[20px]" aria-hidden>close</span>
    </button>
  )

  return (
    <div
      className="fixed inset-0 z-80 flex items-start justify-center overflow-y-auto bg-black/45 px-4 py-6 min-[720px]:px-6 min-[720px]:py-14"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) requestClose()
      }}
    >
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label="Task details"
        className="task-detail-dialog relative w-full max-w-[61rem] overflow-hidden rounded-2xl border border-[var(--task-detail-border)] bg-[var(--task-detail-dialog)] font-body text-[var(--task-detail-primary)] shadow-[var(--task-detail-shadow)]"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="h-[53px] border-b border-[var(--task-detail-divider)] bg-[var(--task-detail-rail)] min-[720px]:hidden" aria-hidden />
        <div className="absolute top-3 right-4 z-10">{closeButton}</div>

        <div className="grid min-[720px]:grid-cols-[minmax(0,1fr)_22rem]">
          <main className="flex min-w-0 flex-col gap-6 px-5 py-7 min-[480px]:px-9 min-[720px]:px-9 min-[720px]:py-8">
            <div>
              <p className="mb-2.5 font-label text-[11px] uppercase tracking-[0.18em] text-[var(--task-detail-muted)]">Task details</p>
              <input
                ref={titleRef}
                aria-label="Title"
                placeholder="Untitled task"
                value={draft.title}
                onChange={(event) => setDraftField('title', event.target.value)}
                className="w-full border-0 bg-transparent p-0 font-headline text-[28px] font-light tracking-[-0.02em] text-[var(--task-detail-primary)] outline-none transition-colors duration-120 placeholder:text-[var(--task-detail-title-placeholder)] hover:bg-[var(--task-detail-field-hover)]"
              />
            </div>

            <textarea
              rows={5}
              aria-label="Description"
              placeholder="Notes and context"
              value={draft.description}
              onChange={(event) => setDraftField('description', event.target.value)}
              className="w-full resize-y border-0 border-l-2 border-[var(--task-detail-rule)] bg-transparent py-0.5 pr-0 pl-4 text-sm leading-[1.7] text-[var(--task-detail-primary)] outline-none transition-colors duration-120 placeholder:text-[var(--task-detail-muted)] hover:bg-[var(--task-detail-field-hover)]"
            />

            <section>
              <div className="flex items-center justify-between border-b border-[var(--task-detail-divider)] pb-2.5">
                <h2 className="font-label text-[11px] uppercase tracking-[0.18em] text-[var(--task-detail-muted)]">Subtasks</h2>
                <span className="text-xs text-[var(--task-detail-muted)]">{task.subtasks.length}</span>
              </div>

              {task.subtasks.length > 0 ? (
                <div className="divide-y divide-[var(--task-detail-divider)]">
                  {task.subtasks.map((subtask) => (
                    <div key={subtask.id} className="group flex items-center gap-3 py-3">
                      <input
                        type="checkbox"
                        aria-label={`${subtask.status === 'completed' ? 'Reopen' : 'Complete'} subtask ${subtask.title}`}
                        checked={subtask.status === 'completed'}
                        onChange={(event) => void onPatch(subtask.id, { status: event.target.checked ? 'completed' : 'open' })}
                        className="size-4 accent-[var(--task-detail-muted)] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)]"
                      />
                      <span className={`min-w-0 flex-1 text-sm ${subtask.status === 'completed' ? 'text-[var(--task-detail-muted)] line-through' : 'text-[var(--task-detail-primary)]'}`}>
                        {subtask.title}
                      </span>
                      <span className="text-xs text-[var(--task-detail-muted)]">{STATUS_LABELS[subtask.status]}</span>
                      <button
                        type="button"
                        aria-label={`Move subtask ${subtask.title} to Trash`}
                        onClick={() => void confirmTrash(subtask)}
                        className="rounded-full p-1 text-[var(--task-detail-muted)] opacity-70 transition-colors hover:text-[#b8514d] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)] group-hover:opacity-100"
                      >
                        <span className="material-symbols-outlined text-[17px]" aria-hidden>delete</span>
                      </button>
                    </div>
                  ))}
                </div>
              ) : null}

              <form
                className="mt-3.5 flex gap-2"
                onSubmit={async (event) => {
                  event.preventDefault()
                  const title = subtaskTitle.trim()
                  if (!title || isAddingSubtask) return
                  setIsAddingSubtask(true)
                  try {
                    await onAddSubtask(task.id, title)
                    setSubtaskTitle('')
                  } finally {
                    setIsAddingSubtask(false)
                  }
                }}
              >
                <input
                  value={subtaskTitle}
                  onChange={(event) => setSubtaskTitle(event.target.value)}
                  placeholder="Add a subtask"
                  aria-label="New subtask title"
                  className="min-w-0 flex-1 rounded-[10px] border border-[var(--task-detail-input-border)] bg-transparent px-3 py-[9px] text-sm text-[var(--task-detail-primary)] outline-none transition-colors duration-120 placeholder:text-[var(--task-detail-muted)] hover:border-[var(--task-detail-input-hover)] focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)]"
                />
                <button
                  type="submit"
                  disabled={isAddingSubtask || !subtaskTitle.trim()}
                  className="rounded-[10px] border border-[var(--task-detail-input-border)] bg-transparent px-4 py-[9px] text-sm text-[var(--task-detail-secondary)] transition-colors duration-120 ease-out hover:border-[var(--task-detail-input-hover)] hover:text-[var(--task-detail-primary)] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)] disabled:cursor-default disabled:opacity-40"
                >
                  Add
                </button>
              </form>
            </section>
          </main>

          <aside className="flex min-w-0 flex-col border-t border-[var(--task-detail-divider)] bg-[var(--task-detail-rail)] min-[720px]:border-t-0 min-[720px]:border-l min-[720px]:pt-[52px]">
            <div className="flex flex-col px-6 pb-2">
              <PropertySelect label="Status" value={draft.status} onChange={(value) => setDraftField('status', value as TaskStatus)}>
                {TASK_STATUSES.map((status) => <option key={status} value={status}>{STATUS_LABELS[status]}</option>)}
              </PropertySelect>

              <PropertySelect label="Location" value={draft.locationId} onChange={(value) => setDraftField('locationId', value)}>
                <option value="">Admin</option>
                {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
              </PropertySelect>

              <PropertyRow label="Urgency" compact>
                <PriorityControl label="Urgency" value={draft.urgency} onChange={(value) => setDraftField('urgency', value)} />
              </PropertyRow>

              <PropertyRow label="Importance" compact>
                <PriorityControl label="Importance" value={draft.importance} onChange={(value) => setDraftField('importance', value)} />
              </PropertyRow>

              <PropertySelect label="Task type" value={draft.taskTypeId} unset={!draft.taskTypeId} onChange={(value) => setDraftField('taskTypeId', value)}>
                <option value="">Unset</option>
                {taskTypes.map((taskType) => <option key={taskType.id} value={taskType.id}>{taskType.name}</option>)}
              </PropertySelect>

              <div>
                <PropertySelect label="Deadline" value={draft.deadlineMode} unset={draft.deadlineMode === 'none'} divider={false} onChange={(value) => setDeadlineMode(value as DeadlineMode)}>
                  <option value="none">No deadline</option>
                  <option value="date">Date only</option>
                  <option value="datetime">Date and time</option>
                </PropertySelect>

                {draft.deadlineMode !== 'none' ? (
                  <div className="space-y-3 border-t border-[var(--task-detail-divider)] py-3">
                    {draft.deadlineMode === 'date' ? (
                      <input type="date" aria-label="Deadline date" value={draft.deadlineDate} onChange={(event) => changeDeadlineDate(event.target.value)} className="w-full rounded-[10px] border border-[var(--task-detail-input-border)] bg-[var(--task-detail-input-surface)] px-3 py-2 text-sm text-[var(--task-detail-primary)] outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)]" />
                    ) : (
                      <input type="datetime-local" aria-label="Deadline date and time" value={draft.deadlineAt} onChange={(event) => changeDeadlineAt(event.target.value)} className="w-full rounded-[10px] border border-[var(--task-detail-input-border)] bg-[var(--task-detail-input-surface)] px-3 py-2 text-sm text-[var(--task-detail-primary)] outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)]" />
                    )}
                    <label className="flex items-center justify-between gap-3 text-[13px] text-[var(--task-detail-muted)]">
                      <span>Reminder</span>
                      <input type="checkbox" checked={Boolean(draft.reminderAt)} disabled={draft.deadlineMode === 'date' ? !draft.deadlineDate : !draft.deadlineAt} onChange={(event) => toggleReminder(event.target.checked)} className="accent-[var(--task-detail-muted)] disabled:opacity-40" />
                    </label>
                    {draft.reminderAt ? (
                      <input type="datetime-local" aria-label="Reminder date and time" value={draft.reminderAt} onChange={(event) => setDraftField('reminderAt', event.target.value)} className="w-full rounded-[10px] border border-[var(--task-detail-input-border)] bg-[var(--task-detail-input-surface)] px-3 py-2 text-sm text-[var(--task-detail-primary)] outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)]" />
                    ) : null}
                  </div>
                ) : null}
              </div>
            </div>

            <footer className="mt-auto flex items-center justify-between gap-3 border-t border-[var(--task-detail-divider)] px-6 py-4">
              <button type="button" onClick={() => void confirmTrash(task)} className="flex items-center gap-2 border-0 bg-transparent p-0 text-[13px] text-[#9f403d] transition-colors duration-120 ease-out hover:text-[#b8514d] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#b8514d]">
                <span className="material-symbols-outlined text-[18px]" aria-hidden>delete</span>
                Move to Trash
              </button>
              <button type="button" disabled={!canSave} onClick={() => void save()} className="rounded-[10px] bg-[#5d5e61] px-5 py-[9px] text-[13px] font-medium text-[#f7f7fa] transition-colors duration-120 ease-out hover:bg-[#6b6c70] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)] disabled:cursor-default disabled:opacity-40 disabled:hover:bg-[#5d5e61]">
                {isSaving ? 'Saving…' : 'Save'}
              </button>
            </footer>
          </aside>
        </div>
      </section>
    </div>
  )
}

function PropertyRow({ label, compact = false, divider = true, children }: { label: string; compact?: boolean; divider?: boolean; children: React.ReactNode }) {
  return (
    <div className={`flex items-center justify-between gap-4 ${compact ? 'py-2.5' : 'py-3'} ${divider ? 'border-b border-[var(--task-detail-divider)]' : ''}`}>
      <span className="shrink-0 text-[13px] text-[var(--task-detail-muted)]">{label}</span>
      {children}
    </div>
  )
}

function PropertySelect({ label, value, unset = false, divider = true, onChange, children }: { label: string; value: string; unset?: boolean; divider?: boolean; onChange: (value: string) => void; children: React.ReactNode }) {
  return (
    <PropertyRow label={label} divider={divider}>
      <select aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} className={`task-detail-select min-w-0 max-w-[12rem] cursor-pointer appearance-none border-0 bg-transparent py-1 pr-[22px] pl-2 text-right text-sm outline-none transition-colors duration-120 ease-out hover:bg-[var(--task-detail-field-hover)] focus-visible:ring-1 focus-visible:ring-[var(--task-detail-muted)] ${unset ? 'text-[var(--task-detail-secondary)]' : 'text-[var(--task-detail-primary)]'}`}>
        {children}
      </select>
    </PropertyRow>
  )
}

function PriorityControl({ label, value, onChange }: { label: string; value: PriorityLevel | null; onChange: (value: PriorityLevel | null) => void }) {
  const groupRef = useRef<HTMLDivElement>(null)

  const moveSelection = (event: React.KeyboardEvent<HTMLButtonElement>, level: PriorityLevel) => {
    const key = event.key
    if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(key)) return
    event.preventDefault()
    const currentIndex = PRIORITY_LEVELS.indexOf(level)
    let nextIndex = currentIndex
    if (key === 'Home') nextIndex = 0
    else if (key === 'End') nextIndex = PRIORITY_LEVELS.length - 1
    else if (key === 'ArrowLeft' || key === 'ArrowUp') nextIndex = (currentIndex - 1 + PRIORITY_LEVELS.length) % PRIORITY_LEVELS.length
    else nextIndex = (currentIndex + 1) % PRIORITY_LEVELS.length
    const next = PRIORITY_LEVELS[nextIndex]
    onChange(next)
    groupRef.current?.querySelector<HTMLButtonElement>(`[data-priority="${next}"]`)?.focus()
  }

  return (
    <div ref={groupRef} role="radiogroup" aria-label={label} className="flex gap-0.5 rounded-full bg-[var(--task-detail-track)] p-0.5">
      {PRIORITY_LEVELS.map((level) => {
        const selected = value === level
        const shortLabel = level === 'medium' ? 'Med' : `${level[0].toUpperCase()}${level.slice(1)}`
        const fullLabel = level[0].toUpperCase() + level.slice(1)
        return (
          <button key={level} type="button" role="radio" aria-checked={selected} aria-label={fullLabel} title={fullLabel} data-priority={level} tabIndex={selected || (!value && level === 'low') ? 0 : -1} onClick={() => onChange(selected ? null : level)} onKeyDown={(event) => moveSelection(event, level)} className={`rounded-full px-[9px] py-[3px] text-xs transition-[color,background-color,transform] duration-150 ease-out focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[var(--task-detail-secondary)] ${selected ? 'bg-[var(--task-detail-selected)] text-[var(--task-detail-primary)]' : 'bg-transparent text-[var(--task-detail-muted)] hover:text-[var(--task-detail-secondary)]'}`}>
            {shortLabel}
          </button>
        )
      })}
    </div>
  )
}
