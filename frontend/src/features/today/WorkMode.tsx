import { useState } from 'react'
import type { ActualBlock, BattleTask } from '../../lib/api'

function inputValue(instant: string | null, timezone: string) {
  if (!instant) return ''
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(instant))
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${value.year}-${value.month}-${value.day}T${value.hour}:${value.minute}`
}

export function WorkMode({ actual, task, timezone, busy, error, onSave, onSetSubtask, onFinish, onFinishAndComplete, onClose }: {
  actual: ActualBlock
  task: BattleTask | null
  timezone: string
  busy: boolean
  error: string | null
  onSave: (values: { startLocal: string; endLocal: string }) => Promise<void>
  onSetSubtask: (id: number, checked: boolean) => Promise<void>
  onFinish: () => Promise<void>
  onFinishAndComplete: () => Promise<void>
  onClose: () => void
}) {
  const [startLocal, setStartLocal] = useState(() => inputValue(actual.start_at, timezone))
  const [endLocal, setEndLocal] = useState(() => inputValue(actual.end_at, timezone))

  const running = actual.end_at == null
  const title = task?.title ?? actual.task?.title ?? actual.note?.trim() ?? actual.task_type.name
  return (
    <section role="dialog" aria-modal="true" aria-label="Work Mode" className="fixed inset-0 z-[120] overflow-y-auto bg-surface px-5 py-6 text-on-surface dark:bg-dark-background dark:text-dark-on-surface">
      <div className="mx-auto flex min-h-full max-w-5xl flex-col">
        <header className="flex items-center justify-between gap-4">
          <div>
            <p className="font-label text-[10px] font-semibold uppercase tracking-[0.2em] text-actual">Work Mode</p>
            <p className="mt-1 text-sm text-on-surface-variant">{running ? 'Actual recording live' : 'Actual recorded'}</p>
          </div>
          <button type="button" aria-label="Close Work Mode" onClick={onClose} disabled={busy} className="rounded-full border border-outline-variant/30 px-4 py-2 text-sm">Close</button>
        </header>

        <div className="grid flex-1 items-center gap-10 py-10 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <div>
            <h1 className="font-headline text-5xl font-extralight leading-tight tracking-tight">{title}</h1>
            {task ? (
              <section aria-label="Subtasks" className="mt-8 space-y-2">
                {task.subtasks.length ? task.subtasks.map((subtask) => (
                  <label key={subtask.id} className="flex items-center gap-3 rounded-xl px-4 py-3 text-base hover:bg-surface-container-low">
                    <input type="checkbox" aria-label={subtask.title} checked={subtask.checked} disabled={busy || task.status === 'completed'} onChange={(event) => void onSetSubtask(subtask.id, event.target.checked)} />
                    <span className={subtask.checked ? 'text-on-surface-variant line-through' : ''}>{subtask.title}</span>
                  </label>
                )) : <p className="text-sm text-on-surface-variant">No Subtasks.</p>}
              </section>
            ) : null}
          </div>

          <aside className="rounded-3xl bg-surface-container-low p-6 dark:bg-dark-surface-container-low">
            <p className="font-label text-[10px] font-semibold uppercase tracking-[0.16em] text-actual">Actual time</p>
            <label className="mt-4 block text-xs text-on-surface-variant">Start
              <input aria-label="Actual start" type="datetime-local" step="60" value={startLocal} disabled={busy} onChange={(event) => setStartLocal(event.target.value)} className="mt-1 w-full rounded-xl border border-actual-border bg-transparent px-3 py-2 text-base text-on-surface" />
            </label>
            <label className="mt-3 block text-xs text-on-surface-variant">End
              <input aria-label="Actual end" type="datetime-local" step="60" value={endLocal} disabled={busy || running} onChange={(event) => setEndLocal(event.target.value)} className="mt-1 w-full rounded-xl border border-actual-border bg-transparent px-3 py-2 text-base text-on-surface" />
            </label>
            <p className="mt-2 text-xs text-on-surface-variant">Minute accurate. Planned time is unchanged.</p>
            <button type="button" disabled={busy || !startLocal || (!running && !endLocal) || (Boolean(endLocal) && endLocal <= startLocal)} onClick={() => void onSave({ startLocal, endLocal })} className="mt-4 w-full rounded-xl border border-outline-variant/40 px-4 py-2.5 text-sm font-medium disabled:opacity-40">Save Actual time</button>
            {running ? (
              <div className="mt-4 grid gap-2">
                <button type="button" disabled={busy} onClick={() => void onFinish()} className="rounded-xl border border-outline-variant/40 px-4 py-3 text-sm font-medium disabled:opacity-40">Finish session</button>
                {task && task.status !== 'completed' ? <button type="button" disabled={busy} onClick={() => void onFinishAndComplete()} className="rounded-xl bg-primary px-4 py-3 text-sm font-medium text-on-primary disabled:opacity-40">Finish session + complete Task</button> : null}
              </div>
            ) : null}
            {error ? <p role="alert" className="mt-4 rounded-xl bg-error-container/20 px-3 py-2 text-sm text-error">{error}</p> : null}
          </aside>
        </div>
      </div>
    </section>
  )
}
