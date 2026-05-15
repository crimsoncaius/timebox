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

  deleteTaskType: (
    id: number,
    opts?: { cascadeBlocks?: boolean; migrateBlocksTo?: number },
  ) => {
    const params = new URLSearchParams()
    if (opts?.cascadeBlocks) params.set('cascade_blocks', 'true')
    if (opts?.migrateBlocksTo != null) params.set('migrate_blocks_to', String(opts.migrateBlocksTo))
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
