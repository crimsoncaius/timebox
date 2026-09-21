import { useState } from 'react'
import type { TimeBlock } from '../lib/api'
import { formatMinuteLabel24 } from '../lib/time'
import { errorMessage } from '../lib/errors'

function parseBlockMinute(text: string): number | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(text.trim())
  if (!match) return null
  const hour = Number(match[1]), minute = Number(match[2])
  if (hour === 24 && minute === 0) return 1440
  return hour < 24 && minute < 60 ? hour * 60 + minute : null
}

export function BlockTimeFields({ block, onSave }: {
  block: TimeBlock
  onSave: (patch: { start_minute: number; end_minute: number }) => Promise<void>
}) {
  const [start, setStart] = useState(formatMinuteLabel24(block.start_minute))
  const [end, setEnd] = useState(block.end_minute === 1440 ? '24:00' : formatMinuteLabel24(block.end_minute))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const a = parseBlockMinute(start), b = parseBlockMinute(end)
  const valid = a != null && b != null && a < b
  return <section className="grid gap-2" aria-label="Block times">
    <div className="flex gap-2">
      <label className="min-w-0 flex-1">Start<input className="w-full rounded border p-2" value={start} disabled={saving} onChange={e => setStart(e.target.value)} /></label>
      <label className="min-w-0 flex-1">End<input className="w-full rounded border p-2" value={end} disabled={saving} onChange={e => setEnd(e.target.value)} /></label>
    </div>
    {!valid && <p role="alert">Enter HH:mm times with end after start.</p>}
    {error && <p role="alert">{error}</p>}
    <button type="button" disabled={saving || !valid || (a === block.start_minute && b === block.end_minute)} onClick={async () => {
      if (a == null || b == null || !valid) return
      setSaving(true); setError(null)
      try { await onSave({ start_minute: a, end_minute: b }) }
      catch (cause) { setError(errorMessage(cause, 'Could not save times')) }
      finally { setSaving(false) }
    }}>{saving ? 'Saving…' : 'Save times'}</button>
  </section>
}
