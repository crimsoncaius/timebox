import { expect, test } from '@playwright/test'

/**
 * Seeds planned + actual blocks via API, then verifies Today + History + Review in the browser.
 */
test('plan blocks, history, and review', async ({ page, request }) => {
  await page.goto('/')
  await expect(page.getByTestId('day-date')).toHaveText(/\d{4}-\d{2}-\d{2}/, { timeout: 30_000 })
  const date = (await page.getByTestId('day-date').textContent())?.trim()
  expect(date).toBeTruthy()

  const base = 'http://127.0.0.1:8000'
  const r1 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'planned',
      title: 'E2E planned',
      start_minute: 480,
      end_minute: 510,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r1.ok()).toBeTruthy()

  const r2 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'actual',
      title: 'E2E actual',
      start_minute: 540,
      end_minute: 570,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r2.ok()).toBeTruthy()

  await page.reload()
  await expect(page.getByLabel('Task planned')).toHaveValue('E2E planned', { timeout: 15_000 })
  await expect(page.getByLabel('Task actual')).toHaveValue('E2E actual')

  await page.getByRole('link', { name: 'History' }).click()
  await expect(page.getByRole('heading', { name: /Chronicle of focus/i })).toBeVisible()

  await page.getByRole('link', { name: 'Review' }).first().click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText(/Daily review/i)
  await expect(page.getByTestId('day-timeline')).toBeVisible()
})
