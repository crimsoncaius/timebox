import { afterEach, expect, it, vi } from 'vitest'
import { BrowserCheckIns } from './browserCheckIns'
import { ActivityRepository, type CheckInEvent, type CheckInState } from './activityRepository'

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); localStorage.clear() })
it('unsupported browsers never request detection permission or infer inactivity', async () => {
  const repository = new ActivityRepository(localStorage, work => work())
  const checkIn = vi.spyOn(repository, 'checkIn')
  const adapter = new BrowserCheckIns(repository)
  await adapter.start()
  expect(adapter.getSnapshot().capability).toBe('unsupported')
  await adapter.requestDetection()
  expect(checkIn).not.toHaveBeenCalled()
  adapter.stop()
})

async function supported() {
  vi.useFakeTimers()
  let now = Date.parse('2026-09-11T10:00:00Z')
  let lastInput = performance.now()
  let unavailable: 'no' | 'null' | 'error' = 'no'
  let connected = true
  const permission = new EventTarget() as EventTarget & { state: PermissionState }
  permission.state = 'granted'
  vi.stubGlobal('navigator', { permissions: { query: async () => permission } })
  class Detector extends EventTarget {
    userState: string | null = null
    screenState: string | null = null
    static requestPermission = vi.fn(async () => 'granted')
    async start({ threshold }: { threshold: number }) {
      if (unavailable === 'error') throw new Error('Sensor unavailable')
      if (unavailable === 'null') return
      this.userState = performance.now() - lastInput >= threshold ? 'idle' : 'active'
      this.screenState = 'unlocked'
    }
  }
  vi.stubGlobal('IdleDetector', Detector)
  const row = { id: 1, name: 'Reading', task_type_id: 1, task_type: { id: 1, name: 'reading' }, start_at: new Date(now).toISOString(), end_at: null }
  let canonical = { protocol: 'activity-online-v1', offline_ready: true, cursor: 1, server_at: new Date(now).toISOString(), current: row, records: [row], acknowledgement: null as { operation_id: string; outcome: string } | null, check_in: { enabled: true, threshold_minutes: 15, generation: 'reading', rearm: 0, armed_at: new Date(now).toISOString(), active_at: null, question: null } as CheckInState }
  const commands: { operation_id: string; device_id: string; check_in: CheckInEvent }[] = []
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (!connected) throw new Error('Offline')
    if (init?.method === 'POST') {
      const command = JSON.parse(init.body); commands.push(command)
      const event = command.check_in
      if (event.action === 'candidate') canonical.check_in.question = { id: 'reading:0', created_at: new Date(now).toISOString(), candidate_device: command.device_id, candidate_operation_id: command.operation_id, delivery: null }
      if (event.action === 'observe') canonical.check_in.active_at = event.coverage_end
      if (event.action === 'delivery') canonical.check_in.question!.delivery = { operation_id: command.operation_id, device_id: command.device_id, at: new Date(now).toISOString(), dismissed: false }
      if (event.action === 'confirm') canonical.check_in = { ...canonical.check_in, question: null, rearm: canonical.check_in.rearm + 1 }
      canonical = { ...canonical, cursor: canonical.cursor + 1, server_at: new Date(now).toISOString(), acknowledgement: { operation_id: command.operation_id, outcome: 'applied' } }
    }
    return new Response(JSON.stringify(canonical))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  vi.spyOn(repository, 'now').mockImplementation(() => now)
  await repository.refresh(); await repository.setCheckInPreferences({ enabled: true, thresholdMinutes: 15 })
  const adapter = new BrowserCheckIns(repository)
  await adapter.start()
  return { repository, adapter, commands, permission, Detector,
    idle: () => { lastInput = performance.now() - 15 * 60000 }, calibrate: (milliseconds: number) => { now += milliseconds }, sensor: (state: typeof unavailable) => { unavailable = state }, offline: () => { connected = false }, online: () => { connected = true },
    advance: async (milliseconds: number) => { for (let elapsed = 0; elapsed < milliseconds; elapsed += 15000) { now += 15000; await vi.advanceTimersByTimeAsync(15000) } },
  }
}

it('native activity is a conservative lower bound; idle qualifies at the configured threshold, not twice it', async () => {
  const test = await supported()
  expect(test.Detector.requestPermission).not.toHaveBeenCalled()
  expect(test.commands[0].check_in.coverage_end).toBe('2026-09-11T09:59:00.000Z')
  await test.advance(14 * 60000)
  expect(test.commands.some(command => command.check_in.action === 'candidate')).toBe(false)
  await test.advance(60000)
  expect(test.repository.state.snapshot?.check_in?.question?.id).toBe('reading:0')
  expect(test.repository.state.snapshot?.current?.start_at).toBe('2026-09-11T10:00:00.000Z')
  test.adapter.stop()
})

