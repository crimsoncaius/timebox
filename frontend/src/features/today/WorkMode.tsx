import type { BattleTask, TimeBlock } from '../../lib/api'

function formatMinute(minute: number) {
  const hours = Math.floor(minute / 60)
  const minutes = minute % 60
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' })
    .format(new Date(2020, 0, 1, hours, minutes))
}

function blockTitle(block: TimeBlock) {
  return block.task?.title ?? block.note?.trim() ?? block.task_type.name
}

export function WorkMode({ current, next, task, nowMinute, confirming, recording, busy, error, onSetSubtask, onLeave, onExit }: {
  current: TimeBlock | null
  next: TimeBlock | null
  task: BattleTask | null
  nowMinute: number
  confirming: boolean
  recording: boolean
  busy: boolean
  error: string | null
  onSetSubtask: (id: number, checked: boolean) => Promise<void>
  onLeave: () => void
  onExit: () => Promise<void>
}) {
  const countdown = next ? Math.max(0, next.start_minute - nowMinute) : null
  return (
    <section role="dialog" aria-modal="true" aria-label="Work Mode" className="fixed inset-0 z-[120] overflow-y-auto bg-surface px-5 py-6 text-on-surface dark:bg-dark-background dark:text-dark-on-surface">
      <div className="mx-auto flex min-h-full max-w-5xl flex-col">
        <header className="flex items-center justify-between gap-4">
          <div>
            <p className="font-label text-[10px] font-semibold uppercase tracking-[0.2em] text-actual">Work Mode</p>
            <p className="mt-1 text-sm text-on-surface-variant">
              {recording ? 'Actual recording live' : confirming ? 'Confirming current work…' : 'Following today’s plan'}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button type="button" onClick={onLeave} disabled={busy} className="rounded-full border border-outline-variant/30 px-4 py-2.5 text-sm disabled:opacity-40">Back to app</button>
            <button type="button" onClick={() => void onExit()} disabled={busy} className="rounded-full bg-primary px-5 py-2.5 text-sm font-medium text-on-primary disabled:opacity-40">Exit Work Mode</button>
          </div>
        </header>

        <div className="flex flex-1 items-center py-10">
          {current ? (
            <div className="w-full max-w-3xl">
              <p className="font-label text-xs font-semibold uppercase tracking-[0.16em] text-actual">Current · {formatMinute(current.start_minute)}–{formatMinute(current.end_minute)}</p>
              <h1 className="mt-3 font-headline text-5xl font-extralight leading-tight tracking-tight">{blockTitle(current)}</h1>
              <p className="mt-3 text-sm font-medium text-on-surface-variant">{current.task_type.name}</p>
              {task?.description?.trim() ? <p className="mt-5 max-w-2xl text-lg leading-relaxed text-on-surface-variant">{task.description}</p> : null}
              {!task && current.note?.trim() ? <p className="mt-5 max-w-2xl text-lg leading-relaxed text-on-surface-variant">{current.note}</p> : null}
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
          ) : next ? (
            <section aria-label="Up next" className="w-full rounded-3xl bg-surface-container-low p-8 dark:bg-dark-surface-container-low">
              <p className="font-label text-xs font-semibold uppercase tracking-[0.18em] text-primary">Up next</p>
              <h1 className="mt-3 font-headline text-4xl font-extralight">{blockTitle(next)}</h1>
              <p className="mt-3 text-on-surface-variant">{formatMinute(next.start_minute)} · {countdown === 0 ? 'starting now' : `in ${countdown} ${countdown === 1 ? 'minute' : 'minutes'}`}</p>
            </section>
          ) : (
            <section className="w-full text-center">
              <h1 className="font-headline text-4xl font-extralight">No more planned work today</h1>
              <p className="mt-3 text-on-surface-variant">Work Mode will stay open until you exit.</p>
            </section>
          )}
        </div>
        {error ? <p role="alert" className="mb-6 rounded-xl bg-error-container/20 px-3 py-2 text-sm text-error">{error}</p> : null}
      </div>
    </section>
  )
}
