export type BlockLane = 'planned' | 'actual'

export interface TimeBlock {
  id: number
  lane: BlockLane
  title: string
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

export const api = {
  health: () => fetchJson<HealthResponse>('/health'),

  getDay: (date: string) => fetchJson<DayRead>(`/days/${date}`),

  patchDay: (
    date: string,
    body: Partial<{ start_hour: number; end_hour: number; show_full_day: boolean }>,
  ) =>
    fetchJson<DayRead>(`/days/${date}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  createBlock: (
    date: string,
    body: { lane: BlockLane; title?: string; start_minute: number; end_minute: number },
  ) =>
    fetchJson<DayRead>(`/days/${date}/blocks`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  patchBlock: (
    date: string,
    blockId: number,
    body: Partial<{ title: string | null; start_minute: number; end_minute: number }>,
  ) =>
    fetchJson<DayRead>(`/days/${date}/blocks/${blockId}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  deleteBlock: (date: string, blockId: number) =>
    fetchJson<DayRead>(`/days/${date}/blocks/${blockId}`, {
      method: 'DELETE',
    }),

  listDays: (limit = 60) => fetchJson<DayListItem[]>(`/days?limit=${limit}`),
}
