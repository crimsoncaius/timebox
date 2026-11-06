import { FocusHost } from './features/activity/FocusMode'
import { BrowserCheckInHost } from './features/activity/BrowserCheckInHost'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useSearchParams } from 'react-router-dom'
import { lazy, Suspense, useLayoutEffect } from 'react'
import { AssistantPage } from './features/assistant/AssistantPage'
import { HistoryPage } from './features/history/HistoryPage'
import { HomeRedirect } from './features/home/HomeRedirect'
import { SettingsPage } from './features/settings/SettingsPage'
import { TaskTypesPage } from './features/task-types/TaskTypesPage'
import { TodayPage } from './features/today/TodayPage'
import { BattlePlanPage } from './features/battle-plan/BattlePlanPage'
import { ReminderWatcher } from './components/ReminderWatcher'
import { ReadinessProvider } from './features/readiness/ReadinessProvider'
import { UndoFailureHost } from './components/UndoFailureHost'
import { ActivitySwitchUndoHost } from './features/activity/ActivitySwitchUndoHost'

const RecurringPage = lazy(() => import('./features/battle-plan/RecurringPage').then((module) => ({ default: module.RecurringPage })))
const PlanNowPrototype = lazy(() => import('./features/activity/PlanNowPrototype'))

export function AppRoutes() {
  const location = useLocation()
  const view = new URLSearchParams(location.search).get('view')
  useLayoutEffect(() => {
    // Day owns its first-block/Now scroll. Opening details on the same page
    // must not move the background, but changing destinations starts at the top.
    if (!location.pathname.startsWith('/day/')) window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [location.pathname, view])
  if (import.meta.env.DEV && location.pathname === '/prototype/297') {
    return <Suspense fallback={<p className="p-8">Loading prototype…</p>}><PlanNowPrototype /></Suspense>
  }
  return (
    <ReadinessProvider>
      <BrowserCheckInHost />
      <UndoFailureHost />
      <ActivitySwitchUndoHost />
      <FocusHost><AppRouteContent /><ReminderWatcher /></FocusHost>
    </ReadinessProvider>
  )
}

function AppRouteContent() {
  return <Routes>
        <Route path="/" element={<HomeRedirect />} />
        <Route path="/day/:date" element={<TodayPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/task-types" element={<TaskTypesPage />} />
        <Route path="/assistant" element={<AssistantPage />} />
        <Route path="/battle-plan" element={<BattlePlanRoute />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
}

function BattlePlanRoute() {
  const [params] = useSearchParams()
  return params.get('view') === 'recurring'
    ? <Suspense fallback={<p className="p-8 text-on-surface-variant">Loading Recurring…</p>}><RecurringPage /></Suspense>
    : <BattlePlanPage />
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  )
}
