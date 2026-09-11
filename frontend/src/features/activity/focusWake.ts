/** A request belongs to one visible Focus generation, never persisted Focus intent. */
export function observeFocusWake(enabled: boolean, explain: (message: string) => void) {
  let disposed = false, generation = 0
  let held: WakeLockSentinel | null = null
  const release = () => { generation++; const previous = held; held = null; void previous?.release().catch(() => {}) }
  const request = async () => {
    release()
    if (disposed || !enabled || document.visibilityState !== 'visible') return
    const token = generation
    if (!navigator.wakeLock) { explain('Keeping the display awake is unavailable in this browser.'); return }
    try {
      const lock = await navigator.wakeLock.request('screen')
      if (disposed || token !== generation || document.visibilityState !== 'visible') { await lock.release(); return }
      held = lock; explain('')
      lock.addEventListener('release', () => { if (held === lock) { held = null; explain('Display wake was released by the browser. Focus continues.') } })
    } catch { if (!disposed && token === generation) explain('The display may sleep. Focus and recording continue.') }
  }
  document.addEventListener('visibilitychange', request)
  void request()
  return () => { disposed = true; document.removeEventListener('visibilitychange', request); release() }
}
