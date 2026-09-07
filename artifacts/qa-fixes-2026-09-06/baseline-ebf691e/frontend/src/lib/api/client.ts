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

export async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
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

export async function fetchVoid(path: string, init?: RequestInit): Promise<void> {
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

