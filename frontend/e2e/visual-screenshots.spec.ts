import * as fs from 'node:fs'
import * as path from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

/**
 * Full-page visual screenshots for manual inspection.
 *
 * Requires the same stack as other e2e tests (see playwright.config.ts webServer):
 * backend on 127.0.0.1:8000 and Vite on 127.0.0.1:5174. There is no user auth;
 * if the API is down, "/" will show an error state instead of redirecting.
 *
 * Run from frontend: npm run screenshots
 * Or from repo root: node scripts/take-screenshots.mjs
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SCREENSHOT_DIR = path.join(__dirname, '..', '..', 'screenshots', 'playwright')

test.describe.configure({ mode: 'serial' })

test.use({
  viewport: { width: 1440, height: 900 },
  deviceScaleFactor: 1,
  colorScheme: 'light',
  locale: 'en-US',
  timezoneId: 'UTC',
  reducedMotion: 'reduce',
})

test.beforeAll(() => {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
})

async function capture(page: Page, basename: string): Promise<void> {
  const filePath = path.join(SCREENSHOT_DIR, `${basename}.png`)
  await page.screenshot({ path: filePath, fullPage: true, animations: 'disabled' })
  console.log(`Saved ${filePath}`)
}

test('home (redirect to server today)', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await Promise.race([
    page.waitForURL(/\/day\/\d{4}-\d{2}-\d{2}/, { timeout: 30_000 }),
    page.getByText('Cannot load today from server.').waitFor({ state: 'visible', timeout: 30_000 }),
  ])
  if (page.url().includes('/day/')) {
    await expect(page.getByTestId('day-timeline')).toBeVisible({ timeout: 30_000 })
    await capture(page, 'home')
  } else {
    await capture(page, 'home-api-unavailable')
  }
})

test('today (fixed date)', async ({ page }) => {
  await page.goto('/day/2026-05-15', { waitUntil: 'domcontentloaded' })
  await expect(page.getByTestId('day-date')).toHaveText('2026-05-15', { timeout: 30_000 })
  await expect(page.getByTestId('day-timeline')).toBeVisible()
  await capture(page, 'today')
})

test('chronicle (history)', async ({ page }) => {
  await page.goto('/history', { waitUntil: 'domcontentloaded' })
  await expect(page.getByTestId('chronicle-calendar')).toBeVisible({ timeout: 30_000 })
  await capture(page, 'chronicle')
})

test('task types', async ({ page }) => {
  await page.goto('/task-types', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: 'Task types' })).toBeVisible({ timeout: 30_000 })
  await capture(page, 'task-types')
})

test('settings', async ({ page }) => {
  await page.goto('/settings', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible({ timeout: 30_000 })
  await capture(page, 'settings')
})
