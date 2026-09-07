import { useEffect, useState } from 'react'
import { addCalendarDays, dateInTimeZone, zonedLocalToIso } from './battlePlan'

export function millisecondsUntilNextAppMidnight(nowIso: string, timeZone: string): number {
  const nextDate = addCalendarDays(dateInTimeZone(nowIso, timeZone), 1)
  return Math.max(1, new Date(zonedLocalToIso(`${nextDate}T00:00`, timeZone)).getTime() - new Date(nowIso).getTime())
}

/** A running clock anchored to the server instant and recomputed at each app-timezone midnight. */
export function useAppClock(serverNowIso: string, timeZone: string): string {
  const sourceKey = `${serverNowIso}|${timeZone}`
  const [clock, setClock] = useState({ sourceKey, nowIso: serverNowIso })

  useEffect(() => {
    const serverAnchor = new Date(serverNowIso).getTime()
    if (!Number.isFinite(serverAnchor)) return
    const clientAnchor = Date.now()
    let timeout: ReturnType<typeof setTimeout> | undefined
    const currentIso = () => new Date(serverAnchor + Date.now() - clientAnchor).toISOString()
    const fire = () => {
      const current = currentIso()
      setClock({ sourceKey, nowIso: current })
      timeout = setTimeout(fire, millisecondsUntilNextAppMidnight(current, timeZone))
    }
    const current = currentIso()
    timeout = setTimeout(fire, millisecondsUntilNextAppMidnight(current, timeZone))
    return () => { if (timeout) clearTimeout(timeout) }
  }, [serverNowIso, sourceKey, timeZone])

  return clock.sourceKey === sourceKey ? clock.nowIso : serverNowIso
}