it('freeze and revoked permission discard coverage while recording continues', async () => {
  const test = await supported(); test.idle()
  await test.advance(14 * 60000)
  document.dispatchEvent(new Event('freeze'))
  await test.advance(60000)
  expect(test.repository.state.snapshot?.check_in?.question).toBeNull()
  document.dispatchEvent(new Event('resume'))
  await test.advance(14 * 60000)
  expect(test.repository.state.snapshot?.check_in?.question).toBeNull()
  test.permission.state = 'denied'; test.permission.dispatchEvent(new Event('change'))
  await test.advance(60000)
  expect(test.adapter.getSnapshot().permission).toBe('denied')
  expect(test.repository.state.snapshot?.check_in?.question).toBeNull()
  expect(test.repository.state.snapshot?.current?.id).toBe(1)
  test.adapter.stop()
})

it('notification claim is durably attempted once across restart', async () => {
  const test = await supported(); test.idle()
  await test.advance(15 * 60000)
  const operation = test.commands.find(command => command.check_in.action === 'candidate')!.operation_id
  expect(await test.repository.claimCheckInNotification(operation)).toBeTruthy()
  expect(await test.repository.claimCheckInNotification(operation)).toBe(false)
  const restored = new ActivityRepository(localStorage, work => work())
  await restored.refresh()
  expect(await restored.claimCheckInNotification(operation)).toBe(false)
  test.adapter.stop()
})

it('an offline candidate remains inline but reconnection does not deliver a notification', async () => {
  const test = await supported(); test.idle(); test.offline()
  const showNotification = vi.fn()
  Object.assign(navigator, { serviceWorker: { getRegistration: async () => ({ showNotification, getNotifications: async () => [] }) } })
  vi.stubGlobal('Notification', { permission: 'granted' })
  await test.advance(15 * 60000)
  expect(test.repository.state.snapshot?.check_in?.question?.id).toBe('reading:0')
  expect(showNotification).not.toHaveBeenCalled()
  test.adapter.stop()
  test.online(); await test.repository.refresh()
  const restarted = new BrowserCheckIns(test.repository)
  await restarted.start(); await test.advance(60000)
  expect(showNotification).not.toHaveBeenCalled()
  expect(test.commands.some(command => command.check_in.action === 'delivery')).toBe(false)
  restarted.stop()
})

it.each(['null', 'error'] as const)('missing native state (%s) breaks coverage and recovery starts a full new interval', async sensor => {
  const test = await supported(); test.idle()
  await test.advance(14 * 60000)
  test.sensor(sensor); await test.advance(60000)
  expect(test.repository.state.snapshot?.check_in?.question).toBeNull()
  test.sensor('no'); await test.advance(14 * 60000)
  expect(test.repository.state.snapshot?.check_in?.question).toBeNull()
  await test.advance(90000)
  expect(test.repository.state.snapshot?.check_in?.question?.id).toBe('reading:0')
  test.adapter.stop()
})

it('a delayed notification list cannot close a newly created current question; a synchronized answer does close it', async () => {
  const test = await supported(); test.idle()
  const notification = { data: { activityQuestion: 'reading:0' }, close: vi.fn() }
  const lists: ((notifications: typeof notification[]) => void)[] = []
  Object.assign(navigator, { serviceWorker: { getRegistration: async () => ({ getNotifications: () => new Promise(resolve => lists.push(resolve)) }) } })
  await test.advance(15 * 60000)
  expect(test.repository.state.snapshot?.check_in?.question?.id).toBe('reading:0')
  lists.splice(0).forEach(resolve => resolve([notification]))
  await vi.advanceTimersByTimeAsync(0)
  expect(notification.close).not.toHaveBeenCalled()
  await test.repository.checkIn({ action: 'confirm', question_id: 'reading:0' })
  await vi.advanceTimersByTimeAsync(0)
  lists.splice(0).forEach(resolve => resolve([notification]))
  await vi.advanceTimersByTimeAsync(0)
  expect(notification.close).toHaveBeenCalled()
  test.adapter.stop()
})

it('server clock recalibration cannot turn time before page startup into observed coverage', async () => {
  const test = await supported(); test.idle()
  test.calibrate(3600000)
  await test.advance(14 * 60000)
  expect(test.repository.state.snapshot?.check_in?.question).toBeNull()
  await test.advance(60000)
  expect(test.repository.state.snapshot?.check_in?.question?.id).toBe('reading:0')
  test.adapter.stop()
})
