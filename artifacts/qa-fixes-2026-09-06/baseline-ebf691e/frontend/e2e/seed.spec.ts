import { test } from '@playwright/test'

test('seed desktop web audit', async ({ page }) => {
  await page.goto('/')
})
