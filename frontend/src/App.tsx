import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { DayReviewPage } from './features/review/DayReviewPage'
import { HistoryPage } from './features/history/HistoryPage'
import { HomeRedirect } from './features/home/HomeRedirect'
import { TodayPage } from './features/today/TodayPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomeRedirect />} />
        <Route path="/day/:date" element={<TodayPage />} />
        <Route path="/review/:date" element={<DayReviewPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
