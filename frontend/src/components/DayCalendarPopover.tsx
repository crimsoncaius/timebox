import { useCallback, useEffect, useId, useRef, useState } from 'react'
import {
  addMonthsIso,
  firstOfMonthIso,
  monthGridForIso,
  monthYearLabelForIso,
  WEEKDAY_LABELS_SUN_FIRST,
} from '../lib/time'

function formatTriggerDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return iso
  const dt = new Date(Date.UTC(y, m - 1, d))
  return dt.toLocaleDateString('en-GB', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

function dayCellLabel(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return ''
  const dt = new Date(Date.UTC(y, m - 1, d))
  return String(dt.getUTCDate())
}

export function DayCalendarPopover({
  value,
  todayIso,
  onSelect,
}: {
  value: string
  todayIso: string
  onSelect: (iso: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [visibleMonthIso, setVisibleMonthIso] = useState(() => firstOfMonthIso(value))
  const containerRef = useRef<HTMLDivElement>(null)
  const headingId = useId()

  useEffect(() => {
    setVisibleMonthIso(firstOfMonthIso(value))
  }, [value])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  useEffect(() => {
    if (!open) return
    const onPointerDown = (e: PointerEvent) => {
      const el = containerRef.current
      if (!el || !(e.target instanceof Node)) return
      if (!el.contains(e.target)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown, true)
    return () => document.removeEventListener('pointerdown', onPointerDown, true)
  }, [open])

  const grid = monthGridForIso(visibleMonthIso)
  const monthLabel = monthYearLabelForIso(visibleMonthIso)

  const pickDay = useCallback(
    (iso: string) => {
      onSelect(iso)
      setOpen(false)
    },
    [onSelect],
  )

  return (
    <div ref={containerRef} className="relative inline-block">
      <button
        type="button"
        data-testid="day-calendar-trigger"
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-controls={open ? headingId : undefined}
        className="inline-flex min-w-38 items-center justify-center gap-2 rounded-xl border border-outline-variant/15 bg-surface-container-low/80 px-3 py-1.5 font-headline text-sm tabular-nums text-on-surface shadow-[0_0_40px_rgba(45,52,53,0.04)] backdrop-blur-sm transition-colors hover:bg-surface-container-high dark:border-stone-600/40 dark:bg-stone-900/50 dark:hover:bg-stone-800/60"
        aria-label="Jump to date"
        onClick={() => setOpen((o) => !o)}
      >
        <span>{formatTriggerDate(value)}</span>
        <span className="material-symbols-outlined text-[18px] text-on-surface-variant" aria-hidden>
          calendar_month
        </span>
      </button>

      {open ? (
        <div
          data-testid="day-calendar-popover"
          role="dialog"
          aria-modal="true"
          aria-labelledby={headingId}
          className="absolute left-0 top-full z-70 mt-2 w-[min(100vw-2rem,20rem)] rounded-2xl border border-outline-variant/15 bg-surface-container-lowest/85 p-4 shadow-[0_0_40px_rgba(45,52,53,0.06)] backdrop-blur-[20px] dark:border-stone-700/50 dark:bg-stone-950/85 dark:shadow-none"
        >
          <div className="mb-3 flex items-center justify-between gap-2">
            <h2 id={headingId} className="font-headline text-sm font-medium text-on-surface dark:text-stone-100">
              {monthLabel}
            </h2>
            <div className="flex gap-1">
              <button
                type="button"
                className="rounded-lg px-2 py-1 font-headline text-on-surface-variant transition-colors hover:bg-surface-container-high dark:hover:bg-stone-800"
                aria-label="Previous month"
                onClick={() => setVisibleMonthIso((v) => addMonthsIso(v, -1))}
              >
                <span aria-hidden>↑</span>
              </button>
              <button
                type="button"
                className="rounded-lg px-2 py-1 font-headline text-on-surface-variant transition-colors hover:bg-surface-container-high dark:hover:bg-stone-800"
                aria-label="Next month"
                onClick={() => setVisibleMonthIso((v) => addMonthsIso(v, 1))}
              >
                <span aria-hidden>↓</span>
              </button>
            </div>
          </div>

          <div className="mb-2 grid grid-cols-7 gap-y-1 text-center font-headline text-[10px] font-medium uppercase tracking-wider text-on-surface-variant">
            {WEEKDAY_LABELS_SUN_FIRST.map((w) => (
              <div key={w} className="py-1">
                {w}
              </div>
            ))}
          </div>

          <div className="grid grid-cols-7 gap-1">
            {grid.map((cell) => {
              const selected = cell.iso === value
              const isToday = cell.iso === todayIso
              return (
                <button
                  key={cell.iso}
                  type="button"
                  className={[
                    'flex h-9 items-center justify-center rounded-xl font-headline text-sm tabular-nums transition-colors',
                    !cell.inMonth ? 'text-on-surface-variant/50' : 'text-on-surface',
                    selected
                      ? 'bg-primary-container text-on-primary-container ring-2 ring-on-surface/40 dark:bg-stone-700 dark:text-stone-100 dark:ring-stone-200/30'
                      : isToday
                        ? 'ring-1 ring-outline-variant/40 dark:ring-stone-500/50'
                        : 'hover:bg-surface-container-high dark:hover:bg-stone-800/80',
                  ].join(' ')}
                  aria-pressed={selected}
                  aria-label={cell.iso}
                  onClick={() => pickDay(cell.iso)}
                >
                  {dayCellLabel(cell.iso)}
                </button>
              )
            })}
          </div>

          <div className="mt-4 flex justify-end border-t border-outline-variant/10 pt-3 dark:border-stone-700/50">
            <button
              type="button"
              className="font-headline text-sm font-medium text-primary transition-colors hover:text-primary-dim dark:text-stone-300 dark:hover:text-stone-100"
              onClick={() => pickDay(todayIso)}
            >
              Today
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
