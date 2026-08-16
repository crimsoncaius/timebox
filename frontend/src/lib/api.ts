export type BlockLane = 'planned' | 'actual'

/** Client-only placement before POST /days/.../blocks (draft-first creation). */
export type BlockDraftPlacement = {
  lane: BlockLane
  start_minute: number
  end_minute: number
  task_id?: number | null
  task_type_id?: number | null
}

export interface TaskType {
  id: number
  name: string
  created_at: string
  updated_at: string
  usage_count?: number
  task_usage_count?: number
}

export type TaskStatus = 'open' | 'in_progress' | 'blocked' | 'completed'
export type PriorityLevel = 'low' | 'medium' | 'high'
export type TaskCollection = 'active' | 'archived' | 'trash'
export type RecurrenceMode = 'scheduled' | 'quota'
export type RecurrenceStatus = 'active' | 'paused' | 'ended'
export type RecurrenceFrequency = 'daily' | 'weekly' | 'monthly'

export interface Project {
  id: number
  name: string
  description: string
  deadline_date: string | null
  deadline_at: string | null
  created_at: string
  updated_at: string
}

export interface BattleTask {
  id: number
  parent_id: number | null
  parent_title?: string | null
  project_id: number | null
  project: Project | null
  task_type_id: number | null
  task_type: TaskType | null
  recurring_template_id?: number | null
  recurring_template_title?: string | null
  occurrence_key?: string | null
  recurrence_kind?: 'scheduled' | 'checklist' | 'quota_parent' | 'quota_session' | null
  quota_period_start?: string | null
  quota_period_end?: string | null
  expected_sessions?: number | null
  session_index?: number | null
  quota_completed?: number | null
  title: string
  description: string
  ready_to_plan?: boolean
  status: TaskStatus
  urgency: PriorityLevel | null
  importance: PriorityLevel | null
  deadline_date: string | null
  deadline_at: string | null
  reminder_at: string | null
  reminder_delivered_at: string | null
  position: number
  archived_at: string | null
  deleted_at: string | null
  created_at: string
  updated_at: string
  overdue: boolean
  subtasks: BattleTask[]
}

export interface BattleTaskList {
  items: BattleTask[]
  timezone: string
  server_now_iso: string
}

export interface DueReminder {
  id: number
  title: string
  deadline_date: string | null
  deadline_at: string | null
  reminder_at: string
}

export type ProjectWrite = {
  name: string
  description?: string
  deadline_date?: string | null
  deadline_at?: string | null
}

export type BattleTaskWrite = {
  title: string
  description?: string
  ready_to_plan?: boolean
  status?: TaskStatus
  project_id?: number | null
  parent_id?: number | null
  task_type_id?: number | null
  urgency?: PriorityLevel | null
  importance?: PriorityLevel | null
  deadline_date?: string | null
  deadline_at?: string | null
  reminder_at?: string | null
}

export interface TimeBlock {
  id: number
  lane: BlockLane
  task_type_id: number
  task_type: TaskType
  task_id?: number | null
  task?: Pick<BattleTask, 'id' | 'title' | 'status' | 'task_type_id'> | null
  note: string | null
  /** Present on Actual blocks created via "complete as planned" from a Planned block. */
  planned_block_id?: number | null
  start_minute: number
  end_minute: number
  created_at: string
  updated_at: string
}

export interface DayMeta {
  timezone: string
  today: string
  server_now_iso: string
}

export interface DayRead {
  id: number
  date: string
  start_hour: number
  end_hour: number
  show_full_day: boolean
  created_at: string
  updated_at: string
  time_blocks: TimeBlock[]
  meta: DayMeta
}

export interface DayListItem {
  id: number
  date: string
  start_hour: number
  end_hour: number
  show_full_day: boolean
  updated_at: string
}

export interface HealthResponse {
  status: string
  today: string
  timezone: string
}

export interface SettingsRead {
  id: number
  start_hour: number
  end_hour: number
  show_full_day: boolean
  week_start?: 'monday' | 'sunday'
  created_at: string
  updated_at: string
}

