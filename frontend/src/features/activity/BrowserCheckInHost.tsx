import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { getBrowserCheckIns } from './browserCheckIns'
import { getActivityRepository } from './activityRepository'
import { dateInTimeZone } from '../../lib/battlePlan'

export function BrowserCheckInHost() {
  const location = useLocation(), navigate = useNavigate()
  useEffect(() => {
    const adapter = getBrowserCheckIns(), repository = getActivityRepository()
    void adapter.start()
    const refresh = () => { void repository.refresh() }
    const timer = setInterval(refresh, 15000)
    window.addEventListener('online', refresh)
    return () => { adapter.stop(); clearInterval(timer); window.removeEventListener('online', refresh) }
  }, [])
  useEffect(() => {
    const id = new URLSearchParams(location.search).get('activity_check_in')
    if (!id) return
    let cancelled = false
    const repository = getActivityRepository()
    void repository.refresh().then(() => {
      if (cancelled) return
      const snapshot = repository.state.snapshot
      if (snapshot?.check_in?.question?.id === id) navigate(`/day/${dateInTimeZone(new Date(repository.now()).toISOString(), snapshot.reporting_timezone)}`, { replace: true })
      else navigate(location.pathname, { replace: true })
    })
    return () => { cancelled = true }
  }, [location.pathname, location.search, navigate])
  return null
}
