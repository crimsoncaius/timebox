import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

if (import.meta.env.MODE === 'activity-review' && import.meta.env.VITE_ACTIVITY_TRACKING_DEV === '1' && 'serviceWorker' in navigator) {
  void navigator.serviceWorker.register('/activity-dev-sw.js').then(async () => {
    const registration = await navigator.serviceWorker.ready
    registration.active?.postMessage({ type: 'cache-shell', urls: [location.origin + '/', location.href,
      ...performance.getEntriesByType('resource').map(entry => entry.name)] })
  }).catch(() => { /* Durable activity storage remains usable in an open page. */ })
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
