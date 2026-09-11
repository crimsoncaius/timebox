import { ActivityRepository, getActivityRepository } from './activityRepository'

interface NativeIdleDetector extends EventTarget {
  userState: 'active' | 'idle' | null
  screenState: 'locked' | 'unlocked' | null
  start(options: { threshold: number; signal: AbortSignal }): Promise<void>
}
interface IdleConstructor { new(): NativeIdleDetector; requestPermission(): Promise<PermissionState> }
const idleConstructor = () => (globalThis as typeof globalThis & { IdleDetector?: IdleConstructor }).IdleDetector
async function readNativeState(Native: IdleConstructor, threshold: number, signal: AbortSignal) {
  const detector = new Native()
  await detector.start({ threshold, signal })
  if (detector.userState === null || detector.screenState === null) {
    await new Promise<void>(resolve => {
      const done = () => { clearTimeout(timeout); detector.removeEventListener('change', done); signal.removeEventListener('abort', done); resolve() }
      const timeout = setTimeout(done, 2000)
      detector.addEventListener('change', done); signal.addEventListener('abort', done)
      if (signal.aborted) done()
    })
  }
  return { user: detector.userState, screen: detector.screenState }
}

export class BrowserCheckIns {
  private listeners = new Set<() => void>()
  private state = { capability: idleConstructor() ? 'supported' : 'unsupported', permission: 'prompt', notification: typeof Notification === 'undefined' ? 'unavailable' : Notification.permission as string, detail: '' }
  private permission?: PermissionStatus
  private abort?: AbortController
  private timer?: ReturnType<typeof setInterval>
  private unsubscribe?: () => void
  private unsubscribeStart?: () => void
  private floor = 0
  private lastTick = 0
  private lastWall = 0
  private epoch = 0
  private running = false
  private paused = false
  private sampling = false
  private identity = ''
  private repository: ActivityRepository
  constructor(repository: ActivityRepository) { this.repository = repository }
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  getSnapshot = () => this.state
  private publish(patch: Partial<typeof this.state>) { this.state = { ...this.state, ...patch }; this.listeners.forEach(listener => listener()) }
  async start() {
    if (this.running) return
    this.running = true; this.paused = false
    this.reset()
    this.unsubscribe = this.repository.subscribe(this.reconcile)
    this.unsubscribeStart = this.repository.subscribeTrackingStart(() => {
      if (navigator.userActivation?.isActive && this.repository.checkInPreferences().enabled && this.state.permission === 'prompt') void this.requestDetection()
    })
    document.addEventListener('freeze', this.suspend)
    document.addEventListener('resume', this.resume)
    window.addEventListener('pagehide', this.suspend)
    window.addEventListener('pageshow', this.resume)
    window.addEventListener('focus', this.refreshPermission)
    this.timer = setInterval(() => void this.sample(), 15000)
    await this.refreshPermission()
  }
  stop() {
    this.running = false; this.reset(); clearInterval(this.timer); this.unsubscribe?.(); this.unsubscribeStart?.()
    this.permission?.removeEventListener('change', this.permissionChanged)
    document.removeEventListener('freeze', this.suspend); document.removeEventListener('resume', this.resume)
    window.removeEventListener('pagehide', this.suspend); window.removeEventListener('pageshow', this.resume)
    window.removeEventListener('focus', this.refreshPermission)
  }
  private reset() { this.epoch++; this.abort?.abort(); this.floor = this.repository.now(); this.lastTick = performance.now(); this.lastWall = Date.now() }
  private suspend = () => { this.paused = true; this.reset(); this.publish({ detail: 'Detection paused; unobserved time is not counted.' }) }
  private resume = () => { this.paused = false; this.reset(); void this.refreshPermission() }
  private permissionChanged = () => { this.reset(); this.publish({ permission: this.permission!.state }); void this.sample() }
  private refreshPermission = async () => {
    this.publish({ notification: typeof Notification === 'undefined' ? 'unavailable' : Notification.permission })
    if (!idleConstructor()) return
    try {
      this.permission?.removeEventListener('change', this.permissionChanged)
      this.permission = await navigator.permissions.query({ name: 'idle-detection' as PermissionName })
      this.permission.addEventListener('change', this.permissionChanged)
      if (this.state.permission !== this.permission.state) this.reset()
      this.publish({ permission: this.permission.state })
      await this.sample()
    } catch { this.reset(); this.publish({ permission: 'unavailable', detail: 'Device detection is unavailable in this browser context.' }) }
  }
  requestDetection = async () => {
    const Native = idleConstructor()
    if (!Native) return
    try { this.publish({ permission: await Native.requestPermission() }); this.reset(); await this.refreshPermission() }
    catch { this.publish({ detail: 'Use browser site settings to allow device detection.' }) }
  }
  requestNotifications = async () => {
    if (typeof Notification === 'undefined') return
    try { this.publish({ notification: await Notification.requestPermission() }) } catch { this.publish({ notification: 'unavailable' }) }
  }
  private reconcile = () => {
    const snapshot = this.repository.state.snapshot
    const preferences = this.repository.checkInPreferences()
    const identity = `${snapshot?.check_in?.generation}:${snapshot?.check_in?.rearm}:${preferences.enabled}:${preferences.thresholdMinutes}`
    if (this.identity !== identity) { this.identity = identity; this.reset() }
    void this.closeResolvedNotifications().catch(() => { /* Optional OS delivery never gates recording. */ })
  }
  private async closeResolvedNotifications() {
    if (!navigator.serviceWorker) return
    const registration = await navigator.serviceWorker.getRegistration()
    if (!registration) return
    const id = this.repository.state.snapshot?.check_in?.question?.id
    for (const notification of await registration.getNotifications()) {
      if (notification.data?.activityQuestion && notification.data.activityQuestion !== id) notification.close()
    }
  }
  private async sample() {
    if (!this.running || this.paused || this.sampling) return
    this.reconcile()
    const tick = performance.now()
    if (tick - this.lastTick > 45000 || tick < this.lastTick || Math.abs(Date.now() - this.lastWall - (tick - this.lastTick)) > 5000) this.reset()
    this.lastTick = tick; this.lastWall = Date.now()
    const Native = idleConstructor(), preferences = this.repository.checkInPreferences()
    const prompt = this.repository.state.snapshot?.check_in
    if (!Native || this.state.permission !== 'granted' || !preferences.enabled || !prompt?.armed_at || !this.repository.state.snapshot?.current) { this.reset(); return }
    const epoch = this.epoch, threshold = preferences.thresholdMinutes * 60000
    this.sampling = true
    this.abort?.abort()
    const abort = new AbortController(); this.abort = abort
    try {
      // A fresh initial native state establishes the preceding threshold. Reading
      // an old detector property after a suspended task would not establish it.
      const short = await readNativeState(Native, 60000, abort.signal)
      if (epoch !== this.epoch || !this.running || abort.signal.aborted) return
      const active = short.user === 'active' && short.screen === 'unlocked'
      const observation = active ? short : await readNativeState(Native, threshold, abort.signal)
      if (epoch !== this.epoch || !this.running || abort.signal.aborted) return
      const now = this.repository.now()
      const idle = observation.user === 'idle'
      this.publish({ detail: 'Native device detection is connected while this page can run.' })
      if (short.user === null || short.screen === null || observation.user === null || observation.screen === null) { this.reset(); return }
      if (!active && !idle) return // Locked alone supplies no lock duration.
      // Active means some input within the threshold, not input at this instant.
      // Its earliest possible time is the only conservative shared lower bound.
      const end = active ? now - 60000 : now
      const start = active ? end : Math.max(this.floor, now - threshold, Date.parse(prompt.armed_at), Date.parse(prompt.active_at ?? prompt.armed_at))
      if (!active && (prompt.question || now - start < threshold)) return
      const operation = await this.repository.checkIn({ action: active ? 'observe' : 'candidate', generation: prompt.generation, rearm: prompt.rearm,
        capability: 'supported', permission: 'granted', observed: active ? 'active' : observation.screen === 'locked' ? 'locked' : 'idle', coverage_start: new Date(start).toISOString(), coverage_end: new Date(end).toISOString() })
      if (!active && operation && epoch === this.epoch && typeof Notification !== 'undefined' && Notification.permission === 'granted' && navigator.serviceWorker) {
        try {
          const registration = await navigator.serviceWorker.getRegistration()
          if (!registration) return
          const question = await this.repository.claimCheckInNotification(operation)
          if (question) await registration.showNotification('Still doing this?', { body: 'Recording continues. Open Timebox to confirm or switch activity.', tag: `activity:${question.id}`, data: { activityQuestion: question.id }, renotify: false } as NotificationOptions)
        } catch { /* A consumed optional delivery attempt is never escalated again. */ }
      }
    } catch (error) { if (!abort.signal.aborted) { this.reset(); this.publish({ detail: `Device detection paused: ${error instanceof Error ? error.message : 'unavailable'}` }) } }
    finally { abort.abort(); this.sampling = false }
  }
}
let shared: BrowserCheckIns | undefined
export const getBrowserCheckIns = () => shared ??= new BrowserCheckIns(getActivityRepository())
