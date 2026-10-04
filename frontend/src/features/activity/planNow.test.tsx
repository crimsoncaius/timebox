import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { ActivityRepository, type ActivitySnapshot, type PlanNowRequest } from './activityRepository'
import { PlanNowEditor } from './PlanNowEditor'

afterEach(() => { localStorage.clear(); vi.unstubAllGlobals(); vi.restoreAllMocks() })
const at = '2026-10-04T10:20:59.732Z'
const type = { id: 1, name: 'Writing', created_at: at, updated_at: at }
const initial: ActivitySnapshot = { protocol: 'activity-online-v1', offline_ready: true, cursor: 0, server_at: at,
  reporting_timezone: 'UTC', current: null, records: [], plans: [], task_types: [type], plan_now_revision: 'before' }
const body: PlanNowRequest = { operation_id: 'save-1', revision: 'before', current_id: null,
  effective_at: at, minutes: 15, task_type_id: 1, task_id: null, name: 'Writing', replace_plan_ids: [] }

it('retries an unknown save after restart using the identical durable request', async () => {
  let calls = 0
  const received: PlanNowRequest[] = []
  const saved = { ...initial, cursor: 1, plan_now_revision: 'after', plan_now_undo: body.operation_id }
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (!init?.method) return new Response(JSON.stringify(calls ? saved : initial))
    received.push(JSON.parse(init.body)); calls++
    if (calls === 1) throw new Error('Response lost after commit')
    return new Response(JSON.stringify(saved))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  expect(await repository.planNow(body)).toBe(true)
  expect(repository.state.pending).toBe(true)
  expect(await repository.command('start', 1)).toBe(false)
  const restarted = new ActivityRepository(localStorage, work => work())
  expect(restarted.state.pending).toBe(true)
  await restarted.refresh()
  expect(received).toEqual([body, body])
  expect(restarted.state.pending).toBe(false)
  expect(restarted.state.snapshot?.plan_now_revision).toBe('after')
})

it('clears a rejected stale request and refreshes the preview', async () => {
  let rejected = false
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (init?.method) { rejected = true; return new Response(JSON.stringify({ detail: 'Review changed plans' }), { status: 409 }) }
    return new Response(JSON.stringify({ ...initial, plan_now_revision: rejected ? 'new' : 'before' }))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  expect(await repository.planNow(body)).toBe(false)
  expect(repository.state.pending).toBe(false)
  expect(repository.state.snapshot?.plan_now_revision).toBe('new')
})

it('requires explicit confirmation for later plans and captures time only on confirmation', async () => {
  const later = { id: 8, task_type_id: 1, task_id: null, name: 'Meeting', note: null, start_at: '2026-10-04T10:25:00Z', end_at: '2026-10-04T11:00:00Z' }
  const snapshot = { ...initial, plans: [later] }
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(snapshot))))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  const save = vi.spyOn(repository, 'planNow').mockResolvedValue(true)
  const clock = vi.spyOn(repository, 'now').mockReturnValue(Date.parse(at))
  render(<PlanNowEditor repository={repository} snapshot={snapshot} now={Date.parse(at)} title="Start tracking" typeId={1} name="Writing" taskId={null} keepCurrent={false} selectionFields={<span>Writing</span>} onClose={() => {}} onSaved={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: '15 min' }))
  fireEvent.click(screen.getByRole('button', { name: 'Start' }))
  expect(save).not.toHaveBeenCalled()
  clock.mockReturnValue(Date.parse(at) + 2000)
  await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Replace this time' })))
  await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({ effective_at: '2026-10-04T10:21:01.732Z', minutes: 15, replace_plan_ids: [8] })))
})

it('keeps the controlled dialog open when Escape arrives during a pending save', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(initial))))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  let resolve!: (value: boolean) => void
  vi.spyOn(repository, 'planNow').mockReturnValue(new Promise<boolean>(done => { resolve = done }))
  const close = vi.fn()
  render(<PlanNowEditor repository={repository} snapshot={initial} now={Date.parse(at)} title="Start tracking" typeId={1} name="Writing" taskId={null} keepCurrent={false} selectionFields={<span>Writing</span>} onClose={close} onSaved={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: '15 min' }))
  fireEvent.click(screen.getByRole('button', { name: 'Start' }))
  const event = new Event('cancel', { cancelable: true })
  screen.getByRole('dialog').dispatchEvent(event)
  expect(event.defaultPrevented).toBe(true)
  expect(close).not.toHaveBeenCalled()
  await act(async () => { resolve(false) })
  expect(screen.getByRole('alert')).toHaveTextContent('Could not save')
})
