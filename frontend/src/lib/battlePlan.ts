import type { BattleTask, BattleTaskWrite, PriorityLevel, TaskStatus } from './api'
import { addDaysIso, calendarIsoDateInTimeZone, zonedLocalToIso } from './time'

export const TASK_STATUSES: TaskStatus[] = ['open', 'in_progress', 'blocked', 'completed']
export const PRIORITY_LEVELS: PriorityLevel[] = ['low', 'medium', 'high']

export const STATUS_LABELS: Record<TaskStatus, string> = {
  open: 'Open',
  in_progress: 'In progress',
  blocked: 'Blocked',
  completed: 'Completed',
}

export type DeadlineTone = 'overdue' | 'today' | 'tomorrow' | 'upcoming' | 'later'

export type DeadlineBadge = {
  label: string
  tone: DeadlineTone
}

// The board combines completion/progress with the independent Blocked condition.
export function taskColumn(task: Pick<BattleTask, 'status' | 'is_blocked'>): TaskStatus {
  if (task.status === 'completed') return 'completed'
  return task.is_blocked ? 'blocked' : task.status
}

export function taskColumnChange(column: Exclude<TaskStatus, 'completed'>): Partial<BattleTaskWrite> {
  return column === 'blocked' ? { is_blocked: true } : { status: column, is_blocked: false }
}

export type PlannedDateTone = 'today' | 'future' | 'past'

export type PlannedDateSummary = {
  primaryDate: string
  dateLabel: string
  relativeLabel: 'Today' | 'Tomorrow' | 'Yesterday' | null
  additionalCount: number
  tone: PlannedDateTone
}

const ISO_CALENDAR_DATE = /^(\d{4})-(\d{2})-(\d{2})$/

export function validPlannedDates(values: string[] | undefined): string[] {
  return [...new Set((values ?? []).filter((value) => {
    const match = ISO_CALENDAR_DATE.exec(value)
    if (!match) return false
    const date = new Date(`${value}T12:00:00Z`)
    return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
  }))].sort()
}

export function orderedPlannedDates(values: string[] | undefined, today: string): string[] {
  const dates = validPlannedDates(values)
  return [
    ...dates.filter((date) => date === today),
    ...dates.filter((date) => date > today),
    ...dates.filter((date) => date < today).reverse(),
  ]
}

export function formatPlannedDate(date: string, today: string, locale?: string): string {
  return new Intl.DateTimeFormat(locale, {
    month: 'short',
    day: 'numeric',
    ...(date.slice(0, 4) === today.slice(0, 4) ? {} : { year: 'numeric' }),
    timeZone: 'UTC',
  }).format(new Date(`${date}T12:00:00Z`))
}

export function plannedDateSummary(
  values: string[] | undefined,
  serverNowIso: string,
  timeZone: string,
  locale?: string,
): PlannedDateSummary | null {
  const today = calendarIsoDateInTimeZone(serverNowIso, timeZone)
  const dates = orderedPlannedDates(values, today)
  const primaryDate = dates[0]
  if (!primaryDate) return null
  const difference = calendarDayDifference(primaryDate, today)
  return {
    primaryDate,
    dateLabel: formatPlannedDate(primaryDate, today, locale),
    relativeLabel: difference === 0 ? 'Today' : difference === 1 ? 'Tomorrow' : difference === -1 ? 'Yesterday' : null,
    additionalCount: dates.length - 1,
    tone: difference === 0 ? 'today' : difference > 0 ? 'future' : 'past',
  }
}

function calendarDayDifference(left: string, right: string) {
  return Math.round(
    (new Date(`${left}T00:00:00Z`).getTime() - new Date(`${right}T00:00:00Z`).getTime())
      / 86_400_000,
  )
}

export function deadlineBadge(
  task: Pick<BattleTask, 'deadline_date' | 'deadline_at' | 'overdue'>,
  serverNowIso: string,
  timeZone: string,
): DeadlineBadge | null {
  if (!task.deadline_date && !task.deadline_at) return null

  const deadlineDate = task.deadline_date ?? calendarIsoDateInTimeZone(task.deadline_at!, timeZone)
  const today = calendarIsoDateInTimeZone(serverNowIso, timeZone)
  const difference = calendarDayDifference(deadlineDate, today)
  const displayDate = new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${deadlineDate}T12:00:00Z`))
  const displayTime = task.deadline_at
    ? new Intl.DateTimeFormat(undefined, {
        hour: 'numeric',
        minute: '2-digit',
        timeZone,
      }).format(new Date(task.deadline_at))
    : null
  const timeSuffix = displayTime ? ` · ${displayTime}` : ''

  if (task.overdue) {
    return { label: `Overdue · ${displayDate}${timeSuffix}`, tone: 'overdue' }
  }
  if (difference === 0) return { label: `Today${timeSuffix}`, tone: 'today' }
  if (difference === 1) return { label: `Tomorrow${timeSuffix}`, tone: 'tomorrow' }
  if (difference >= 2 && difference <= 7) {
    const weekday = new Intl.DateTimeFormat(undefined, {
      weekday: 'long',
      timeZone: 'UTC',
    }).format(new Date(`${deadlineDate}T12:00:00Z`))
    return { label: `${weekday}${timeSuffix}`, tone: 'upcoming' }
  }
  return { label: `${displayDate}${timeSuffix}`, tone: 'later' }
}

export function priorityRank(value: PriorityLevel | null) {
  return value === 'high' ? 3 : value === 'medium' ? 2 : value === 'low' ? 1 : 0
}

export function deadlineRank(task: BattleTask) {
  if (task.deadline_at) return new Date(task.deadline_at).getTime()
  if (task.deadline_date) return new Date(`${task.deadline_date}T23:59:59`).getTime()
  return Number.POSITIVE_INFINITY
}


export function isoToZonedLocal(iso: string | null, timeZone: string): string {
  if (!iso) return ''
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date(iso))
      .map((part) => [part.type, part.value]),
  )
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

export function defaultReminderIso(now: string, timeZone: string): string {
  const local = isoToZonedLocal(now, timeZone)
  const date = local.slice(0, 10)
  const instant = new Date(now)
  // Skip a repeated local hour: the editor stores local time without an offset.
  let next = new Date(Math.floor(instant.getTime() / 60_000) * 60_000 + 60_000)
  while (isoToZonedLocal(next.toISOString(), timeZone).slice(14, 16) !== '00'
    || isoToZonedLocal(next.toISOString(), timeZone) <= local) {
    next = new Date(next.getTime() + 60_000)
  }
  if (calendarIsoDateInTimeZone(next.toISOString(), timeZone) === date) return next.toISOString()
  return zonedLocalToIso(`${addDaysIso(date, 1)}T09:00`, timeZone)
}
