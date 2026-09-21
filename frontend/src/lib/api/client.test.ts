import { afterEach, expect, it, vi } from 'vitest'
import { fetchJson } from './client'

afterEach(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
})

it.each([
  [undefined, '/api/health'],
  ['', '/api/health'],
  ['https://api.example.test', 'https://api.example.test/health'],
])('routes health requests with API base URL %s', async (baseUrl, expectedUrl) => {
  vi.stubEnv('VITE_API_BASE_URL', baseUrl)
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'ok' })))
  vi.stubGlobal('fetch', fetchMock)

  await expect(fetchJson('/health')).resolves.toEqual({ status: 'ok' })
  expect(fetchMock).toHaveBeenCalledWith(expectedUrl, expect.any(Object))
})
