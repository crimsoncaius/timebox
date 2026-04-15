export type BlockLane = 'planned' | 'actual'

/** Client-only placement before POST /days/.../blocks (draft-first creation). */
export type BlockDraftPlacement = {
  lane: BlockLane
  start_minute: number
  end_minute: number
}

export interface TaskType {
  id: number
  name: string
  created_at: string
  updated_at: string
}

export interface TimeBlock {
  id: number
  lane: BlockLane
  task_type_id: number
  task_type: TaskType
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
  created_at: string
  updated_at: string
}

function apiPrefix(): string {
  return import.meta.env.VITE_API_BASE_URL ?? '/api'
}

async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${apiPrefix()}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || res.statusText)
  }
  return res.json() as Promise<T>
}

async function fetchVoid(path: string, init?: RequestInit): Promise<void> {
  const res = await fetch(`${apiPrefix()}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || res.statusText)
  }
}

export const api = {
  health: () => fetchJson<HealthResponse>('/health'),

  getDay: (date: string) => fetchJson<DayRead>(`/days/${date}`),

  getSettings: () => fetchJson<SettingsRead>('/settings'),

  patchSettings: (body: Partial<{ start_hour: number; end_hour: number; show_full_day: boolean }>) =>
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

  deleteTaskType: (id: number) =>
    fetchVoid(`/task-types/${id}`, {
      method: 'DELETE',
    }),

  createBlock: (
    date: string,
    body: {
      lane: BlockLane
      task_type_id: number
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
    body: Partial<{ task_type_id: number; note: string | null; start_minute: number; end_minute: number }>,
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
}
