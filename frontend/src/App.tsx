import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { HistoryPage } from './features/history/HistoryPage'
import { HomeRedirect } from './features/home/HomeRedirect'
import { SettingsPage } from './features/settings/SettingsPage'
import { TaskTypesPage } from './features/task-types/TaskTypesPage'
import { TodayPage } from './features/today/TodayPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/day/:date" element={<TodayPage />} />
      <Route path="/history" element={<HistoryPage />} />
      <Route path="/task-types" element={<TaskTypesPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  )
}
