import { useCallback, useEffect, useState } from 'react'
import { Layout } from '../../components/Layout'
import { api, type SettingsRead } from '../../lib/api'

function saveStatusClass(saveState: 'idle' | 'saving' | 'saved' | 'error') {
  if (saveState === 'error') return 'text-error'
  if (saveState === 'saving' || saveState === 'saved') return 'text-tertiary'
  return 'text-on-surface-variant'
}

const inputClassName =
  'min-w-[4.5rem] rounded-lg border border-outline-variant/15 bg-surface-container-lowest px-3 py-2 text-right font-body text-sm tabular-nums text-on-surface shadow-inner shadow-black/5 transition-[border-color,box-shadow] placeholder:text-outline focus:border-primary/40 focus:outline-none focus:ring-1 focus:ring-primary/20 dark:border-dark-outline-variant dark:bg-dark-surface-container-lowest/80 dark:text-dark-on-surface dark:shadow-black/20 dark:focus:border-dark-outline'

export function SettingsPage() {
  const [settings, setSettings] = useState<SettingsRead | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const s = await api.getSettings()
      setSettings(s)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load settings')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const patchSettings = async (body: Partial<{
    start_hour: number
    end_hour: number
    show_full_day: boolean
  }>) => {
    setSaveState('saving')
    setError(null)
    try {
      const next = await api.patchSettings(body)
      setSettings(next)
      setSaveState('saved')
    } catch (e) {
      setSaveState('error')
      setError(e instanceof Error ? e.message : 'Failed to save settings')
    }
  }

  if (loading) {
    return (
      <Layout>
        <p className="text-on-surface-variant">Loading…</p>
      </Layout>
    )
  }

  if (!settings) {
    return (
      <Layout>
        <p className="text-error">{error ?? 'Failed to load settings.'}</p>
      </Layout>
    )
  }

  return (
    <Layout>
      <section className="mb-10 flex flex-col gap-4 sm:mb-12 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-2 font-headline text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
            Settings
          </h1>
          <p className="max-w-xl font-body text-lg font-light leading-relaxed text-on-surface-variant">
            Global day window. Changes apply to all days.
          </p>
        </div>
        <div
          className="inline-flex shrink-0 items-center gap-2 self-start rounded-full border border-outline-variant/15 bg-surface-container-low/80 px-3 py-1.5 text-xs font-medium dark:border-dark-outline-variant dark:bg-dark-surface-container/50"
          aria-live="polite"
        >
          <span
            className={[
              'h-1.5 w-1.5 shrink-0 rounded-full',
              saveState === 'saving' && 'animate-pulse bg-tertiary',
              saveState === 'saved' && 'bg-tertiary',
              saveState === 'error' && 'bg-error',
              saveState === 'idle' && 'bg-outline-variant/60',
            ]
              .filter(Boolean)
              .join(' ')}
            aria-hidden
          />
          <span className={['font-label tracking-tight', saveStatusClass(saveState)].join(' ')}>
            {saveState === 'saving' && 'Saving…'}
            {saveState === 'saved' && 'Saved'}
            {saveState === 'error' && 'Save failed'}
            {saveState === 'idle' && 'Up to date'}
          </span>
        </div>
      </section>

      {error && (
        <div className="mb-6 rounded-xl border border-error-container bg-error-container/20 px-4 py-3 text-sm text-on-error-container">
          {error}
        </div>
      )}

      <section
        className="max-w-3xl overflow-hidden rounded-2xl bg-surface-container-low/70 dark:bg-dark-surface-container/35"
        aria-labelledby="settings-day-window-heading"
      >
        <header className="px-5 py-4">
          <h2
            id="settings-day-window-heading"
            className="font-headline text-base font-light tracking-tight text-on-surface dark:text-dark-on-surface"
          >
            Day window
          </h2>
          <p className="mt-1 max-w-lg text-sm leading-relaxed text-on-surface-variant">
            Visible hours on the timeline. End hour is exclusive (e.g. 8–20 shows 8:00 through 19:59).
          </p>
        </header>

        <div className="space-y-3 px-3 pb-3">
          <div className="flex flex-col gap-3 rounded-xl bg-surface-container-lowest/55 px-2 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-8 dark:bg-dark-surface-container-low/60">
            <div className="min-w-0 flex-1">
              <label htmlFor="settings-start-hour" className="block font-headline text-sm font-medium text-on-surface dark:text-dark-on-surface">
                Start hour
              </label>
              <p className="mt-0.5 text-sm text-on-surface-variant">First hour shown (0–23).</p>
            </div>
            <input
              id="settings-start-hour"
              type="number"
              min={0}
              max={23}
              className={inputClassName}
              defaultValue={settings.start_hour}
              key={`start-${settings.updated_at}`}
              onBlur={(e) => {
                const v = Number(e.target.value)
                if (Number.isFinite(v)) void patchSettings({ start_hour: v })
              }}
            />
          </div>

          <div className="flex flex-col gap-3 rounded-xl bg-surface-container-lowest/55 px-2 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-8 dark:bg-dark-surface-container-low/60">
            <div className="min-w-0 flex-1">
              <label htmlFor="settings-end-hour" className="block font-headline text-sm font-medium text-on-surface dark:text-dark-on-surface">
                End hour
              </label>
              <p className="mt-0.5 text-sm text-on-surface-variant">Exclusive end of the window (1–24).</p>
            </div>
            <input
              id="settings-end-hour"
              type="number"
              min={1}
              max={24}
              className={inputClassName}
              defaultValue={settings.end_hour}
              key={`end-${settings.updated_at}`}
              onBlur={(e) => {
                const v = Number(e.target.value)
                if (Number.isFinite(v)) void patchSettings({ end_hour: v })
              }}
            />
          </div>

          <div className="flex flex-col gap-3 rounded-xl bg-surface-container-lowest/55 px-2 py-4 sm:flex-row sm:items-start sm:justify-between sm:gap-8 dark:bg-dark-surface-container-low/60">
            <div className="min-w-0 flex-1 pt-0.5">
              <p className="font-headline text-sm font-medium text-on-surface dark:text-dark-on-surface">Show full 24 hours</p>
              <p className="mt-0.5 text-sm text-on-surface-variant">
                Ignore start/end and display the full day on the timeline.
              </p>
            </div>
            <label className="relative inline-flex shrink-0 cursor-pointer items-center sm:mt-1">
              <input
                type="checkbox"
                className="peer sr-only"
                checked={settings.show_full_day}
                onChange={(e) => void patchSettings({ show_full_day: e.target.checked })}
                aria-label="Show full 24 hours"
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
          </div>
        </div>
      </section>
    </Layout>
  )
}
