import { useEffect, useId, useRef, useState } from 'react'
import { DayCalendarPopover } from './DayCalendarPopover'

function validDate(value: string, minIso?: string, maxIso?: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || (minIso && value < minIso) || (maxIso && value > maxIso)) return false
  const parsed = new Date(`${value}T12:00:00Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

export function CalendarDateField({
  value, onChange, label, className = '', required = false, minIso, maxIso,
}: {
  value: string
  onChange: (value: string) => void
  label: string
  className?: string
  required?: boolean
  minIso?: string
  maxIso?: string
}) {
  const [editing, setEditing] = useState({ committed: value, draft: value })
  const draft = editing.committed === value ? editing.draft : value
  const inputRef = useRef<HTMLInputElement>(null)
  const errorId = useId()
  const invalid = Boolean(draft && !validDate(draft, minIso, maxIso))
  useEffect(() => {
    const input = inputRef.current
    if (!input) return
    input.setCustomValidity(invalid ? 'Enter a valid date in YYYY-MM-DD format.' : '')
  }, [invalid])

  return <div className="min-w-0"><div className="flex min-w-0 items-center gap-1">
    <input
      ref={inputRef}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      placeholder="YYYY-MM-DD"
      pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}"
      aria-label={label}
      aria-invalid={invalid}
      aria-describedby={invalid ? errorId : undefined}
      required={required}
      max={maxIso}
      value={draft}
      onChange={(event) => {
        const next = event.target.value
        setEditing({ committed: value, draft: next })
        if (!next || validDate(next, minIso, maxIso)) onChange(next)
      }}
      className={`min-w-0 flex-1 ${className}`}
    />
    <DayCalendarPopover
      value={validDate(draft, minIso, maxIso) ? draft : value}
      minIso={minIso}
      maxIso={maxIso}
      onSelect={(selected) => { setEditing({ committed: value, draft: selected }); onChange(selected) }}
      triggerLabel={`Choose ${label.toLowerCase()}`}
      triggerClassName="inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-lg border border-outline-variant/30 text-on-surface-variant hover:bg-surface-container-high dark:border-dark-outline-variant dark:text-dark-on-surface"
      iconOnly
      alignRight
      portal
    />
  </div>{invalid && <p id={errorId} role="alert" className="mt-1 text-xs text-error">Enter a valid date as YYYY-MM-DD{minIso ? `, on or after ${minIso}` : ''}{maxIso ? `, on or before ${maxIso}` : ''}.</p>}</div>
}

export function CalendarDateTimeField({
  value, onChange, label, className = '',
}: {
  value: string
  onChange: (value: string) => void
  label: string
  className?: string
}) {
  const date = value.slice(0, 10)
  const [pendingTime, setPendingTime] = useState('09:00')
  const time = value.slice(11, 16) || pendingTime
  const fieldLabel = label.endsWith(' date and time') ? label.slice(0, -' date and time'.length) : label
  return <div role="group" aria-label={label} className="flex min-w-0 flex-wrap items-center gap-2">
    <div className="min-w-40 flex-1">
      <CalendarDateField
        value={date}
        onChange={(next) => onChange(next ? `${next}T${time}` : '')}
        label={`${fieldLabel} date`}
        className={className}
      />
    </div>
    <input
      type="time"
      aria-label={`${fieldLabel} time`}
      value={time}
      required={Boolean(date)}
      onChange={(event) => { setPendingTime(event.target.value); if (date) onChange(`${date}T${event.target.value}`) }}
      className={className}
    />
  </div>
}