export type RecurrenceWindow = { key: string; start: string; end: string }

export interface RecurringTemplate {
  id: number
  title: string
  description: string
  project_id: number | null
  project: Project | null
  task_type_id: number | null
  task_type: TaskType | null
  mode: RecurrenceMode
  status: RecurrenceStatus
  frequency: RecurrenceFrequency
  interval: number
  weekdays: number[]
  month_day: number | null
  quota_count: number | null
  start_date: string
  end_date: string | null
  cycle_limit: number | null
  urgency: PriorityLevel | null
  importance: PriorityLevel | null
  paused_at: string | null
  ended_at: string | null
  created_at: string
  updated_at: string
  checklist_items: Array<{ id: number; title: string; position: number }>
  upcoming: RecurrenceWindow[]
  current_tasks: Array<{ id: number; title: string; deadline_date: string | null; overdue: boolean }>
  cadence: string
  next_occurrence: string | null
}

export type RecurrenceRuleWrite = {
  mode: RecurrenceMode
  frequency: RecurrenceFrequency
  interval: number
  weekdays?: number[]
  month_day?: number | null
  quota_count?: number | null
  start_date: string
  end_date?: string | null
  cycle_limit?: number | null
}

export type RecurringTemplateWrite = RecurrenceRuleWrite & {
  title: string
  description?: string
  project_id?: number | null
  task_type_id?: number | null
  urgency?: PriorityLevel | null
  importance?: PriorityLevel | null
  checklist_titles?: string[]
  confirm_backfill?: boolean
}

export type RecurrencePreview = {
  upcoming: RecurrenceWindow[]
  past_cycles: number
  past_tasks: number
}

function apiPrefix(): string {
  return import.meta.env.VITE_API_BASE_URL ?? '/api'
}

/** Avoid hung "Loading today…" when the API is down or the dev proxy cannot connect. */
const DEFAULT_FETCH_TIMEOUT_MS = 12_000

function timeoutSignal(ms: number): AbortSignal {
  if (typeof AbortSignal !== 'undefined' && 'timeout' in AbortSignal) {
    return AbortSignal.timeout(ms)
  }
  const c = new AbortController()
  setTimeout(() => c.abort(new DOMException('The operation timed out.', 'TimeoutError')), ms)
  return c.signal
}

function mergeSignals(user: AbortSignal | null | undefined, timeout: AbortSignal): AbortSignal {
  if (!user) return timeout
  if (typeof AbortSignal !== 'undefined' && 'any' in AbortSignal) {
    return AbortSignal.any([user, timeout])
  }
  const c = new AbortController()
  const forward = (s: AbortSignal) => {
    if (s.aborted) {
      c.abort(s.reason)
      return
    }
    s.addEventListener('abort', () => c.abort(s.reason), { once: true })
  }
  forward(user)
  forward(timeout)
  return c.signal
}

function isAbortError(e: unknown): boolean {
  return (
    (e instanceof DOMException && e.name === 'AbortError') ||
    (e instanceof Error && e.name === 'TimeoutError')
  )
}

/** Matches backend 409 detail when DELETE /task-types/:id has blocks (no cascade/migrate). */
export const TASK_TYPE_STILL_IN_USE_DETAIL = 'Task type is still used by existing blocks'

/** HTTP error from the API with status and a readable message (parsed FastAPI `detail`). */
export class ApiHttpError extends Error {
  readonly status: number
  readonly detailMessage: string

  constructor(status: number, detailMessage: string) {
    super(detailMessage)
    this.name = 'ApiHttpError'
    this.status = status
    this.detailMessage = detailMessage
  }
}

function formatFastApiDetail(detail: unknown): string {
  if (typeof detail === 'string') return detail
  if (Array.isArray(detail)) {
    return detail
      .map((item) => {
        if (item && typeof item === 'object' && 'msg' in item) {
          return String((item as { msg: unknown }).msg)
        }
        return JSON.stringify(item)
      })
      .join('; ')
  }
  if (detail && typeof detail === 'object') return JSON.stringify(detail)
  return 'Request failed'
}

