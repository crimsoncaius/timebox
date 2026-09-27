import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { addMonthsIso, firstOfMonthIso, monthGridForIso, monthYearLabelForIso, WEEKDAY_LABELS_MON_FIRST } from '../../lib/time'
import { dayCountLabel, nextRangeDraft, trendDayLabel, trendRangeDayCount, type RangeDraft } from './trends'

const monthArrow = 'grid size-8 place-items-center rounded-full text-lg text-on-surface hover:bg-surface-container disabled:opacity-30 disabled:hover:bg-transparent dark:text-dark-on-surface dark:hover:bg-dark-surface-container'

function dayName(iso: string) {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric', timeZone: 'UTC' })
}

/**
 * The Custom range control: a "Choose days" pill that opens a Monday-first calendar. The first tap picks the
 * first day, the second the last; days between are shaded, and nothing changes until the range is shown.
 */
export function CustomRangePicker({ start, end, today, onChange }: {
  start: string
  end: string
  today: string
  onChange: (start: string, end: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState<RangeDraft>({ start, end })
  const [month, setMonth] = useState(firstOfMonthIso(end))
  const [hovered, setHovered] = useState<string | null>(null)
  const [anchor, setAnchor] = useState({ left: 16, top: 16 })
  const [lift, setLift] = useState(0)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLDivElement>(null)
  const headingId = useId()

  function openCalendar() {
    const bounds = triggerRef.current?.getBoundingClientRect()
    if (bounds) {
      const width = Math.min(window.innerWidth - 32, 344)
      const left = Math.max(16, Math.min(bounds.left, window.innerWidth - width - 16))
      setAnchor({ left, top: bounds.bottom + 8 })
    }
    setDraft({ start, end })
    setMonth(firstOfMonthIso(end))
    setHovered(null)
    setOpen(true)
  }

  useEffect(() => {
    if (!open) return
    const dialog = dialogRef.current
    const trigger = triggerRef.current
    if (!dialog) return
    const buttons = () => Array.from(dialog.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'))
    ;(dialog.querySelector<HTMLButtonElement>('button[data-endpoint="last"]:not(:disabled)') ?? buttons()[0])?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); setOpen(false) }
      else if (event.key === 'Tab') {
        const items = buttons()
        if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items.at(-1)?.focus() }
        else if (!event.shiftKey && document.activeElement === items.at(-1)) { event.preventDefault(); items[0]?.focus() }
      }
    }
    const onPointerDown = (event: PointerEvent) => {
      if (event.target instanceof Node && !dialog.contains(event.target) && !trigger?.contains(event.target)) setOpen(false)
    }
    window.addEventListener('keydown', onKey, true)
    document.addEventListener('pointerdown', onPointerDown, true)
    return () => {
      window.removeEventListener('keydown', onKey, true)
      document.removeEventListener('pointerdown', onPointerDown, true)
      if (trigger?.isConnected) trigger.focus()
    }
  }, [open])

  const cells = monthGridForIso(month)
  // Drop a trailing week that belongs entirely to the next month.
  const visibleCells = cells.slice(35).some(cell => cell.inMonth) ? cells : cells.slice(0, 35)
  const previewEnd = draft.end ?? (hovered && hovered >= draft.start ? hovered : null)
  const chosenDays = draft.end ? trendRangeDayCount({ period: 'custom', start: draft.start, end: draft.end }) : 0
  const canShowNextMonth = addMonthsIso(month, 1) <= today
  const rows = visibleCells.length / 7

  // Open beneath the pill, sliding up only as far as needed to keep the whole month in view.
  useLayoutEffect(() => {
    const dialog = dialogRef.current
    if (!open || !dialog) return
    setLift(Math.max(0, anchor.top + dialog.offsetHeight + 16 - window.innerHeight))
  }, [open, anchor, rows])

  function apply() {
    if (!draft.end) return
    onChange(draft.start, draft.end)
    setOpen(false)
  }

  return <>
    <button ref={triggerRef} type="button" aria-haspopup="dialog" aria-expanded={open} onClick={() => open ? setOpen(false) : openCalendar()}
      className="inline-flex h-10 shrink-0 items-center gap-2 whitespace-nowrap rounded-full border border-outline-variant/60 px-4 text-sm text-on-surface transition-colors hover:bg-surface-container-low dark:border-dark-outline-variant dark:text-dark-on-surface dark:hover:bg-dark-surface-container">
      <span aria-hidden className="material-symbols-outlined text-[16px]">date_range</span>
      Choose days
    </button>
    {open && createPortal(<div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby={headingId} style={{ left: anchor.left, top: Math.max(16, anchor.top - lift) }}
      className="fixed z-120 max-h-[calc(100vh-2rem)] w-[min(100vw-2rem,21.5rem)] overflow-y-auto rounded-2xl border border-outline-variant/20 bg-surface-container-lowest p-4 text-on-surface shadow-[0_12px_40px_rgba(45,52,53,0.12)] dark:border-dark-outline-variant dark:bg-dark-surface-container-low dark:text-dark-on-surface dark:shadow-[0_12px_40px_rgba(0,0,0,0.45)]">
      <h2 id={headingId} className="sr-only">Choose days</h2>
      <div className="grid grid-cols-2 gap-3 border-b border-outline-variant/30 pb-3 dark:border-dark-outline-variant" aria-live="polite">
        {/* The underline marks the slot the next tap fills. */}
        <RangeSlot label="First day" value={trendDayLabel(draft.start, today)} next={draft.end !== null} />
        <RangeSlot label="Last day" value={draft.end ? trendDayLabel(draft.end, today) : null} next={draft.end === null} />
      </div>

      <div className="mt-2 flex items-center justify-between">
        <button type="button" className={monthArrow} aria-label="Previous month" onClick={() => setMonth(addMonthsIso(month, -1))}>‹</button>
        <p className="font-headline text-sm font-medium">{monthYearLabelForIso(month, 'en-US')}</p>
        <button type="button" className={monthArrow} aria-label="Next month" disabled={!canShowNextMonth} onClick={() => setMonth(addMonthsIso(month, 1))}>›</button>
      </div>

      <div className="mt-1 grid grid-cols-7 text-center text-[10px] font-medium uppercase tracking-wider text-on-surface-variant dark:text-dark-on-surface-variant" aria-hidden>
        {WEEKDAY_LABELS_MON_FIRST.map(day => <span key={day} className="py-1.5">{day}</span>)}
      </div>
      <div className="grid grid-cols-7 gap-y-0.5" onPointerLeave={() => setHovered(null)}>
        {visibleCells.map((cell, index) => {
          if (!cell.inMonth) return <span key={cell.iso} aria-hidden />
          const first = cell.iso === draft.start
          const last = cell.iso === draft.end
          const inRange = previewEnd !== null && cell.iso >= draft.start && cell.iso <= previewEnd
          const future = cell.iso > today
          const column = index % 7
          // The band runs between the endpoints and breaks at the edges of each week and of the month.
          const band = inRange && !(first && cell.iso === previewEnd)
          const bandShape = [
            first || column === 0 || cell.iso.endsWith('-01') ? 'rounded-l-full' : '',
            cell.iso === previewEnd || column === 6 || !visibleCells[index + 1]?.inMonth ? 'rounded-r-full' : '',
          ].join(' ')
          const state = first ? ', first day' : last ? ', last day' : inRange && draft.end ? ', in range' : ''
          return <div key={cell.iso} className={`flex h-9 items-center justify-center ${band ? `${draft.end ? 'bg-surface-container-high dark:bg-dark-surface-container-high' : 'bg-surface-container dark:bg-dark-surface-container'} ${bandShape}` : ''}`}>
            <button type="button" disabled={future} data-endpoint={last ? 'last' : first ? 'first' : undefined}
              aria-label={`${dayName(cell.iso)}${cell.iso === today ? ', today' : ''}${state}`} aria-pressed={first || last}
              title={future ? 'Days after Today have no recorded time' : undefined}
              onPointerEnter={() => setHovered(cell.iso)} onFocus={() => setHovered(cell.iso)}
              onClick={() => setDraft(current => nextRangeDraft(current, cell.iso))}
              className={`grid size-9 place-items-center rounded-full font-headline text-sm tabular-nums transition-colors disabled:cursor-default disabled:text-on-surface-variant/35 dark:disabled:text-dark-on-surface-variant/35 ${first || last
                ? 'bg-on-surface font-medium text-surface dark:bg-dark-on-surface dark:text-dark-surface'
                : cell.iso === today ? 'font-semibold text-planned hover:bg-surface-container-highest dark:text-planned-dark dark:hover:bg-dark-surface-container-highest' : 'hover:bg-surface-container-highest dark:hover:bg-dark-surface-container-highest'}`}>
              {Number(cell.iso.slice(8))}
            </button>
          </div>
        })}
      </div>

      <div className="mt-3 flex items-center justify-end gap-2">
        <button type="button" onClick={() => setOpen(false)} className="rounded-full px-4 py-2 text-sm text-on-surface-variant hover:bg-surface-container dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container">Cancel</button>
        <button type="button" disabled={!draft.end} onClick={apply}
          className="rounded-full bg-on-surface px-4 py-2 text-sm font-medium text-surface disabled:bg-surface-container-high disabled:text-on-surface-variant dark:bg-dark-on-surface dark:text-dark-surface dark:disabled:bg-dark-surface-container-high dark:disabled:text-dark-on-surface-variant">
          {draft.end ? `Show ${dayCountLabel(chosenDays)}` : 'Choose the last day'}
        </button>
      </div>
    </div>, document.body)}
  </>
}

function RangeSlot({ label, value, next }: { label: string; value: string | null; next: boolean }) {
  return <div className={`border-b-2 pb-1 ${next ? 'border-on-surface dark:border-dark-on-surface' : 'border-transparent'}`}>
    <p className="text-[11px] uppercase tracking-[0.14em] text-on-surface-variant dark:text-dark-on-surface-variant">{label}</p>
    <p className={`mt-0.5 font-headline text-lg font-light tabular-nums ${value ? '' : 'text-on-surface-variant dark:text-dark-on-surface-variant'}`}>{value ?? 'Tap a day'}</p>
  </div>
}
