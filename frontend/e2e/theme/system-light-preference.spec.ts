// spec: specs/web-frontend-audit.plan.md
// seed: e2e/seed.spec.ts
import { expect, test } from '@playwright/test'

test.describe('Theme behavior', () => {
  test('system-light-preference', async ({ page }) => {
    // 1. Open Timebox with no saved theme while the browser prefers light colors.
    await page.emulateMedia({ colorScheme: 'light' })
    await page.addInitScript(() => localStorage.removeItem('timebox-theme'))
    await page.goto('/settings')

    await expect(page.locator('html')).not.toHaveClass(/\bdark\b/)
    await expect(page.locator('html')).toHaveCSS('color-scheme', 'light')
    await expect(page.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible()
    await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(249, 249, 249)')
  })
})
