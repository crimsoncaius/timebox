// spec: specs/web-frontend-audit.plan.md
// seed: e2e/seed.spec.ts
import { expect, test } from '@playwright/test'

test.describe('Theme behavior', () => {
  test('manual-theme-persists', async ({ page }) => {
    // 1. Open Timebox in light mode and choose dark mode.
    await page.emulateMedia({ colorScheme: 'light' })
    await page.addInitScript(() => {
      if (sessionStorage.getItem('timebox-theme-test-ready')) return
      localStorage.removeItem('timebox-theme')
      sessionStorage.setItem('timebox-theme-test-ready', 'true')
    })
    await page.goto('/settings')
    await page.getByRole('button', { name: 'Switch to dark mode' }).click()

    await expect(page.locator('html')).toHaveClass(/\bdark\b/)
    expect(await page.evaluate(() => localStorage.getItem('timebox-theme'))).toBe('dark')

    // 2. Navigate through the core routes, then reload.
    for (const route of [
      '/day/2026-05-15',
      '/history',
      '/battle-plan',
      '/task-types',
      '/settings',
    ]) {
      await page.goto(route)
      await expect(page.locator('html')).toHaveClass(/\bdark\b/)
      await expect(page.getByRole('button', { name: 'Switch to light mode' })).toBeVisible()
    }
    await page.reload()
    await expect(page.locator('html')).toHaveClass(/\bdark\b/)
  })
})
