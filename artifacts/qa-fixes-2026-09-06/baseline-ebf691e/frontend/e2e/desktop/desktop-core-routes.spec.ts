// spec: specs/web-frontend-audit.plan.md
// seed: e2e/seed.spec.ts
import { expect, test } from '@playwright/test'

const routes = [
  { path: '/day/2026-05-15', heading: 'Friday, May 15, 2026' },
  { path: '/history', heading: 'Chronicle of focus' },
  { path: '/battle-plan', heading: 'All Tasks' },
  { path: '/battle-plan?view=recurring', heading: 'Recurring' },
  { path: '/task-types', heading: 'Task types' },
  { path: '/settings', heading: 'Settings' },
]

test.describe('Desktop layout', () => {
  test('desktop-core-routes', async ({ page }) => {
    const pageErrors: string[] = []
    page.on('pageerror', (error) => pageErrors.push(error.message))

    // 1. Visit every core route at the two constrained desktop widths.
    for (const width of [1280, 1440]) {
      await page.setViewportSize({ width, height: 900 })
      for (const route of routes) {
        await page.goto(route.path)
        await expect(page.getByRole('heading', { name: route.heading, exact: true })).toBeVisible()
        await expect(page.getByRole('navigation').first()).toBeVisible()
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
      }
    }

    expect(pageErrors).toEqual([])
  })
})
