import { useCallback, useEffect, useState } from 'react'
import {
  applyDocumentTheme,
  preferredTheme,
  readStoredTheme,
  THEME_STORAGE_KEY,
  type ThemeMode,
} from '../lib/theme'

export function ThemeToggle() {
  const [mode, setMode] = useState<ThemeMode>(() =>
    typeof document !== 'undefined' && document.documentElement.classList.contains('dark') ? 'dark' : 'light',
  )

  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== THEME_STORAGE_KEY) return
      const next = e.newValue === 'dark' || e.newValue === 'light' ? e.newValue : preferredTheme()
      applyDocumentTheme(next, false)
      setMode(next)
    }
    const colorScheme = window.matchMedia?.('(prefers-color-scheme: dark)')
    const onColorSchemeChange = (event: MediaQueryListEvent) => {
      if (readStoredTheme()) return
      const next: ThemeMode = event.matches ? 'dark' : 'light'
      applyDocumentTheme(next, false)
      setMode(next)
    }
    window.addEventListener('storage', onStorage)
    colorScheme?.addEventListener('change', onColorSchemeChange)
    return () => {
      window.removeEventListener('storage', onStorage)
      colorScheme?.removeEventListener('change', onColorSchemeChange)
    }
  }, [])

  const toggle = useCallback(() => {
    setMode((current) => {
      const next: ThemeMode = current === 'dark' ? 'light' : 'dark'
      applyDocumentTheme(next)
      return next
    })
  }, [])

  return (
    <button
      type="button"
      onClick={toggle}
      className="material-symbols-outlined rounded-full p-2 text-on-surface-variant transition-colors hover:bg-surface-container-low hover:text-on-surface active:scale-95 dark:text-dark-on-surface-variant dark:hover:bg-dark-surface-container dark:hover:text-dark-on-surface"
      aria-label={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
      title={mode === 'dark' ? 'Light mode' : 'Dark mode'}
    >
      {mode === 'dark' ? 'light_mode' : 'dark_mode'}
    </button>
  )
}
