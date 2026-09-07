export type ThemeMode = 'light' | 'dark'

export const THEME_STORAGE_KEY = 'timebox-theme'

export function readStoredTheme(): ThemeMode | null {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : null
  } catch {
    return null
  }
}

export function preferredTheme(): ThemeMode {
  const stored = readStoredTheme()
  if (stored) return stored
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light'
}

/** Persist and apply theme on `<html>` (works with Tailwind class-based `dark:`). */
export function applyDocumentTheme(mode: ThemeMode, persist = true): void {
  document.documentElement.classList.toggle('dark', mode === 'dark')
  document.documentElement.style.colorScheme = mode === 'dark' ? 'dark' : 'light'
  if (!persist) return
  try {
    localStorage.setItem(THEME_STORAGE_KEY, mode)
  } catch {
    /* ignore */
  }
}
