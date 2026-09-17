import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ActivityRepository } from './activityRepository'
import { FocusController } from './focusController'
import { FocusHost } from './FocusMode'

const dependencies = vi.hoisted(() => ({
  repository: undefined as ActivityRepository | undefined,
  controller: undefined as FocusController | undefined,
}))

vi.mock('./activityRepository', async importOriginal => {
  const actual = await importOriginal<typeof import('./activityRepository')>()
  return { ...actual, getActivityRepository: () => dependencies.repository! }
})

vi.mock('./focusController', async importOriginal => {
  const actual = await importOriginal<typeof import('./focusController')>()
  return { ...actual, getFocusController: () => dependencies.controller! }
})

const current = {
  id: 7,
  task_type_id: 1,
  task_type: { id: 1, name: 'Writing', created_at: '', updated_at: '' },
  start_at: '2026-09-11T10:00:00Z',
  end_at: null,
  name: 'Writing',
  task_id: null,
  planned_block_id: null,
  task: null,
  note: null,
  created_at: '',
  updated_at: '',
}

beforeEach(async () => {
  localStorage.clear()
  dependencies.repository = new ActivityRepository(localStorage, work => work())
  dependencies.controller = new FocusController(localStorage)
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    protocol: 'activity-online-v1',
    offline_ready: true,
    cursor: 1,
    server_at: '2026-09-11T10:00:00Z',
    reporting_timezone: 'UTC',
    current,
    records: [current],
    task_types: [current.task_type],
  }), { headers: { 'Content-Type': 'application/json' } })))
  await dependencies.repository.refresh()
  expect(await dependencies.controller.enter(dependencies.repository)).toBe(true)
})

afterEach(() => {
  cleanup()
  dependencies.controller?.exit()
  dependencies.repository = undefined
  dependencies.controller = undefined
  localStorage.clear()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

it('shows Focus over the routed app and returns to the app after exiting', async () => {
  render(<FocusHost><div>Day content</div></FocusHost>)

  const focus = await screen.findByRole('main', { name: 'Focus' })
  expect(focus).toBeVisible()
  expect(screen.getByText('Writing')).toBeVisible()
  expect(screen.getByRole('button', { name: 'Exit Focus' })).toBeVisible()
  expect(screen.getByText('Day content').closest('[hidden]')).not.toBeNull()

  screen.getByRole('button', { name: 'Exit Focus' }).click()
  await waitFor(() => expect(screen.queryByRole('main', { name: 'Focus' })).not.toBeInTheDocument())
  expect(screen.getByText('Day content').closest('[hidden]')).toBeNull()
})
