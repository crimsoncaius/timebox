/* Development-only app shell. API responses are never cached here: the activity
 * journal owns confirmed history and pending commands. */
const cacheName = 'timebox-activity-development-shell-v1'
const eligible = url => url.origin === self.location.origin && !url.pathname.startsWith('/api') && !url.pathname.includes('activity-dev-sw.js')
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()))
self.addEventListener('notificationclick', event => {
  const question = event.notification.data?.activityQuestion
  if (!question) return
  event.notification.close()
  event.waitUntil((async () => {
    const url = new URL('/settings', self.location.origin)
    url.searchParams.set('activity_check_in', question)
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const client = windows.find(item => new URL(item.url).origin === self.location.origin)
    if (client) { await client.navigate(url.href); await client.focus() }
    else await self.clients.openWindow(url.href)
  })())
})
// Dismissal intentionally leaves the shared question pending. No new delivery
// is scheduled by the worker, reconnect, or a subsequent app launch.
self.addEventListener('message', event => {
  if (event.data?.type !== 'cache-shell') return
  event.waitUntil((async () => {
    const cache = await caches.open(cacheName)
    await Promise.all(event.data.urls.filter(value => eligible(new URL(value))).map(async url => {
      try { const response = await fetch(url); if (response.ok) await cache.put(url, response) } catch { /* Retry on next online visit. */ }
    }))
  })())
})
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET' || !eligible(new URL(event.request.url))) return
  event.respondWith((async () => {
    const cache = await caches.open(cacheName)
    try {
      const response = await fetch(event.request)
      if (response.ok) await cache.put(event.request, response.clone())
      return response
    } catch (error) {
      // Vite varies module responses on Origin; same-origin prewarming and
      // module fetches differ in that header but serve identical static bytes.
      const saved = await cache.match(event.request, { ignoreVary: true })
      if (saved) return saved
      if (event.request.mode === 'navigate') {
        const shell = await cache.match('/', { ignoreVary: true })
        if (shell) return shell
      }
      throw error
    }
  })())
})
