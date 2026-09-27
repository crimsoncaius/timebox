import type { ReactNode } from 'react'

export const settingsInputClassName =
  'min-w-[4.5rem] rounded-lg border border-outline-variant/15 bg-surface-container-lowest px-3 py-2 font-body text-sm tabular-nums text-on-surface shadow-inner shadow-black/5 transition-[border-color,box-shadow] placeholder:text-outline focus:border-primary/40 focus:outline-none focus:ring-1 focus:ring-primary/20 disabled:cursor-not-allowed dark:border-dark-outline-variant dark:bg-dark-surface-container-lowest/80 dark:text-dark-on-surface dark:shadow-black/20 dark:focus:border-dark-outline'

export const settingsButtonClassName =
  'shrink-0 rounded-full bg-surface-container px-4 py-2 font-label text-sm font-medium text-on-surface transition-colors hover:bg-surface-container-high focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary/30 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-surface-container dark:bg-dark-surface-container-high dark:text-dark-on-surface dark:hover:bg-dark-surface-container-highest'

export type SettingsScope = 'shared' | 'device'

/** One group of related settings, labelled with where its values live. */
export function SettingsSection({ id, title, scope, description, children }: {
  id: string
  title: string
  scope: SettingsScope
  description?: string
  children: ReactNode
}) {
  return (
    <section
      className="overflow-hidden rounded-2xl bg-surface-container-low/70 dark:bg-dark-surface-container/35"
      aria-labelledby={`${id}-heading`}
    >
      <header className="flex items-start justify-between gap-4 px-5 pb-3 pt-5">
        <div className="min-w-0">
          <h2
            id={`${id}-heading`}
            className="font-headline text-lg font-light tracking-tight text-on-surface dark:text-dark-on-surface"
          >
            {title}
          </h2>
          {description && (
            <p className="mt-1 max-w-lg text-sm leading-relaxed text-on-surface-variant dark:text-dark-on-surface-variant">
              {description}
            </p>
          )}
        </div>
        <span className="mt-1 shrink-0 rounded-full border border-outline-variant/25 px-2.5 py-1 font-label text-[10px] font-medium uppercase tracking-[0.16em] text-on-surface-variant dark:border-dark-outline-variant dark:text-dark-on-surface-variant">
          {scope === 'shared' ? 'All devices' : 'This device'}
        </span>
      </header>
      <div className="space-y-2 px-3 pb-3">{children}</div>
    </section>
  )
}

/** A label and description on the left, its control on the right; wide controls wrap below on narrow screens. */
export function SettingsRow({ label, htmlFor, description, disabled, footer, children }: {
  label: string
  htmlFor?: string
  description?: ReactNode
  disabled?: boolean
  footer?: ReactNode
  children?: ReactNode
}) {
  const labelClassName = 'block font-headline text-sm font-medium text-on-surface dark:text-dark-on-surface'
  return (
    <div
      className={[
        'rounded-xl bg-surface-container-lowest/55 px-4 py-4 transition-opacity dark:bg-dark-surface-container-low/60',
        disabled && 'opacity-50',
      ].filter(Boolean).join(' ')}
    >
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
        <div className="min-w-[10rem] flex-1">
          {htmlFor ? <label htmlFor={htmlFor} className={labelClassName}>{label}</label> : <p className={labelClassName}>{label}</p>}
          {description && (
            <p className="mt-0.5 text-sm leading-relaxed text-on-surface-variant dark:text-dark-on-surface-variant">{description}</p>
          )}
        </div>
        {children && <div className="flex max-w-full shrink-0 flex-wrap items-center gap-2">{children}</div>}
      </div>
      {footer}
    </div>
  )
}

export function SettingsToggle({ checked, onChange, label, disabled }: {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  disabled?: boolean
}) {
  return (
    <label className="relative inline-flex shrink-0 cursor-pointer items-center has-[:disabled]:cursor-not-allowed">
      <input
        type="checkbox"
        className="peer sr-only"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        aria-label={label}
      />
      <span
        className="block h-7 w-12 rounded-full border border-outline-variant/15 bg-outline-variant/25 transition-colors peer-focus-visible:ring-1 peer-focus-visible:ring-primary/30 peer-checked:border-tertiary/50 peer-checked:bg-tertiary dark:border-dark-outline-variant dark:bg-dark-surface-container-high dark:peer-checked:bg-tertiary"
        aria-hidden
      />
      <span
        className="pointer-events-none absolute left-0.5 top-0.5 z-10 h-6 w-6 rounded-full bg-surface-container-lowest shadow-[0_0_24px_rgba(45,52,53,0.04)] transition-transform peer-checked:translate-x-5 dark:bg-dark-on-surface"
        aria-hidden
      />
    </label>
  )
}

/** Long explanations stay one click away instead of crowding the rows. */
export function SettingsDetails({ summary, children }: { summary: string; children: ReactNode }) {
  return (
    <details className="group rounded-xl px-4 py-2 text-sm text-on-surface-variant dark:text-dark-on-surface-variant">
      <summary className="cursor-pointer select-none font-label font-medium text-on-surface-variant transition-colors hover:text-on-surface marker:text-outline dark:text-dark-on-surface-variant dark:hover:text-dark-on-surface">
        {summary}
      </summary>
      <div className="mt-2 max-w-xl space-y-2 leading-relaxed">{children}</div>
    </details>
  )
}

/** Read-only state for a permission or capability the page cannot change itself. */
export function SettingsStatus({ children }: { children: ReactNode }) {
  return (
    <span className="rounded-full bg-surface-container px-3 py-1 font-label text-xs font-medium text-on-surface-variant dark:bg-dark-surface-container-high dark:text-dark-on-surface-variant">
      {children}
    </span>
  )
}