function parseApiErrorBody(text: string): string {
  const trimmed = text.trim()
  if (!trimmed) return 'Request failed'
  try {
    const data = JSON.parse(trimmed) as { detail?: unknown }
    if ('detail' in data && data.detail !== undefined) return formatFastApiDetail(data.detail)
  } catch {
    /* keep raw */
  }
  return trimmed
}

async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
  const timeout = timeoutSignal(DEFAULT_FETCH_TIMEOUT_MS)
  let res: Response
  try {
    res = await fetch(`${apiPrefix()}${path}`, {
      ...init,
      signal: mergeSignals(init?.signal, timeout),
      headers: {
        'Content-Type': 'application/json',
        ...init?.headers,
      },
    })
  } catch (e) {
    if (isAbortError(e)) {
      throw new Error(
        `No API response within ${DEFAULT_FETCH_TIMEOUT_MS / 1000}s. Start the backend (see README) or check the Vite /api proxy target.`,
      )
    }
    throw e
  }
  if (!res.ok) {
    const text = await res.text()
    throw new ApiHttpError(res.status, parseApiErrorBody(text || res.statusText))
  }
  return res.json() as Promise<T>
}

async function fetchVoid(path: string, init?: RequestInit): Promise<void> {
  const timeout = timeoutSignal(DEFAULT_FETCH_TIMEOUT_MS)
  let res: Response
  try {
    res = await fetch(`${apiPrefix()}${path}`, {
      ...init,
      signal: mergeSignals(init?.signal, timeout),
      headers: {
        'Content-Type': 'application/json',
        ...init?.headers,
      },
    })
  } catch (e) {
    if (isAbortError(e)) {
      throw new Error(
        `No API response within ${DEFAULT_FETCH_TIMEOUT_MS / 1000}s. Start the backend (see README) or check the Vite /api proxy target.`,
      )
    }
    throw e
  }
  if (!res.ok) {
    const text = await res.text()
    throw new ApiHttpError(res.status, parseApiErrorBody(text || res.statusText))
  }
}

