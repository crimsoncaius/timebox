// spec: specs/web-frontend-audit.plan.md
// seed: e2e/seed.spec.ts
import { expect, test } from '@playwright/test'

test.describe('Theme behavior', () => {
  test('system-dark-preference', async ({ page }) => {
    // 1. Open Timebox with no saved theme while the browser prefers dark colors.
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.addInitScript(() => localStorage.removeItem('timebox-theme'))
    await page.goto('/settings')

    await expect(page.locator('html')).toHaveClass(/\bdark\b/)
    await expect(page.locator('html')).toHaveCSS('color-scheme', 'dark')
    await expect(page.getByRole('button', { name: 'Switch to light mode' })).toBeVisible()
    await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(18, 18, 18)')

    // 2. Reload the page without saving an explicit preference.
    await page.reload()
    await expect(page.locator('html')).toHaveClass(/\bdark\b/)

    // 3. Change the browser preference to light without saving an explicit preference.
    await page.emulateMedia({ colorScheme: 'light' })
    await expect(page.locator('html')).not.toHaveClass(/\bdark\b/)
    await expect(page.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible()
  })
})
