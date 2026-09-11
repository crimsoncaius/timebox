import { zonedLocalDateTimeCandidates } from '../../lib/time'
import type { ActivityTimeValue } from './activityTime'

export function ActivityTimeField({ label, value, onChange, timezone }: { label: string; value: ActivityTimeValue; onChange: (value: ActivityTimeValue) => void; timezone: string }) {
  let candidates: string[] = []
  try { candidates = zonedLocalDateTimeCandidates(value.local, timezone) } catch { /* incomplete input */ }
  return <div className="space-y-1">
    <label className="block">{label}<input className="block w-full rounded border p-2 dark:bg-dark-surface" type="datetime-local" value={value.local} onChange={e => onChange({ local: e.target.value })} /></label>
    {candidates.length > 1 ? <label className="block">{label} occurrence<select className="block w-full rounded border p-2 dark:bg-dark-surface" value={value.occurrence ?? ''} onChange={e => onChange({ local: value.local, occurrence: e.target.value as 'earlier' | 'later' })}>
      <option value="">Choose occurrence</option><option value="earlier">Earlier — {candidates[0]}</option><option value="later">Later — {candidates[1]}</option>
    </select></label> : null}
    {value.local && !candidates.length ? <p role="alert">That local time does not exist in {timezone}.</p> : null}
  </div>
}
