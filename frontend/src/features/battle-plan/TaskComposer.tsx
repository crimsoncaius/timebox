import { useEffect, useRef, useState } from 'react'
import {
  addCalendarDays,
  dateInTimeZone,
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

type Picker = 'due' | 'urgency' | 'impact' | 'type'

const priorityIcons: Record<PriorityLevel, string> = {
  high: 'keyboard_double_arrow_up',
  medium: 'drag_handle',
  low: 'keyboard_arrow_down',
}

const chipBase = 'flex h-[30px] items-center rounded-full text-xs transition-[background,border-color] duration-[120ms]'

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
  const [projectId, setProjectId] = useState(() => fixedProjectId == null ? '' : String(fixedProjectId))
  const [taskTypeId, setTaskTypeId] = useState('')
  const [urgency, setUrgency] = useState<PriorityLevel | ''>('')
  const [importance, setImportance] = useState<PriorityLevel | ''>('')
  const [deadlineDate, setDeadlineDate] = useState('')
  const [deadlineAt, setDeadlineAt] = useState('')
  const [openPicker, setOpenPicker] = useState<Picker | null>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const dateInputRef = useRef<HTMLInputElement>(null)

  const today = dateInTimeZone(serverNowIso, timezone)
  const tomorrow = addCalendarDays(today, 1)
  const nextWeek = addCalendarDays(today, 7)
  const effectiveProjectId = fixedProjectId === undefined ? projectId : fixedProjectId == null ? '' : String(fixedProjectId)
  const projectName = effectiveProjectId
    ? projects.find((project) => project.id === Number(effectiveProjectId))?.name ?? 'Project'
    : 'Admin'
  const selectedType = taskTypes.find((taskType) => taskType.id === Number(taskTypeId))

  useEffect(() => {
    if (!openPicker) return
    const dismiss = (event: PointerEvent) => {
      const picker = (event.target as Element | null)?.closest?.('[data-picker]')
      if (picker?.getAttribute('data-picker') !== openPicker) setOpenPicker(null)
    }
    document.addEventListener('pointerdown', dismiss)
    return () => document.removeEventListener('pointerdown', dismiss)
  }, [openPicker])

  const reset = () => {
    setTitle('')
    setDescription('')
    setProjectId(fixedProjectId == null ? '' : String(fixedProjectId))
    setTaskTypeId('')
    setUrgency('')
    setImportance('')
    setDeadlineDate('')
    setDeadlineAt('')
    setOpenPicker(null)
  }

  const close = () => {
    if (busy) return
    reset()
    setOpen(false)
  }

  const updateTitle = (value: string) => {
    let next = value

    next = next.replace(/(^|\s)!((?:high|medium|low))(?=\s|$)/gi, (_match, prefix: string, level: string) => {
      setUrgency(level.toLowerCase() as PriorityLevel)
      return prefix
    })
    next = next.replace(/(^|\s)~((?:high|medium|low))(?=\s|$)/gi, (_match, prefix: string, level: string) => {
      setImportance(level.toLowerCase() as PriorityLevel)
      return prefix
    })

    if (fixedProjectId === undefined) {
      next = next.replace(/(^|\s)#([^\s]+)(?=\s|$)/g, (match, prefix: string, token: string) => {
        const normalized = normalizeShortcut(token)
        const project = projects.find((item) => normalizeShortcut(item.name) === normalized)
        if (!project) return match
        setProjectId(String(project.id))
        return prefix
      })
    }

    setTitle(next.replace(/ {2,}/g, ' '))
  }

  const setDueDate = (value: string) => {
    setDeadlineDate(value)
    setDeadlineAt('')
    setOpenPicker(null)
  }

  const showDatePicker = () => {
    setOpenPicker(null)
    const input = dateInputRef.current
    if (!input) return
    input.focus()
    const pickerInput = input as HTMLInputElement & { showPicker?: () => void }
    if (typeof pickerInput.showPicker === 'function') {
      try {
        pickerInput.showPicker()
      } catch {
        input.click()
      }
    } else {
      input.click()
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        aria-label={`Add ${STATUS_LABELS[status]} task`}
        className="mb-3 flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm text-on-surface-variant transition hover:bg-surface-container-lowest/70 hover:text-on-surface"
        onClick={() => setOpen(true)}
      >
        <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden>add</span>
        Add task
      </button>
    )
  }

  return (
    <form
      ref={formRef}
      aria-label="New task"
      className="mb-3 rounded-2xl border border-outline-variant/25 bg-surface-container-lowest px-4 pb-3 pt-4 shadow-[0_0_32px_rgba(45,52,53,0.045)]"
      onKeyDown={(event) => {
        if (event.key !== 'Escape') return
        event.preventDefault()
        event.stopPropagation()
        if (openPicker) setOpenPicker(null)
        else close()
      }}
      onSubmit={async (event) => {
        event.preventDefault()
        const cleanTitle = title.trim()
        if (!cleanTitle || busy) return
        setBusy(true)
        try {
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
            deadline_date: deadlineDate || null,
            deadline_at: deadlineAt ? zonedLocalToIso(deadlineAt, timezone) : null,
            reminder_at: null,
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
        placeholder="Task name  ·  #project  !urgency  ~impact"
        value={title}
        onChange={(event) => updateTitle(event.target.value)}
        className="w-full bg-transparent p-0 font-headline text-[17px] font-normal tracking-[-0.015em] text-on-surface outline-none placeholder:text-on-surface-variant/60"
      />
      <textarea
        aria-label="Task description"
        placeholder="Description"
        rows={1}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        className="mt-2 w-full resize-none bg-transparent p-0 text-[13px] leading-[1.6] outline-none placeholder:text-on-surface-variant/60"
      />

      <div className="mt-3.5 flex flex-wrap items-center gap-x-1.5 gap-y-[7px]">
        <div className={`${chipBase} max-w-full gap-1.5 border border-outline-variant/55 bg-surface-container px-[11px] text-on-surface`} title={`Location: ${projectName}`}>
          <span className="material-symbols-outlined text-[15px]" aria-hidden>folder_open</span>
          <span className="truncate">{projectName}</span>
        </div>

        <AttributeChip
          label="Due"
          icon="calendar_today"
          value={dueLabel(deadlineDate, today, tomorrow, nextWeek)}
          open={openPicker === 'due'}
          onToggle={() => setOpenPicker((current) => current === 'due' ? null : 'due')}
          onClear={() => { setDeadlineDate(''); setDeadlineAt('') }}
        >
          <ChipMenu label="Due" options={[
            { label: 'Today', icon: 'today', active: deadlineDate === today, onSelect: () => setDueDate(today) },
            { label: 'Tomorrow', icon: 'event', active: deadlineDate === tomorrow, onSelect: () => setDueDate(tomorrow) },
            { label: 'Next week', icon: 'date_range', active: deadlineDate === nextWeek, onSelect: () => setDueDate(nextWeek) },
            { label: 'Pick a date…', icon: 'calendar_month', active: Boolean(deadlineDate) && ![today, tomorrow, nextWeek].includes(deadlineDate), onSelect: showDatePicker },
          ]} />
        </AttributeChip>

        <AttributeChip
          label="Urgency"
          icon="bolt"
          value={capitalize(urgency)}
          open={openPicker === 'urgency'}
          onToggle={() => setOpenPicker((current) => current === 'urgency' ? null : 'urgency')}
          onClear={() => setUrgency('')}
        >
          <PriorityMenu label="Urgency" value={urgency} onSelect={(value) => { setUrgency(value); setOpenPicker(null) }} />
        </AttributeChip>

        <AttributeChip
          label="Impact"
          icon="flag"
          value={capitalize(importance)}
          open={openPicker === 'impact'}
          onToggle={() => setOpenPicker((current) => current === 'impact' ? null : 'impact')}
          onClear={() => setImportance('')}
        >
          <PriorityMenu label="Impact" value={importance} onSelect={(value) => { setImportance(value); setOpenPicker(null) }} />
        </AttributeChip>

        <AttributeChip
          label="Type"
          icon="sell"
          value={selectedType?.name ?? ''}
          open={openPicker === 'type'}
          onToggle={() => setOpenPicker((current) => current === 'type' ? null : 'type')}
          onClear={() => setTaskTypeId('')}
        >
          <ChipMenu label="Type" options={taskTypes.map((taskType) => ({
            label: taskType.name,
            icon: taskTypeIcon(taskType.name),
            active: taskType.id === Number(taskTypeId),
            onSelect: () => { setTaskTypeId(String(taskType.id)); setOpenPicker(null) },
          }))} />
        </AttributeChip>
      </div>

      <input
        ref={dateInputRef}
        aria-label="New task deadline date"
        type="date"
        value={deadlineDate}
        onChange={(event) => setDueDate(event.target.value)}
        className="sr-only"
        tabIndex={-1}
      />

      <div className="mt-3 flex items-center justify-end gap-1.5">
        <button
          type="button"
          aria-label="Cancel"
          disabled={busy}
          onClick={close}
          className="flex size-8 items-center justify-center rounded-full text-on-surface-variant transition hover:bg-surface-container-low disabled:opacity-40"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden>close</span>
        </button>
        <button
          type="submit"
          aria-label="Add task"
          disabled={busy || !title.trim()}
          className="flex size-[34px] items-center justify-center rounded-full bg-on-surface text-on-primary transition disabled:cursor-not-allowed disabled:bg-surface-container-high disabled:text-inverse-on-surface"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden>arrow_upward</span>
        </button>
      </div>
    </form>
  )
}

function AttributeChip({
  label,
  icon,
  value,
  open,
  onToggle,
  onClear,
  children,
}: {
  label: string
  icon: string
  value: string
  open: boolean
  onToggle: () => void
  onClear: () => void
  children: React.ReactNode
}) {
  const isSet = Boolean(value)
  return (
    <div className="relative" data-picker={label.toLowerCase()}>
      <div className={[
        chipBase,
        open
          ? 'border border-primary bg-primary-fixed text-on-surface'
          : isSet
            ? 'border border-outline-variant/55 bg-surface-container text-on-surface'
            : 'border border-dashed border-outline-variant/85 bg-transparent text-outline',
      ].join(' ')}>
        <button
          type="button"
          aria-label={label}
          aria-expanded={open}
          onClick={onToggle}
          className={`flex h-full min-w-0 items-center gap-1.5 ${isSet ? 'pl-[11px]' : 'px-[11px]'}`}
        >
          <span className="material-symbols-outlined text-[15px]" aria-hidden>{icon}</span>
          <span className="max-w-32 truncate">{value || label}</span>
        </button>
        {isSet ? (
          <button
            type="button"
            aria-label="Clear"
            onClick={onClear}
            className="h-[30px] py-0 pl-0.5 pr-[9px] text-[13px] text-outline transition-colors hover:text-on-surface"
          >
            ×
          </button>
        ) : null}
      </div>
      {open ? children : null}
    </div>
  )
}

function PriorityMenu({ label, value, onSelect }: { label: string; value: PriorityLevel | ''; onSelect: (value: PriorityLevel) => void }) {
  return (
    <ChipMenu label={label} options={[...PRIORITY_LEVELS].reverse().map((level) => ({
      label: capitalize(level),
      icon: priorityIcons[level],
      active: value === level,
      onSelect: () => onSelect(level),
    }))} />
  )
}

function ChipMenu({ label, options }: {
  label: string
  options: Array<{ label: string; icon: string; active: boolean; onSelect: () => void }>
}) {
  return (
    <div role="menu" aria-label={label} className="absolute left-0 top-[38px] z-30 min-w-[196px] rounded-xl border border-outline-variant/30 bg-surface-container-lowest p-1.5 shadow-[0_18px_40px_rgba(45,52,53,0.16)]">
      {options.map((option) => (
        <button
          key={option.label}
          type="button"
          role="menuitemradio"
          aria-checked={option.active}
          onClick={option.onSelect}
          className="flex w-full items-center gap-2.5 rounded-[10px] px-2.5 py-2 text-left text-[13px] text-on-surface transition hover:bg-surface-container-low"
        >
          <span className="material-symbols-outlined text-[17px] text-outline" aria-hidden>{option.icon}</span>
          <span className="min-w-0 flex-1 truncate">{option.label}</span>
          <span className={`text-xs text-primary ${option.active ? 'opacity-100' : 'opacity-0'}`} aria-hidden>✓</span>
        </button>
      ))}
    </div>
  )
}

function capitalize(value: string) {
  return value ? `${value[0].toUpperCase()}${value.slice(1)}` : ''
}

function normalizeShortcut(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '')
}

function taskTypeIcon(name: string) {
  const normalized = name.toLowerCase()
  if (normalized.includes('deep')) return 'psychology'
  if (normalized.includes('errand')) return 'directions_walk'
  if (normalized.includes('admin')) return 'inbox'
  return 'sell'
}

function dueLabel(value: string, today: string, tomorrow: string, nextWeek: string) {
  if (!value) return ''
  if (value === today) return 'Today'
  if (value === tomorrow) return 'Tomorrow'
  if (value === nextWeek) return 'Next week'
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${value}T12:00:00Z`))
}
