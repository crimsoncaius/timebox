import { useEffect, useRef } from 'react'
import type { DayViewPreferences } from './dayViewPreferences'

export function DayViewOptions({ preferences, onChange, zoom, onZoomChange, onResetZoom, onClose, storageError }: {
  preferences: DayViewPreferences
  onChange: (section: keyof DayViewPreferences, value: boolean) => void
  zoom: number
  onZoomChange: (zoom: number) => void
  onResetZoom: () => void
  onClose: () => void
  storageError: string | null
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const previous = document.activeElement
    const dialog = ref.current
    dialog?.showModal()
    return () => {
      dialog?.close()
      if (previous instanceof HTMLElement) previous.focus()
    }
  }, [])
  return <dialog ref={ref} aria-labelledby="day-view-title" onCancel={onClose}
    onClick={event => { if (event.target === event.currentTarget) onClose() }}
    className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-sm overflow-y-auto rounded-3xl bg-surface p-0 text-on-surface backdrop:bg-black/50 dark:bg-dark-surface dark:text-dark-on-surface">
    <div className="p-6">
      <h2 id="day-view-title" className="font-headline text-[27px] font-extralight tracking-tight">Day view</h2>
      <p className="mt-2 text-sm text-on-surface-variant dark:text-dark-on-surface-variant">Choose what stays in view.</p>
      <div className="mt-5 divide-y divide-outline-variant/40 dark:divide-dark-outline-variant">
        {([['calendar', 'Calendar', 'calendar_today'], ['tracking', 'Activity Tracking', 'play_arrow']] as const).map(([key, label, icon]) =>
          <button key={key} type="button" role="switch" aria-checked={preferences[key]} onClick={() => onChange(key, !preferences[key])}
            className="flex min-h-16 w-full items-center gap-3 rounded-sm text-left focus-visible:outline-2 focus-visible:outline-planned">
            <span aria-hidden className="material-symbols-outlined text-xl text-on-surface-variant dark:text-dark-on-surface-variant">{icon}</span>
            <span className="flex-1 text-sm font-medium">{label}</span>
            <span aria-hidden className={`flex h-8 w-12 shrink-0 items-center rounded-full border-2 px-1 ${preferences[key] ? 'justify-end border-planned bg-planned dark:border-planned-dark dark:bg-planned-dark' : 'border-outline-variant bg-surface-container-low dark:border-dark-outline-variant dark:bg-dark-surface-container'}`}>
              <span className={`h-5 w-5 rounded-full ${preferences[key] ? 'bg-white dark:bg-dark-surface' : 'bg-on-surface-variant dark:bg-dark-on-surface-variant'}`} />
            </span>
          </button>)}
        <div className="flex min-h-14 items-center justify-between gap-3">
          <label htmlFor="day-view-zoom" className="text-sm font-medium">Zoom</label>
          <input id="day-view-zoom" type="range" min={0.5} max={12} step="any" value={zoom}
            aria-valuetext={`${zoom.toFixed(1)} times`}
            onChange={event => onZoomChange(Number(event.target.value))}
            onKeyDown={event => {
              if (event.key === 'ArrowUp' || event.key === 'ArrowRight' || event.key === 'ArrowDown' || event.key === 'ArrowLeft') {
                event.preventDefault()
                const increase = event.key === 'ArrowUp' || event.key === 'ArrowRight'
                onZoomChange(Math.min(12, Math.max(0.5, zoom * (increase ? 1.2 : 1 / 1.2))))
              }
            }}
            className="w-40 accent-planned focus-visible:outline-2 focus-visible:outline-planned" />
        </div>
        <button type="button" disabled={zoom === 1} onClick={onResetZoom}
          className="flex min-h-14 w-full items-center gap-3 rounded-sm text-left focus-visible:outline-2 focus-visible:outline-planned disabled:opacity-50">
          <span aria-hidden className="material-symbols-outlined text-xl text-on-surface-variant dark:text-dark-on-surface-variant">restart_alt</span>
          <span className="flex-1 text-sm font-medium">Reset zoom</span>
          <span className="font-mono text-sm tabular-nums text-on-surface-variant dark:text-dark-on-surface-variant">{zoom.toFixed(1)}×</span>
        </button>
      </div>
      <p className="mt-4 text-xs leading-5 text-on-surface-variant dark:text-dark-on-surface-variant">Activity Tracking keeps running when hidden.</p>
      {storageError && <p role="alert" className="mt-3 text-sm">{storageError}</p>}
      <button type="button" onClick={onClose} className="mt-6 min-h-12 w-full rounded-xl bg-on-surface text-sm font-medium text-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-planned dark:bg-dark-on-surface dark:text-dark-surface">Done</button>
    </div>
  </dialog>
}
