import { useState } from 'react'
import {
  addCalendarDays,
  dateInTimeZone,
  defaultReminderIso,
  isoToZonedLocal,
  PRIORITY_LEVELS,
  STATUS_LABELS,
  zonedLocalToIso,
} from '../../lib/battlePlan'
import type {
  BattleTaskWrite,
  PriorityLevel,
  Project,
  TaskStatus,
  TaskType,
} from '../../lib/api'

type DeadlineMode = 'none' | 'date' | 'datetime'

export function TaskComposer({
  status,
  projects,
  taskTypes,
  fixedProjectId,
  timezone,
  serverNowIso,
  onCreate,
}: {
  status: TaskStatus
  projects: Project[]
  taskTypes: TaskType[]
  fixedProjectId: number | null | undefined
  timezone: string
  serverNowIso: string
  onCreate: (task: BattleTaskWrite) => Promise<void>
}) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [projectId, setProjectId] = useState('')
  const [taskTypeId, setTaskTypeId] = useState('')
  const [urgency, setUrgency] = useState<PriorityLevel | ''>('')
  const [importance, setImportance] = useState<PriorityLevel | ''>('')
  const [deadlineMode, setDeadlineMode] = useState<DeadlineMode>('none')
  const [deadlineDate, setDeadlineDate] = useState('')
  const [deadlineAt, setDeadlineAt] = useState('')
  const [reminderAt, setReminderAt] = useState('')

  const today = dateInTimeZone(serverNowIso, timezone)
  const hasDeadline = deadlineMode === 'date' ? Boolean(deadlineDate) : deadlineMode === 'datetime' && Boolean(deadlineAt)

  const reset = () => {
    setTitle('')
    setDescription('')
    setProjectId('')
    setTaskTypeId('')
    setUrgency('')
    setImportance('')
    setDeadlineMode('none')
    setDeadlineDate('')
    setDeadlineAt('')
    setReminderAt('')
  }

  const close = () => {
    if (busy) return
    reset()
    setOpen(false)
  }

  const updateDate = (value: string) => {
    setDeadlineMode('date')
    setDeadlineDate(value)
    setDeadlineAt('')
    if (reminderAt) {
      setReminderAt(isoToZonedLocal(defaultReminderIso(value || null, null, timezone), timezone))
    }
  }

  const updateDateTime = (value: string) => {
    setDeadlineAt(value)
    if (reminderAt) {
      const deadline = value ? zonedLocalToIso(value, timezone) : null
      setReminderAt(isoToZonedLocal(defaultReminderIso(null, deadline, timezone), timezone))
    }
  }

  const setMode = (mode: DeadlineMode) => {
    setDeadlineMode(mode)
    if (mode !== deadlineMode) setReminderAt('')
    if (mode === 'none') {
      setDeadlineDate('')
      setDeadlineAt('')
      setReminderAt('')
    } else if (mode === 'date') {
      setDeadlineAt('')
    } else {
      setDeadlineDate('')
    }
  }

  const toggleReminder = (enabled: boolean) => {
    if (!enabled) {
      setReminderAt('')
      return
    }
    const deadline = deadlineAt ? zonedLocalToIso(deadlineAt, timezone) : null
    setReminderAt(isoToZonedLocal(
      defaultReminderIso(deadlineDate || null, deadline, timezone),
      timezone,
    ))
  }

  if (!open) {
    return (
      <button
        type="button"
        aria-label={`Add ${STATUS_LABELS[status]} task`}
        className="mb-3 flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm text-on-surface-variant transition hover:bg-surface-container-lowest/70 hover:text-on-surface dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container"
        onClick={() => setOpen(true)}
      >
        <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden>add</span>
        Add task
      </button>
    )
  }

  return (
    <form
      aria-label="New task"
      className="mb-3 rounded-2xl border border-outline-variant/25 bg-surface-container-lowest p-3 shadow-sm dark:border-dark-outline-variant/40 dark:bg-dark-surface-container-lowest"
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault()
          close()
        }
      }}
      onSubmit={async (event) => {
        event.preventDefault()
        const cleanTitle = title.trim()
        if (!cleanTitle || busy) return
        setBusy(true)
        try {
          const deadlineAtIso = deadlineMode === 'datetime' && deadlineAt
            ? zonedLocalToIso(deadlineAt, timezone)
            : null
          await onCreate({
            title: cleanTitle,
            description,
            status,
            project_id: fixedProjectId === undefined
              ? (projectId ? Number(projectId) : null)
              : fixedProjectId,
            task_type_id: taskTypeId ? Number(taskTypeId) : null,
            urgency: urgency || null,
            importance: importance || null,
            deadline_date: deadlineMode === 'date' && deadlineDate ? deadlineDate : null,
            deadline_at: deadlineAtIso,
            reminder_at: reminderAt ? zonedLocalToIso(reminderAt, timezone) : null,
          })
          reset()
          setOpen(false)
        } catch {
          // The page owns the API error message; keep this draft intact for retry.
        } finally {
          setBusy(false)
        }
      }}
    >
      <input
        autoFocus
        aria-label="Task title"
        placeholder="Task name"
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        className="w-full bg-transparent px-1 py-1 font-headline text-base outline-none placeholder:text-on-surface-variant/60"
      />
      <textarea
        aria-label="Task description"
        placeholder="Description"
        rows={2}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        className="mt-1 w-full resize-none bg-transparent px-1 py-1 text-sm outline-none placeholder:text-on-surface-variant/60"
      />

      <div className="mt-3 grid grid-cols-2 gap-2">
        {fixedProjectId === undefined ? (
          <CompactSelect label="Location" value={projectId} onChange={setProjectId}>
            <option value="">Admin</option>
            {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
          </CompactSelect>
        ) : (
          <ReadOnlyField
            label="Location"
            value={fixedProjectId == null
              ? 'Admin'
              : projects.find((project) => project.id === fixedProjectId)?.name ?? 'Project'}
          />
        )}
        <CompactSelect label="Task type" value={taskTypeId} onChange={setTaskTypeId}>
          <option value="">Unset</option>
          {taskTypes.map((taskType) => <option key={taskType.id} value={taskType.id}>{taskType.name}</option>)}
        </CompactSelect>
        <CompactSelect label="Urgency" value={urgency} onChange={(value) => setUrgency(value as PriorityLevel | '')}>
          <option value="">Unset</option>
          {PRIORITY_LEVELS.map((level) => <option key={level} value={level}>{level}</option>)}
        </CompactSelect>
        <CompactSelect label="Importance" value={importance} onChange={(value) => setImportance(value as PriorityLevel | '')}>
          <option value="">Unset</option>
          {PRIORITY_LEVELS.map((level) => <option key={level} value={level}>{level}</option>)}
        </CompactSelect>
      </div>

      <fieldset className="mt-3 rounded-xl bg-surface-container-low p-2.5 dark:bg-dark-surface-container">
        <legend className="px-1 font-label text-[10px] uppercase tracking-[0.12em] text-on-surface-variant">Deadline</legend>
        <div className="flex flex-wrap gap-1.5">
          <ShortcutButton active={deadlineMode === 'date' && deadlineDate === today} onClick={() => updateDate(today)}>Today</ShortcutButton>
          <ShortcutButton active={deadlineMode === 'date' && deadlineDate === addCalendarDays(today, 1)} onClick={() => updateDate(addCalendarDays(today, 1))}>Tomorrow</ShortcutButton>
          <select
            aria-label="Deadline mode"
            value={deadlineMode}
            onChange={(event) => setMode(event.target.value as DeadlineMode)}
            className="min-w-0 flex-1 rounded-lg bg-surface-container-lowest px-2 py-1.5 text-xs outline-none dark:bg-dark-surface-container-lowest"
          >
            <option value="none">No date</option>
            <option value="date">Date</option>
            <option value="datetime">Date & time</option>
          </select>
        </div>
        {deadlineMode === 'date' ? (
          <input aria-label="New task deadline date" type="date" value={deadlineDate} onChange={(event) => updateDate(event.target.value)} className="mt-2 w-full rounded-lg bg-surface-container-lowest px-2 py-1.5 text-xs dark:bg-dark-surface-container-lowest" />
        ) : null}
        {deadlineMode === 'datetime' ? (
          <input aria-label="New task deadline date and time" type="datetime-local" value={deadlineAt} onChange={(event) => updateDateTime(event.target.value)} className="mt-2 w-full rounded-lg bg-surface-container-lowest px-2 py-1.5 text-xs dark:bg-dark-surface-container-lowest" />
        ) : null}
        {deadlineMode !== 'none' ? (
          <div className="mt-2 border-t border-outline-variant/20 pt-2 dark:border-dark-outline-variant/30">
            <label className="flex items-center justify-between gap-2 text-xs">
              <span>Reminder</span>
              <input type="checkbox" checked={Boolean(reminderAt)} disabled={!hasDeadline} onChange={(event) => toggleReminder(event.target.checked)} />
            </label>
            {reminderAt ? (
              <input aria-label="New task reminder date and time" type="datetime-local" value={reminderAt} onChange={(event) => setReminderAt(event.target.value)} className="mt-2 w-full rounded-lg bg-surface-container-lowest px-2 py-1.5 text-xs dark:bg-dark-surface-container-lowest" />
            ) : null}
          </div>
        ) : null}
      </fieldset>

      <div className="mt-3 flex items-center justify-end gap-2">
        <button type="button" disabled={busy} onClick={close} className="rounded-lg px-3 py-2 text-xs text-on-surface-variant disabled:opacity-40">Cancel</button>
        <button type="submit" disabled={busy || !title.trim()} className="rounded-lg bg-primary px-3 py-2 text-xs font-medium text-on-primary disabled:opacity-40">
          {busy ? 'Adding…' : 'Add task'}
        </button>
      </div>
    </form>
  )
}

function CompactSelect({ label, value, onChange, children }: { label: string; value: string; onChange: (value: string) => void; children: React.ReactNode }) {
  return (
    <label className="min-w-0">
      <span className="sr-only">{label}</span>
      <select aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} className="w-full truncate rounded-lg bg-surface-container-low px-2 py-2 text-xs capitalize outline-none dark:bg-dark-surface-container">
        {children}
      </select>
    </label>
  )
}

function ReadOnlyField({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-lg bg-surface-container-low px-2 py-2 text-xs dark:bg-dark-surface-container" title={`${label}: ${value}`}>
      <span className="sr-only">{label}: </span>
      <span className="block truncate">{value}</span>
    </div>
  )
}

function ShortcutButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" aria-pressed={active} onClick={onClick} className={`rounded-lg px-2 py-1.5 text-xs ${active ? 'bg-primary text-on-primary' : 'bg-surface-container-lowest dark:bg-dark-surface-container-lowest'}`}>
      {children}
    </button>
  )
}
