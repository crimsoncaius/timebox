export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'timebox-theme'

/** Persist and apply theme on `<html>` (works with Tailwind class-based `dark:`). */
export function applyDocumentTheme(mode: ThemeMode): void {
  document.documentElement.classList.toggle('dark', mode === 'dark')
  document.documentElement.style.colorScheme = mode === 'dark' ? 'dark' : 'light'
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    /* ignore */
  }
}
