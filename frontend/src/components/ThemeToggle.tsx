import { useCallback, useEffect, useState } from 'react'
import { applyDocumentTheme, type ThemeMode } from '../lib/theme'

export function ThemeToggle() {
  const [mode, setMode] = useState<ThemeMode>(() =>
    typeof document !== 'undefined' && document.documentElement.classList.contains('dark') ? 'dark' : 'light',
  )

  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== 'timebox-theme') return
      const next: ThemeMode = e.newValue === 'dark' ? 'dark' : 'light'
      applyDocumentTheme(next)
      setMode(next)
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
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
