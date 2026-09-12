import type { DayRead } from '../../lib/api'

export const needsElapsedDayView = (day: DayRead) => day.actual_blocks.some(p => (p.day_length_minutes ?? 1440) !== 1440)

/** Wall-clock grids cannot represent a repeated/skipped hour faithfully. */
export function ReportingDayActuals({ day, onSelect }: { day: DayRead; onSelect: (id: number) => void }) {
  const format = (iso: string) => new Intl.DateTimeFormat('en-GB', { timeZone: day.meta.timezone, month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'shortOffset' }).format(new Date(iso))
  return <section aria-label="Actual time on clock-change day" className="mb-4 space-y-2">
    <p className="text-sm">Actual time · {day.meta.timezone}. This day includes a clock change; elapsed daily shares are shown below.</p>
    {day.actual_blocks.map(p => <button key={p.actual_block.id} className="block w-full rounded-lg bg-actual-surface p-3 text-left dark:bg-actual-dark-surface" onClick={() => onSelect(p.actual_block.id)}>
      <strong>{p.actual_block.name || p.actual_block.task_type.name}</strong> · {p.duration_minutes}m on this day
      <span className="block text-sm">{format(p.actual_block.start_at)} – {p.actual_block.end_at ? format(p.actual_block.end_at) : 'Running'}</span>
    </button>)}
  </section>
}