export const api = {
  health: () => fetchJson<HealthResponse>('/health'),

  getDay: (date: string) => fetchJson<DayRead>(`/days/${date}`),

  getSettings: () => fetchJson<SettingsRead>('/settings'),

  patchSettings: (body: Partial<{ start_hour: number; end_hour: number; show_full_day: boolean; week_start: 'monday' | 'sunday' }>) =>
    fetchJson<SettingsRead>('/settings', {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  listTaskTypes: () => fetchJson<TaskType[]>('/task-types'),

  createTaskType: (body: { name: string }) =>
    fetchJson<TaskType>('/task-types', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  patchTaskType: (id: number, body: Partial<{ name: string }>) =>
    fetchJson<TaskType>(`/task-types/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  deleteTaskType: (
    id: number,
    opts?: { cascadeBlocks?: boolean; migrateBlocksTo?: number; clearTaskReferences?: boolean },
  ) => {
    const params = new URLSearchParams()
    if (opts?.cascadeBlocks) params.set('cascade_blocks', 'true')
    if (opts?.migrateBlocksTo != null) params.set('migrate_blocks_to', String(opts.migrateBlocksTo))
    if (opts?.clearTaskReferences) params.set('clear_task_references', 'true')
    const qs = params.toString()
    return fetchVoid(`/task-types/${id}${qs ? `?${qs}` : ''}`, {
      method: 'DELETE',
    })
  },

  createBlock: (
    date: string,
    body: {
      lane: BlockLane
      task_type_id: number
      task_id?: number | null
      note?: string | null
      start_minute: number
      end_minute: number
    },
  ) =>
    fetchJson<DayRead>(`/days/${date}/blocks`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  patchBlock: (
    date: string,
    blockId: number,
    body: Partial<{ task_type_id: number; task_id: number | null; note: string | null; start_minute: number; end_minute: number }>,
  ) =>
    fetchJson<DayRead>(`/days/${date}/blocks/${blockId}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  deleteBlock: (date: string, blockId: number) =>
    fetchJson<DayRead>(`/days/${date}/blocks/${blockId}`, {
      method: 'DELETE',
    }),

  completeBlockAsPlanned: (date: string, blockId: number) =>
    fetchJson<DayRead>(`/days/${date}/blocks/${blockId}/complete-as-planned`, {
      method: 'POST',
    }),

  listDays: (limit = 60) => fetchJson<DayListItem[]>(`/days?limit=${limit}`),

  listProjects: () => fetchJson<Project[]>('/projects'),

  createProject: (body: ProjectWrite) =>
    fetchJson<Project>('/projects', { method: 'POST', body: JSON.stringify(body) }),

  patchProject: (id: number, body: Partial<ProjectWrite>) =>
    fetchJson<Project>(`/projects/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),

  deleteProject: (id: number) => fetchVoid(`/projects/${id}`, { method: 'DELETE' }),

  listBattleTasks: (state: TaskCollection = 'active') =>
    fetchJson<BattleTaskList>(`/tasks?state=${state}`),

  createBattleTask: (body: BattleTaskWrite) =>
    fetchJson<BattleTask>('/tasks', { method: 'POST', body: JSON.stringify(body) }),

  patchBattleTask: (id: number, body: Partial<BattleTaskWrite>) =>
    fetchJson<BattleTask>(`/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),

  reorderBattleTasks: (
    placements: Array<{ task_id: number; status: TaskStatus; position: number }>,
  ) =>
    fetchVoid('/tasks/reorder', {
      method: 'POST',
      body: JSON.stringify({ placements }),
    }),

  archiveBattleTasks: (taskIds: number[]) =>
    fetchVoid('/tasks/archive-completed', {
      method: 'POST',
      body: JSON.stringify({ task_ids: taskIds }),
    }),

  unarchiveBattleTask: (id: number) =>
    fetchVoid(`/tasks/${id}/unarchive`, { method: 'POST' }),

  trashBattleTask: (id: number) =>
    fetchJson<BattleTask>(`/tasks/${id}`, { method: 'DELETE' }),

  restoreBattleTask: (id: number) =>
    fetchVoid(`/tasks/${id}/restore`, { method: 'POST' }),

  permanentlyDeleteBattleTask: (id: number) =>
    fetchVoid(`/tasks/${id}/permanent`, { method: 'DELETE' }),

  dueReminders: () => fetchJson<DueReminder[]>('/reminders/due'),

  acknowledgeReminder: (id: number) =>
    fetchVoid(`/reminders/${id}/delivered`, { method: 'POST' }),

  previewRecurrence: (body: RecurrenceRuleWrite) =>
    fetchJson<RecurrencePreview>('/recurring-templates/preview', {
      method: 'POST', body: JSON.stringify(body),
    }),

  listRecurringTemplates: (status: RecurrenceStatus = 'active') =>
    fetchJson<RecurringTemplate[]>(`/recurring-templates?status=${status}`),

  getRecurringTemplate: (id: number) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}`),

  createRecurringTemplate: (body: RecurringTemplateWrite) =>
    fetchJson<RecurringTemplate>('/recurring-templates', {
      method: 'POST', body: JSON.stringify(body),
    }),

  patchRecurringTemplate: (id: number, body: Partial<RecurringTemplateWrite>) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}`, {
      method: 'PATCH', body: JSON.stringify(body),
    }),

  pauseRecurringTemplate: (id: number) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}/pause`, { method: 'POST' }),

  resumeRecurringTemplate: (id: number) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}/resume`, { method: 'POST' }),

  endRecurringTemplate: (id: number) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}/end`, { method: 'POST' }),

  deleteRecurringTemplate: (id: number) =>
    fetchVoid(`/recurring-templates/${id}`, { method: 'DELETE' }),
}
