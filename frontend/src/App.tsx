import { BrowserRouter, Navigate, Route, Routes, useSearchParams } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { HistoryPage } from './features/history/HistoryPage'
import { HomeRedirect } from './features/home/HomeRedirect'
import { SettingsPage } from './features/settings/SettingsPage'
import { TaskTypesPage } from './features/task-types/TaskTypesPage'
import { TodayPage } from './features/today/TodayPage'
import { BattlePlanPage } from './features/battle-plan/BattlePlanPage'
import { CreateTaskComposerPrototype } from './features/battle-plan/CreateTaskComposerPrototype'
import { ReminderWatcher } from './components/ReminderWatcher'

const RecurringPage = lazy(() => import('./features/battle-plan/RecurringPage').then((module) => ({ default: module.RecurringPage })))

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/day/:date" element={<TodayPage />} />
      <Route path="/history" element={<HistoryPage />} />
      <Route path="/task-types" element={<TaskTypesPage />} />
      <Route path="/battle-plan" element={<BattlePlanRoute />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

function BattlePlanRoute() {
  const [params] = useSearchParams()
  if (import.meta.env.DEV && params.get('prototype') === 'create-task') {
    return <CreateTaskComposerPrototype />
  }
  return params.get('view') === 'recurring'
    ? <Suspense fallback={<p className="p-8 text-on-surface-variant">Loading Recurring…</p>}><RecurringPage /></Suspense>
    : <BattlePlanPage />
}

export default function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  )
}

function AppContent() {
  const [params] = useSearchParams()
  const prototypeOpen = import.meta.env.DEV && params.get('prototype') === 'create-task'
  return (
    <>
      <AppRoutes />
      {!prototypeOpen && <ReminderWatcher />}
    </>
  )
}
