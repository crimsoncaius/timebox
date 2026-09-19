import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Layout } from './Layout'

function response(data: unknown) {
  return new Response(JSON.stringify(data), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('Layout navigation', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      if (url.includes('/health')) return response({ status: 'ok', today: '2026-09-19', timezone: 'UTC' })
      return response(null)
    }) as typeof fetch
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  function renderAt(path: string) {
    render(<MemoryRouter initialEntries={[path]}><Layout><p>content</p></Layout></MemoryRouter>)
  }

  it('offers Day, Chronicle, Battle Plan and Assistant, with Task Types no longer a destination', () => {
    renderAt('/history')

    for (const name of ['Day', 'Chronicle', 'Battle Plan', 'Assistant']) {
      expect(screen.getByRole('link', { name })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: `${name} mobile navigation` })).toBeInTheDocument()
    }
    expect(screen.queryByRole('link', { name: /task types/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Settings' })).toHaveAttribute('href', '/settings')
  })

  it('keeps Battle Plan selected on the Task Types page', () => {
    renderAt('/task-types')

    expect(screen.getByRole('link', { name: 'Battle Plan' })).toHaveClass('border-primary')
    expect(screen.getByRole('link', { name: 'Battle Plan mobile navigation' })).toHaveClass('bg-surface-container-low')
  })
})
